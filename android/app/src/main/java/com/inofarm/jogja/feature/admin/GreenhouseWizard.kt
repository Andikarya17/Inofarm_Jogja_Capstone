package com.inofarm.jogja.feature.admin

import com.inofarm.jogja.feature.FarmFormBar
import com.inofarm.jogja.feature.FarmFormSection
import com.inofarm.jogja.feature.FarmInfoCard
import com.inofarm.jogja.feature.FarmStepIndicator
import com.inofarm.jogja.feature.LocalCanopyForm
import com.inofarm.jogja.feature.FinanceDateField
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.design.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Start planting in an existing greenhouse; an open season without planting is reused, never duplicated. */
data class PlantIn(val greenhouseId: String, val name: String, val seasonId: String? = null, val seasonName: String = "", val seasonStart: String = "")

/** Field checks for the "Tambah greenhouse" wizard. Kept free of Compose so they can be unit-tested. */
internal object WizardRules {
    fun greenhouseNameError(name: String): String? = when {
        name.isBlank() -> "Isi nama greenhouse."
        name.trim().length > 150 -> "Nama maksimal 150 karakter."
        else -> null
    }

    /** One planting per variety, 1 or 2 varieties (Inofarm 2.4). [varieties] holds name to plant count. */
    fun plantingErrors(season: String, start: String, varieties: List<Pair<String, String>>,
                       sowing: String, transplant: String): Map<String, String> = buildMap {
        if (season.isBlank()) put("season", "Isi nama musim.")
        if (runCatching { LocalDate.parse(start) }.isFailure) put("start", "Isi tanggal mulai, contoh 2026-11-10.")
        varieties.forEachIndexed { index, (name, population) ->
            when {
                name.isBlank() -> put("variety$index", "Isi nama varietas.")
                name.trim().length > 150 -> put("variety$index", "Nama maksimal 150 karakter.")
                varieties.take(index).any { it.first.trim().equals(name.trim(), ignoreCase = true) } ->
                    put("variety$index", "Nama varietas kedua harus berbeda dari yang pertama.")
            }
            if (population.toIntOrNull()?.let { it >= 0 } != true) put("population$index", "Isi jumlah tanaman sebagai angka bulat.")
        }
        val sown = runCatching { LocalDate.parse(sowing) }.getOrNull()
        if (sown == null) put("sowing", "Isi tanggal semai, contoh 2026-11-10.")
        if (transplant.isNotBlank()) {
            val moved = runCatching { LocalDate.parse(transplant) }.getOrNull()
            if (moved == null) put("transplant", "Tanggal pindah tanam tidak sah.")
            else if (sown != null && moved.isBefore(sown)) put("transplant", "Tanggal pindah tidak boleh sebelum semai.")
        }
    }

}

/**
 * Two steps: greenhouse, then first planting (optional) with one or two varieties planted together. Each variety
 * becomes a planting group named after it, which the recording screens never show. Workers need no assignment
 * (17 Nov 2026), so there is no worker step. Each step saves as soon as the user continues, so a failure leaves
 * earlier steps intact. Operation ids are reused on retry.
 */
