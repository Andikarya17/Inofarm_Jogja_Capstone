package com.inofarm.jogja.feature.admin

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
import java.time.OffsetDateTime
import java.util.UUID

/** Field checks for the "Tambah greenhouse" wizard. Kept free of Compose so they can be unit-tested. */
internal object WizardRules {
    fun greenhouseNameError(name: String): String? = when {
        name.isBlank() -> "Isi nama greenhouse."
        name.trim().length > 150 -> "Nama maksimal 150 karakter."
        else -> null
    }

    fun plantingErrors(season: String, start: String, variety: String, group: String, population: String,
                       sowing: String, transplant: String): Map<String, String> = buildMap {
        if (season.isBlank()) put("season", "Isi nama musim.")
        if (runCatching { LocalDate.parse(start) }.isFailure) put("start", "Isi tanggal mulai, contoh 2026-11-10.")
        if (variety.isBlank()) put("variety", "Isi nama varietas.")
        if (group.isBlank()) put("group", "Isi nama kelompok tanam.")
        if (population.toIntOrNull()?.let { it >= 0 } != true) put("population", "Isi jumlah tanaman sebagai angka bulat.")
        val sown = runCatching { LocalDate.parse(sowing) }.getOrNull()
        if (sown == null) put("sowing", "Isi tanggal semai, contoh 2026-11-10.")
        if (transplant.isNotBlank()) {
            val moved = runCatching { LocalDate.parse(transplant) }.getOrNull()
            if (moved == null) put("transplant", "Tanggal pindah tanam tidak sah.")
            else if (sown != null && moved.isBefore(sown)) put("transplant", "Tanggal pindah tidak boleh sebelum semai.")
        }
    }

    fun workerErrors(name: String, email: String, password: String): Map<String, String> = buildMap {
        if (name.isBlank()) put("name", "Isi nama pekerja.")
        if (!email.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) put("email", "Isi email yang benar.")
        if (password.length < 12) put("password", "Password minimal 12 karakter.")
    }
}

/**
 * Three steps: greenhouse, first planting (optional), workers (optional). Each step saves as soon as the
 * user continues, so a failure leaves earlier steps intact. Operation ids are reused on retry.
 */