@Composable
fun GreenhouseWizardHost(
    session: FinanceSession,
    repository: AdminRepository,
    onChanged: () -> Unit,
    onOpenGreenhouse: (String) -> Unit,
    onRecord: (String) -> Unit,
    onClose: () -> Unit,
    /** Start planting in an existing greenhouse: only the planting step is shown. */
    plantIn: PlantIn? = null,
) {
    if (session.role != UserRole.MANAGER) { LaunchedEffect(Unit) { onClose() }; return }
    val ticket = repository.vault.generation
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(if (plantIn != null) 2 else 1) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val today = remember { LocalDate.now(ActivityDraft.WIB).toString() }

    val ghId = rememberSaveable { plantIn?.greenhouseId ?: UUID.randomUUID().toString() }
    var ghDone by rememberSaveable { mutableStateOf(plantIn != null) }
    var name by rememberSaveable { mutableStateOf(plantIn?.name.orEmpty()) }

    val seasonId = rememberSaveable { plantIn?.seasonId ?: UUID.randomUUID().toString() }
    val varietyIds = rememberSaveable { listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString()) }
    val groupIds = rememberSaveable { listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString()) }
    var seasonDone by rememberSaveable { mutableStateOf(plantIn?.seasonId != null) }
    var varietyDone by rememberSaveable { mutableStateOf(listOf(false, false)) }
    var groupDone by rememberSaveable { mutableStateOf(listOf(false, false)) }
    var periodDone by rememberSaveable { mutableStateOf(false) }
    var season by rememberSaveable { mutableStateOf(plantIn?.seasonName.orEmpty()) }
    var start by rememberSaveable { mutableStateOf(plantIn?.seasonStart?.takeIf { it.isNotBlank() } ?: today) }
    var names by rememberSaveable { mutableStateOf(listOf("", "")) }
    var populations by rememberSaveable { mutableStateOf(listOf("", "")) }
    var twoVarieties by rememberSaveable { mutableStateOf(false) }
    var sowing by rememberSaveable { mutableStateOf(today) }
    var transplant by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(error) { if (error != null) scroll.animateScrollTo(0) }
    LaunchedEffect(step) { fieldErrors = emptyMap() }

    val operations = remember { mutableMapOf<String, JSONObject>() }

    // An operation is reused only while its payload is identical (a lost-response retry); edited fields get a fresh one.
    suspend fun send(form: AdminForm, password: String = ""): JSONObject? {
        val key = form.id + "|" + form.payload(password).toString()
        val operation = operations.getOrPut(key) { repository.buildOperation(session, ticket, form, password) }
        return try {
            repository.sendOperation(session, ticket, form, operation)
        } catch (failure: FinanceApiFailure) {
            if (failure.code == "entity_exists") null else throw failure
        }
    }

    fun run(label: String, work: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; progress = label
        scope.launch {
            try { work() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                error = if (failure is java.io.IOException) "Tidak ada koneksi. Isian tetap di layar; coba lagi." else userMessage(failure)
            }
            finally { onChanged(); busy = false; progress = null }
        }
    }

    fun saveGreenhouse() {
        val problem = WizardRules.greenhouseNameError(name)
        fieldErrors = if (problem != null) mapOf("name" to problem) else emptyMap()
        if (problem != null) return
        if (ghDone) { step = 2; return }
        run("Menyimpan greenhouse…") {
            send(AdminForm(AdminResource.GREENHOUSE, id = ghId, values = mapOf("name" to name.trim())))
            ghDone = true
            step = 2
        }
    }

    val count = if (twoVarieties) 2 else 1
    fun savePlanting() {
        val problems = WizardRules.plantingErrors(season, start, (0 until count).map { names[it] to populations[it] }, sowing, transplant)
        fieldErrors = problems
        if (problems.isNotEmpty()) return
        run("Menyimpan tanam…") {
            if (!seasonDone) {
                progress = "Menyimpan musim…"
                send(AdminForm(AdminResource.SEASON, id = seasonId, values = mapOf("greenhouse_id" to ghId, "name" to season.trim(), "start_date" to start)))
                seasonDone = true
            }
            for (index in 0 until count) {
                val name = names[index].trim()
                if (!varietyDone[index]) {
                    progress = "Menyimpan varietas $name…"
                    send(AdminForm(AdminResource.VARIETY, id = varietyIds[index], values = mapOf("greenhouse_id" to ghId, "name" to name)))
                    varietyDone = varietyDone.toMutableList().also { it[index] = true }
                }
                if (!groupDone[index]) {
                    // The planting group carries the variety's name; recording screens show varieties, not groups.
                    send(AdminForm(AdminResource.GROUP, id = groupIds[index], values = mapOf("season_id" to seasonId, "variety_id" to varietyIds[index],
                        "name" to name, "population" to populations[index].trim(), "sowing_date" to sowing, "transplant_date" to transplant)))
                    groupDone = groupDone.toMutableList().also { it[index] = true }
                }
            }
            if (!periodDone) {
                progress = "Menetapkan tanam saat ini…"
                val version = repository.options(session, ticket, "greenhouses", null).firstOrNull { it.getString("id") == ghId }
                    ?.getLong("version") ?: error("Greenhouse belum muncul di daftar. Perbarui lalu coba lagi.")
                val operation = operations.getOrPut("period|$version|${groupIds[0]}") { repository.designateOperation(session, ticket, ghId, version, groupIds[0]) }
                repository.sendDesignation(session, ticket, ghId, operation)
                periodDone = true
            }
            finished = true
        }
    }

    // Once the greenhouse is saved, leaving goes to the finish screen so the manager sees it already exists.
    val close: () -> Unit = { if (!busy) { if (ghDone && !finished && plantIn == null) finished = true else onClose() } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        BackHandler { if (!busy) { if (finished) onClose() else if (step > 1 && plantIn == null) step-- else close() } }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CompositionLocalProvider(LocalCanopyForm provides true) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = FarmSpace.Page)) {
                FarmSheetHeading(if (plantIn != null) "Mulai tanam" else "Tambah greenhouse", close, !busy)
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(vertical = FarmSpace.Medium),
                    verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                    androidx.compose.animation.AnimatedContent(finished, label = "Wizard selesai", transitionSpec = {
                        androidx.compose.animation.fadeIn(tween(FarmMotion.State, delayMillis = FarmMotion.TabExit)) togetherWith
                            androidx.compose.animation.fadeOut(tween(FarmMotion.TabExit))
                    }) { done ->
                    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                    if (done) {
                        Text("Greenhouse siap dicatat", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                        Text(name.trim(), style = MaterialTheme.typography.titleMedium)
                        val planted = (0 until count).filter { groupDone[it] }.map { names[it].trim() }
                        Text(if (periodDone) "Mulai tanam ${planted.joinToString(" dan ")}. Semua pekerja sudah bisa mencatat di sini."
                            else "Belum mulai tanam. Mulai tanam dari halaman greenhouse sebelum mencatat.", style = MaterialTheme.typography.bodyMedium)
                        if (periodDone) Button(onClick = { onRecord(ghId) }, shape = FarmShape.Control,
                            modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("wizard-record")) { Text("Catat kegiatan") }
                        OutlinedButton(onClick = { onOpenGreenhouse(ghId) }, shape = FarmShape.Control,
                            modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("wizard-open")) { Text("Ke greenhouse") }
                    } else {
                        if (plantIn == null) FarmStepIndicator(step, listOf("Greenhouse", "Mulai tanam"))
                        Text(if (plantIn != null) name.trim() else if (step == 1) "Langkah 1 dari 2" else "Langkah 2 dari 2 · bisa dilewati",
                            Modifier.testTag("wizard-step").semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        progress?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        error?.let { Notice(it, true) }
                        // Forward slides in from the end, Kembali from the start: the same shared axis as the shell.
                        androidx.compose.animation.AnimatedContent(step, label = "Langkah wizard", transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            val enter = tween<androidx.compose.ui.unit.IntOffset>(FarmMotion.Depth, easing = FarmMotion.EmphasizedDecelerate)
                            val exit = tween<androidx.compose.ui.unit.IntOffset>((FarmMotion.Depth * FarmMotion.DepthExitShare).toInt(), easing = FarmMotion.EmphasizedAccelerate)
                            (androidx.compose.animation.slideInHorizontally(enter) { (it * 0.08f).toInt() * direction } +
                                androidx.compose.animation.fadeIn(tween(FarmMotion.Depth, easing = FarmMotion.EmphasizedDecelerate))) togetherWith
                                (androidx.compose.animation.slideOutHorizontally(exit) { -(it * 0.08f).toInt() * direction } +
                                    androidx.compose.animation.fadeOut(tween((FarmMotion.Depth * FarmMotion.DepthExitShare).toInt(), easing = FarmMotion.EmphasizedAccelerate)))
                        }) { shown ->
                        Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                        when (shown) {
                            1 -> {
                                Text("Greenhouse", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                                if (ghDone) FarmInfoCard { Text("Greenhouse ${name.trim()} sudah tersimpan.", style = MaterialTheme.typography.bodyMedium) }
                                FarmFormSection("Data greenhouse") {
                                    FinanceField("Nama greenhouse", name, { name = it }, "wizard-name", fieldErrors["name"], enabled = !busy && !ghDone)
                                }
                            }
                            else -> {
                                if (plantIn == null) Text("Mulai tanam", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                                val saved = listOfNotNull(if (seasonDone) "musim" else null,
                                    (0 until count).filter { groupDone[it] }.takeIf { it.isNotEmpty() }?.joinToString(" dan ") { names[it].trim() })
                                if (saved.isNotEmpty()) FarmInfoCard { Text(saved.joinToString(", ").replaceFirstChar { it.uppercase() } + " sudah tersimpan.", style = MaterialTheme.typography.bodyMedium) }
                                FarmFormSection("Musim") {
                                    FinanceField("Nama musim", season, { season = it }, "wizard-season", fieldErrors["season"], enabled = !busy && !seasonDone)
                                    FinanceDateField("Tanggal mulai musim", start, { start = it }, "wizard-start", fieldErrors["start"], hint = "contoh 2026-11-10", enabled = !busy && !seasonDone)
                                }
                                val planted = groupDone.any { it }
                                FarmFormSection("Tanam") {
                                    Text("Semua varietas di greenhouse ini ditanam serentak.", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FinanceDateField("Tanggal semai", sowing, { sowing = it }, "wizard-sowing", fieldErrors["sowing"], hint = "contoh 2026-11-10", enabled = !busy && !planted)
                                    FinanceDateField("Tanggal pindah tanam (opsional)", transplant, { transplant = it }, "wizard-transplant", fieldErrors["transplant"], hint = "contoh 2026-11-10", enabled = !busy && !planted)
                                }
                                for (index in 0 until count) {
                                    FarmFormSection(if (count == 1) "Varietas" else "Varietas ${index + 1}") {
                                        val locked = busy || varietyDone[index]
                                        FinanceField("Nama varietas", names[index], { value -> names = names.toMutableList().also { it[index] = value }; fieldErrors = fieldErrors - "variety$index" },
                                            "wizard-variety-$index", fieldErrors["variety$index"], enabled = !locked)
                                        FinanceField("Jumlah tanaman", populations[index], { value -> populations = populations.toMutableList().also { it[index] = value }; fieldErrors = fieldErrors - "population$index" },
                                            "wizard-population-$index", fieldErrors["population$index"], KeyboardType.Number, enabled = !busy && !groupDone[index], suffix = "tanaman")
                                        if (index == 1 && !varietyDone[1]) TextButton(onClick = { twoVarieties = false; names = listOf(names[0], ""); populations = listOf(populations[0], "") },
                                            enabled = !busy, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("wizard-remove-variety")) { Text("Hapus varietas kedua") }
                                    }
                                }
                                if (!twoVarieties && !planted) OutlinedButton(onClick = { twoVarieties = true }, enabled = !busy, shape = FarmShape.Control,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("wizard-add-variety")) { Text("Tambah varietas kedua") }
                                if (plantIn == null) TextButton(onClick = { finished = true }, enabled = !busy,
                                    modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("wizard-skip")) { Text("Lewati langkah ini") }
                            }
                        }
                        }
                        }
                    }
                    }
                    }
                }
                if (!finished) FarmFormBar("wizard-back", { step-- }, "wizard-next", if (step == 2) "Simpan dan selesai" else "Simpan dan lanjut",
                    { if (step == 1) saveGreenhouse() else savePlanting() },
                    enabled = !busy, busy = busy, saveLabel = "Kembali", showSave = step > 1 && plantIn == null)
            }
            }
        }
    }
}