@Composable
fun GreenhouseWizardHost(
    session: FinanceSession,
    repository: AdminRepository,
    onChanged: () -> Unit,
    onOpenGreenhouse: (String) -> Unit,
    onRecord: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (session.role != UserRole.MANAGER) { LaunchedEffect(Unit) { onClose() }; return }
    val ticket = repository.vault.generation
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(1) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val today = remember { LocalDate.now(ActivityDraft.WIB).toString() }

    val ghId = rememberSaveable { UUID.randomUUID().toString() }
    var ghDone by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }

    val seasonId = rememberSaveable { UUID.randomUUID().toString() }
    val varietyId = rememberSaveable { UUID.randomUUID().toString() }
    val groupId = rememberSaveable { UUID.randomUUID().toString() }
    var seasonDone by rememberSaveable { mutableStateOf(false) }
    var varietyDone by rememberSaveable { mutableStateOf(false) }
    var groupDone by rememberSaveable { mutableStateOf(false) }
    var periodDone by rememberSaveable { mutableStateOf(false) }
    var season by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf(today) }
    var variety by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    var population by rememberSaveable { mutableStateOf("") }
    var sowing by rememberSaveable { mutableStateOf(today) }
    var transplant by rememberSaveable { mutableStateOf("") }

    var workers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var workersLoaded by remember { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf(setOf<String>()) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newEmail by rememberSaveable { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var addedWorkers by rememberSaveable { mutableIntStateOf(0) }
    var showPassword by remember { mutableStateOf(false) }
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

    fun savePlanting() {
        val problems = WizardRules.plantingErrors(season, start, variety, group, population, sowing, transplant)
        fieldErrors = problems
        if (problems.isNotEmpty()) return
        run("Menyimpan tanam…") {
            if (!seasonDone) {
                progress = "Menyimpan musim…"
                send(AdminForm(AdminResource.SEASON, id = seasonId, values = mapOf("greenhouse_id" to ghId, "name" to season.trim(), "start_date" to start)))
                seasonDone = true
            }
            if (!varietyDone) {
                progress = "Menyimpan varietas…"
                send(AdminForm(AdminResource.VARIETY, id = varietyId, values = mapOf("greenhouse_id" to ghId, "name" to variety.trim())))
                varietyDone = true
            }
            if (!groupDone) {
                progress = "Menyimpan kelompok tanam…"
                send(AdminForm(AdminResource.GROUP, id = groupId, values = mapOf("season_id" to seasonId, "variety_id" to varietyId,
                    "name" to group.trim(), "population" to population.trim(), "sowing_date" to sowing, "transplant_date" to transplant)))
                groupDone = true
            }
            if (!periodDone) {
                progress = "Menetapkan tanam saat ini…"
                val version = repository.options(session, ticket, "greenhouses", null).firstOrNull { it.getString("id") == ghId }
                    ?.getLong("version") ?: error("Greenhouse belum muncul di daftar. Perbarui lalu coba lagi.")
                val operation = operations.getOrPut("period|$version|$groupId") { repository.designateOperation(session, ticket, ghId, version, groupId) }
                repository.sendDesignation(session, ticket, ghId, operation)
                periodDone = true
            }
            step = 3
        }
    }

    fun loadWorkers() {
        if (workersLoaded || busy) return
        busy = true
        scope.launch {
            try { workers = repository.options(session, ticket, "assignment-users", ghId); workersLoaded = true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = userMessage(failure) }
            finally { busy = false }
        }
    }
    if (step == 3 && ghDone) LaunchedEffect(Unit) { loadWorkers() }

    fun saveWorkers() {
        val creating = newName.isNotBlank() || newEmail.isNotBlank() || newPassword.isNotBlank()
        val problems = if (creating) WizardRules.workerErrors(newName, newEmail, newPassword) else emptyMap()
        fieldErrors = problems
        if (problems.isNotEmpty()) return
        run("Menyimpan pekerja…") {
            val from = OffsetDateTime.now(ActivityDraft.WIB).withNano(0).toString()
            picked.toList().forEach { userId ->
                send(AdminForm(AdminResource.ASSIGNMENT, id = UUID.nameUUIDFromBytes("wizard:$ghId:$userId".toByteArray()).toString(),
                    values = mapOf("greenhouse_id" to ghId, "user_id" to userId, "effective_from" to from)))
                addedWorkers++
                picked = picked - userId
            }
            if (creating) {
                val form = AdminForm(AdminResource.USER, id = UUID.nameUUIDFromBytes("wizard-user:$ghId:${newEmail.trim().lowercase()}".toByteArray()).toString(),
                    values = mapOf("greenhouse_id" to ghId, "name" to newName.trim(), "email" to newEmail.trim(), "role" to "worker"))
                send(form, newPassword)
                addedWorkers++
                newName = ""; newEmail = ""; newPassword = ""
            }
            finished = true
        }
    }

    // Once the greenhouse is saved, leaving goes to the finish screen so the manager sees it already exists.
    val close: () -> Unit = { if (!busy) { if (ghDone && !finished) finished = true else onClose() } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        BackHandler { if (!busy) { if (finished) onClose() else if (step > 1) step-- else close() } }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = FarmSpace.Page)) {
                FarmSheetHeading("Tambah greenhouse", close, !busy)
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(vertical = FarmSpace.Medium),
                    verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                    if (finished) {
                        Text("Greenhouse siap dicatat", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                        Text(name.trim(), style = MaterialTheme.typography.titleMedium)
                        val facts = listOfNotNull(
                            if (periodDone) "Tanam saat ini sudah ditetapkan." else "Belum ada tanam saat ini.",
                            if (addedWorkers > 0) "$addedWorkers pekerja sudah ditambahkan." else null)
                        facts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (periodDone) Button(onClick = { onRecord(ghId) }, shape = FarmShape.Control,
                            modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("wizard-record")) { Text("Catat kegiatan") }
                        OutlinedButton(onClick = { onOpenGreenhouse(ghId) }, shape = FarmShape.Control,
                            modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("wizard-open")) { Text("Ke greenhouse") }
                    } else {
                        Text("Langkah $step dari 3", Modifier.testTag("wizard-step").semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        progress?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        error?.let { Notice(it, true) }
                        when (step) {
                            1 -> {
                                Text("Greenhouse", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                                FinanceField("Nama greenhouse", name, { name = it }, "wizard-name", fieldErrors["name"], enabled = !busy && !ghDone)
                                Button(onClick = ::saveGreenhouse, enabled = !busy, shape = FarmShape.Control,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("wizard-next")) { Text("Lanjut") }
                            }
                            2 -> {
                                Text("Mulai tanam", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                                FinanceField("Nama musim", season, { season = it }, "wizard-season", fieldErrors["season"], enabled = !busy && !seasonDone)
                                FinanceDateField("Tanggal mulai musim", start, { start = it }, "wizard-start", fieldErrors["start"], hint = "contoh 2026-11-10", enabled = !busy && !seasonDone)
                                FinanceField("Nama varietas", variety, { variety = it }, "wizard-variety", fieldErrors["variety"], enabled = !busy && !varietyDone)
                                FinanceField("Nama kelompok tanam", group, { group = it }, "wizard-group", fieldErrors["group"], enabled = !busy && !groupDone)
                                FinanceField("Jumlah tanaman", population, { population = it }, "wizard-population", fieldErrors["population"], KeyboardType.Number, enabled = !busy && !groupDone)
                                FinanceDateField("Tanggal semai", sowing, { sowing = it }, "wizard-sowing", fieldErrors["sowing"], hint = "contoh 2026-11-10", enabled = !busy && !groupDone)
                                FinanceDateField("Tanggal pindah tanam (opsional)", transplant, { transplant = it }, "wizard-transplant", fieldErrors["transplant"], hint = "contoh 2026-11-10", enabled = !busy && !groupDone)
                                Button(onClick = ::savePlanting, enabled = !busy, shape = FarmShape.Control,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("wizard-next")) { Text("Simpan") }
                                OutlinedButton(onClick = { step = 3 }, enabled = !busy, shape = FarmShape.Control,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("wizard-skip")) { Text("Lewati") }
                            }
                            else -> {
                                Text("Pekerja", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                                if (workers.isNotEmpty()) FarmSection("Pekerja yang sudah ada") {
                                    workers.forEach { worker ->
                                        val id = worker.getString("id")
                                        FarmCheckbox(worker.optString("name").ifBlank { worker.optString("email") }, id in picked,
                                            { checked -> picked = if (checked) picked + id else picked - id }, Modifier.testTag("wizard-worker-$id"))
                                    }
                                }
                                FarmSection("Buat akun pekerja baru") {
                                    FinanceField("Nama", newName, { newName = it }, "wizard-user-name", fieldErrors["name"])
                                    FinanceField("Email", newEmail, { newEmail = it }, "wizard-user-email", fieldErrors["email"], KeyboardType.Email)
                                    OutlinedTextField(newPassword, { newPassword = it }, label = { Text("Password (minimal 12 karakter)") },
                                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None
                                            else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password,
                                            imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                        singleLine = true,
                                        isError = fieldErrors["password"] != null, supportingText = fieldErrors["password"]?.let { m -> { Text(m) } },
                                        modifier = Modifier.fillMaxWidth().testTag("wizard-user-password"), enabled = !busy)
                                    FarmCheckbox("Tampilkan password", showPassword, { showPassword = it }, Modifier.testTag("wizard-show-password"))
                                }
                                Button(onClick = ::saveWorkers, enabled = !busy, shape = FarmShape.Control,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("wizard-next")) { Text("Selesai") }
                                OutlinedButton(onClick = { finished = true },
                                    enabled = !busy && picked.isEmpty() && newName.isBlank() && newEmail.isBlank() && newPassword.isEmpty(),
                                    shape = FarmShape.Control,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("wizard-skip")) { Text("Lewati") }
                            }
                        }
                        if (step > 1) TextButton(onClick = { step-- }, enabled = !busy,
                            modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("wizard-back")) { Text("Kembali") }
                    }
                }
            }
        }
    }
}
