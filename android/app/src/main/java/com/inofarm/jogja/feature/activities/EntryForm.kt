package com.inofarm.jogja.feature.activities

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.FarmCheckbox
import com.inofarm.jogja.core.design.FarmChoiceChips
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmIcon
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.measureText
import com.inofarm.jogja.feature.Choice
import com.inofarm.jogja.feature.MasterGap
import com.inofarm.jogja.feature.FarmContextStrip
import com.inofarm.jogja.feature.FarmDetailCard
import com.inofarm.jogja.feature.FarmFieldPair
import com.inofarm.jogja.feature.FarmFormBar
import com.inofarm.jogja.feature.FarmFormFrame
import com.inofarm.jogja.feature.FarmFormHeader
import com.inofarm.jogja.feature.FarmFormSection
import com.inofarm.jogja.feature.FarmInfoCard
import com.inofarm.jogja.feature.FarmReviewFrame
import com.inofarm.jogja.feature.FinanceDateField
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.FinanceTimeField
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.activities.domain.EntryDraft
import com.inofarm.jogja.feature.activities.domain.MaterialDraft
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.rememberEntryCancel
import com.inofarm.jogja.feature.whenLabel
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The bar of the greenhouse-by-greenhouse nutrition check, when the entry form runs inside it. */
internal class EntryRun(val step: Int, val names: List<String>, val mainLabel: String, val onSkip: () -> Unit, val onMain: () -> Unit)

private const val TYPED = "__typed"
internal val SprayPeriods = linkedMapOf("morning" to "Pagi", "afternoon" to "Sore")
internal val MeasureMoments = linkedMapOf("before_injection" to "Sebelum inject", "after_injection" to "Sesudah inject")
private fun option(id: String, name: String) = JSONObject().put("id", id).put("name", name)

/** Sown plantings of the entry's season, one per variety, named after the variety (K2: groups are not shown). */
internal fun CultivationUiState.entryVarieties(entry: EntryDraft, today: LocalDate = LocalDate.now(com.inofarm.jogja.feature.activities.domain.ActivityDraft.WIB)): List<JSONObject> =
    groups.filter {
        it.optString("season_id") == entry.seasonId && it.optBoolean("active", true) &&
            runCatching { LocalDate.parse(it.getString("sowing_date")) <= today }.getOrDefault(false)
    }.sortedBy { it.optString("sowing_date") + it.optString("name") }.map { group ->
        option(group.getString("id"), varieties.firstOrNull { it.optString("id") == group.optString("variety_id") }?.optString("name") ?: group.optString("name"))
    }

/** "HST 30 · diisi 17 Nov": the plant age as Inofarm last typed it on this device's records (K6), or null. */
internal fun lastTypedAge(records: List<LocalRecord>, greenhouse: String): String? = records
    .filter { it.kind == "activity" && it.greenhouseId == greenhouse && it.body.optString("record_state", "active") == "active" }
    .mapNotNull { row ->
        val form = row.body.optJSONObject("_local_form")
        val basis = (form ?: row.body).optString("age_basis").takeIf { it == "hss" || it == "hst" } ?: return@mapNotNull null
        val day = (form ?: row.body).optString("age_day").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
        val at = runCatching { OffsetDateTime.parse(row.body.optString("occurred_at")) }.getOrNull() ?: return@mapNotNull null
        Triple(at, basis, day)
    }.maxByOrNull { it.first }?.let { (at, basis, day) ->
        "${basis.uppercase()} $day · diisi ${at.atZoneSameInstant(com.inofarm.jogja.feature.activities.domain.ActivityDraft.WIB)
            .format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id-ID")))}"
    }

@Composable
internal fun EntryForm(state: ActivityUiState, context: CultivationUiState, vm: ActivityViewModel, worker: Boolean, run: EntryRun? = null) {
    val entry = state.entry ?: return
    val greenhouse = context.greenhouses.find { it.optString("id") == entry.greenhouseId }?.optString("name") ?: "Greenhouse terakhir tersimpan"
    val varieties = context.entryVarieties(entry)
    val cancel = if (worker || run != null) null else rememberEntryCancel(state.busy, vm::entryChanged, vm::cancelEntry)
    FarmFormFrame(
        header = {
            if (cancel == null) FarmFormHeader(if (run != null) "Cek nutrisi semua greenhouse" else "Catat kegiatan", state.saved, "finance-draft-status",
                "finance-close", vm::saveEntryAndClose, !state.busy)
            else FarmFormHeader("Catat kegiatan", state.saved, "finance-draft-status", "finance-close", cancel, !state.busy,
                showDraftStatus = false, closeLabel = "Batal", step = 1)
        },
        bar = {
            when {
                run != null -> FarmFormBar("nutrition-run-skip", run.onSkip, "nutrition-run-next", run.mainLabel, run.onMain,
                    enabled = !state.busy, busy = state.busy, saveLabel = "Lewati")
                cancel == null -> FarmFormBar("finance-save-draft", vm::saveEntryAndClose, "finance-review", "Periksa", vm::reviewEntry,
                    enabled = !state.busy, busy = state.busy, saveLabel = "Simpan sebagai draf")
                else -> FarmFormBar("finance-cancel", cancel, "finance-review", "Simpan", vm::reviewEntry, enabled = !state.busy,
                    busy = state.busy, saveLabel = "Batal")
            }
        }
    ) {
        if (run != null) Text("Greenhouse ${run.step} dari ${run.names.size}", style = MaterialTheme.typography.labelLarge)
        FarmContextStrip(greenhouse, varieties.joinToString(" dan ") { it.getString("name") }.ifBlank { null }, null)
        if (entry.scheduleId.isNotBlank()) {
            val type = com.inofarm.jogja.feature.activities.domain.ActivityType.entries.find { it.wire == entry.scheduleType }?.label ?: "Kegiatan"
            Text("Dari jadwal: $type. Isi bagian ${EntryDraft.scheduleSection(entry.scheduleType)} supaya jadwal tercatat selesai.",
                Modifier.testTag("entry-schedule"), style = MaterialTheme.typography.bodyMedium)
        }
        EntryFields(state, context, vm, entry, varieties, worker)
    }
}

@Composable
private fun ColumnScope.EntryFields(state: ActivityUiState, context: CultivationUiState, vm: ActivityViewModel, entry: EntryDraft,
                                    varieties: List<JSONObject>, worker: Boolean) {
    val errors = state.errors
    FarmFormSection("Waktu") {
        FarmFieldPair({
            FinanceDateField("Tanggal", entry.date, { value -> vm.editEntry { it.copy(date = value) } }, "entry-date", errors["occurred_at"])
        }, {
            FinanceTimeField("Jam WIB", entry.time, { value -> vm.editEntry { it.copy(time = value) } }, "entry-time")
        }, minPairWidth = 340.dp)
    }
    FarmFormSection("Umur tanaman") {
        Text("Diisi sesuai fase dari Inofarm, bukan dihitung aplikasi.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        lastTypedAge(state.records, entry.greenhouseId)?.let {
            Text("Terakhir: $it", Modifier.testTag("entry-age-last"), style = MaterialTheme.typography.bodyMedium)
        }
        FarmChoiceChips("Satuan", entry.ageBasis.ifBlank { null }, listOf(option("hss", "HSS"), option("hst", "HST")), "entry-age-basis") { value ->
            vm.editEntry { it.copy(ageBasis = value) }
        }
        FinanceField("Hari ke", entry.ageDay, { value -> vm.editEntry { it.copy(ageDay = value.filter(Char::isDigit).take(4)) } },
            "entry-age-day", errors["age"], KeyboardType.Number, suffix = entry.ageBasis.uppercase().ifBlank { null })
    }
    FarmFormSection("Pengukuran") {
        FarmFieldPair({
            FinanceField("EC", entry.ec, { value -> vm.editEntry { it.copy(ec = value) } }, "entry-ec", errors["manual_ec"] ?: errors["manual_pair"],
                KeyboardType.Decimal, prominent = true)
        }, {
            FinanceField("PPM", entry.ppm, { value -> vm.editEntry { it.copy(ppm = value) } }, "entry-ppm", errors["manual_ppm"],
                KeyboardType.Decimal, suffix = "ppm", prominent = true)
        })
        FarmFieldPair({
            FinanceField("pH", entry.ph, { value -> vm.editEntry { it.copy(ph = value) } }, "entry-ph", errors["manual_ph"], KeyboardType.Decimal)
        }, {
            FinanceField("Suhu air", entry.waterTemperature, { value -> vm.editEntry { it.copy(waterTemperature = value) } }, "entry-water-temperature",
                errors["observations.water_temperature_c"], KeyboardType.Decimal, suffix = "°C")
        })
        FarmChoiceChips("Diukur", entry.moment.ifBlank { null }, MeasureMoments.map { (id, name) -> option(id, name) }, "entry-moment") { value ->
            vm.editEntry { it.copy(moment = if (it.moment == value) "" else value) }
        }
        Text("EC dan PPM diisi berpasangan. Kosongkan semua bila tidak mengukur.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val units = context.units.filter { it.optString("greenhouse_id") == entry.greenhouseId && it.optBoolean("active", true) }
    val ghMaterials = context.materials.filter { it.optString("greenhouse_id") == entry.greenhouseId && it.optBoolean("active", true) }
    FarmFormSection("Nutrisi AB Mix") {
        val materials = ghMaterials.filter { it.optString("purpose") != "spray" }
        entry.nutrition.forEachIndexed { index, row ->
            fun change(updated: MaterialDraft) { vm.editEntry { it.copy(nutrition = it.nutrition.toMutableList().apply { set(index, updated) }) } }
            FarmDetailCard("Hapus bahan ${index + 1}", { vm.editEntry { it.copy(nutrition = it.nutrition.filterIndexed { i, _ -> i != index }) } },
                "entry-nutrition-remove-$index") {
                val material = materials.find { it.optString("id") == row.materialId }
                Choice("Bahan ${index + 1}", row.materialId, materials, "entry-nutrition-material-$index",
                    master = MasterGap(com.inofarm.jogja.feature.admin.AdminResource.MATERIAL, entry.greenhouseId)) { change(row.copy(materialId = it, unitId = "")) }
                FarmFieldPair({
                    FinanceField("Jumlah", row.quantity, { change(row.copy(quantity = it)) }, "entry-nutrition-quantity-$index",
                        errors["nutrition.$index"], KeyboardType.Decimal)
                }, {
                    Choice("Satuan", row.unitId, units.filter { it.optString("dimension") == material?.optString("dimension") },
                        "entry-nutrition-unit-$index", master = MasterGap(com.inofarm.jogja.feature.admin.AdminResource.UNIT, entry.greenhouseId)) { change(row.copy(unitId = it)) }
                }, minPairWidth = 360.dp) // inside a card the pair stacks before a unit name has to break
            }
        }
        OutlinedButton(onClick = { vm.editEntry { it.copy(nutrition = it.nutrition + MaterialDraft()) } }, enabled = entry.nutrition.size < 30,
            shape = FarmShape.Control, modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("entry-nutrition-add")) {
            Text(if (entry.nutrition.isEmpty()) "Tambah bahan nutrisi" else "Tambah bahan lain")
        }
    }
    FarmFormSection("Penyemprotan") {
        if (!entry.sprayOpen) {
            Text("Opsional. Buka bila hari ini ada penyemprotan.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { vm.editEntry { it.copy(sprayOpen = true, sprayGroups = varieties.map { v -> v.getString("id") }.toSet(),
                spray = it.spray.ifEmpty { listOf(MaterialDraft()) }) } }, shape = FarmShape.Control,
                modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("entry-spray-open")) { Text("Tambah penyemprotan") }
        } else {
            FarmFieldPair({
                FinanceDateField("Tanggal semprot", entry.sprayDate, { value -> vm.editEntry { it.copy(sprayDate = value) } }, "entry-spray-date",
                    errors["spray_date"])
            }, {
                FarmChoiceChips("Waktu", entry.sprayPeriod.ifBlank { null }, SprayPeriods.map { (id, name) -> option(id, name) }, "entry-spray-period") { value ->
                    vm.editEntry { it.copy(sprayPeriod = value) }
                }
            }, minPairWidth = 340.dp)
            errors["spray_period"]?.let { Notice(it, true) }
            if (varieties.size > 1) {
                Text("Varietas yang disemprot", style = MaterialTheme.typography.titleSmall)
                varieties.forEach { variety ->
                    val id = variety.getString("id")
                    FarmCheckbox(variety.getString("name"), id in entry.sprayGroups, { checked ->
                        vm.editEntry { it.copy(sprayGroups = if (checked) it.sprayGroups + id else it.sprayGroups - id) }
                    }, Modifier.testTag("entry-spray-group-$id"))
                }
                errors["spray_groups"]?.let { Notice(it, true) }
            }
            val sprayList = ghMaterials.filter { it.optString("purpose") == "spray" }.sortedBy { it.optString("name").lowercase() } +
                option(TYPED, "Ketik bahan lain")
            entry.spray.forEachIndexed { index, row ->
                fun change(updated: MaterialDraft) { vm.editEntry { it.copy(spray = it.spray.toMutableList().apply { set(index, updated) }) } }
                FarmDetailCard("Hapus bahan ${index + 1}", { vm.editEntry { it.copy(spray = it.spray.filterIndexed { i, _ -> i != index }) } },
                    "entry-spray-remove-$index") {
                    // A typed name is kept with a leading space while still empty, so the name field stays open.
                    val typed = row.materialId.isBlank() && row.name.isNotEmpty()
                    val selected = if (row.materialId.isNotBlank()) row.materialId else if (typed) TYPED else ""
                    Choice("Bahan ${index + 1}", selected, sprayList, "entry-spray-material-$index") { value ->
                        change(if (value == TYPED) row.copy(materialId = "", name = row.name.ifEmpty { " " }) else row.copy(materialId = value, name = ""))
                    }
                    if (typed) FinanceField("Nama bahan", row.name.trimStart(), { change(row.copy(materialId = "", name = it.ifEmpty { " " })) },
                        "entry-spray-name-$index", hint = "Tersimpan sebagai bahan baru untuk semua greenhouse")
                    FarmFieldPair({
                        FinanceField("Dosis (opsional)", row.quantity, { change(row.copy(quantity = it)) }, "entry-spray-quantity-$index",
                            errors["spray.$index"], KeyboardType.Decimal)
                    }, {
                        Choice("Satuan", row.unitId, units, "entry-spray-unit-$index", master = MasterGap(com.inofarm.jogja.feature.admin.AdminResource.UNIT, entry.greenhouseId)) { change(row.copy(unitId = it)) }
                    }, minPairWidth = 360.dp)
                }
            }
            errors["spray"]?.let { Notice(it, true) }
            OutlinedButton(onClick = { vm.editEntry { it.copy(spray = it.spray + MaterialDraft()) } }, enabled = entry.spray.size < 30,
                shape = FarmShape.Control, modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("entry-spray-add")) { Text("Tambah bahan semprot") }
            TextButton(onClick = { vm.editEntry { it.copy(sprayOpen = false, spray = emptyList(), sprayGroups = emptySet(), sprayPeriod = "") } },
                modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("entry-spray-close")) { Text("Hapus penyemprotan") }
        }
    }
    FarmFormSection("Stadia/Fase") {
        FinanceField("Stadia/Fase saat ini (opsional)", entry.stage, { value -> vm.editEntry { it.copy(stage = value.take(100)) } }, "entry-stage",
            errors["actual_stage"], hint = "Tulis sesuai pengamatan, misalnya pembesaran buah")
    }
    FarmFormSection("Catatan") {
        FinanceField("Catatan (opsional)", entry.notes, { value -> vm.editEntry { it.copy(notes = value) } }, "entry-notes", errors["notes"], singleLine = false)
    }
    errors["entry"]?.let { Notice(it, true) }
    if (worker) FarmInfoCard { Text("Satu isian ini berlaku untuk seluruh greenhouse. Penyemprotan dicatat per varietas yang dipilih.") }
    state.message?.let { Notice(it, true) }
}

/** Manager Ringkasan and worker Periksa: every section the entry will send, before it is saved. */
@Composable
internal fun EntryReview(state: ActivityUiState, context: CultivationUiState, vm: ActivityViewModel, worker: Boolean) {
    val entry = state.entry ?: return
    val greenhouse = context.greenhouses.find { it.optString("id") == entry.greenhouseId }?.optString("name") ?: "Greenhouse terakhir tersimpan"
    val names = context.entryVarieties(entry).associate { it.getString("id") to it.getString("name") }
    fun material(row: MaterialDraft): String {
        val name = context.materials.firstOrNull { it.optString("id") == row.materialId }?.optString("name") ?: row.name.trim().ifBlank { "Bahan" }
        val unit = context.units.firstOrNull { it.optString("id") == row.unitId }?.optString("name")
        return if (row.quantity.isBlank()) name else "$name ${row.quantity}${unit?.let { " $it" }.orEmpty()}"
    }
    FarmReviewFrame("kegiatan", "$greenhouse · ${whenLabel(entry.date, entry.time)}", FarmIcon.CheckList, state.busy, "finance-queue", "finance-back",
        vm::backToForm, vm::queueEntry, mainLabel = if (worker) "Kirim" else "Simpan", summary = !worker,
        hint = "Pastikan isinya benar. Setelah dikirim, catatan terkirim otomatis saat ada internet.") {
        state.message?.let { Notice(it, true) }
        FarmFormSection("Umum") {
            FarmFact("Umur tanaman", "${entry.ageBasis.uppercase()} ${entry.ageDay}")
            FarmFact("Berlaku untuk", "Seluruh greenhouse")
            if (entry.notes.isNotBlank()) FarmFact("Catatan", entry.notes)
        }
        if (entry.hasMeasurement) FarmFormSection("Pengukuran") {
            FarmFact("EC", "${measureText(entry.ec)} mS/cm"); FarmFact("PPM", "${measureText(entry.ppm)} ppm")
            if (entry.ph.isNotBlank()) FarmFact("pH", measureText(entry.ph))
            if (entry.waterTemperature.isNotBlank()) FarmFact("Suhu air", "${entry.waterTemperature} °C")
            MeasureMoments[entry.moment]?.let { FarmFact("Diukur", it) }
        }
        if (entry.nutrition.isNotEmpty()) FarmFormSection("Nutrisi AB Mix") { entry.nutrition.forEach { Text(material(it)) } }
        if (entry.hasSpray) FarmFormSection("Penyemprotan") {
            FarmFact("Waktu", "${entry.sprayDate} · ${SprayPeriods[entry.sprayPeriod].orEmpty()}")
            FarmFact("Varietas", entry.sprayGroups.sorted().joinToString(", ") { names[it] ?: "Varietas" })
            entry.spray.forEach { Text(material(it)) }
        }
        if (entry.stage.isNotBlank()) FarmFormSection("Stadia/Fase") { Text(entry.stage) }
        if (worker) FarmInfoCard { Text("Muncul di Riwayat dengan label Belum terkirim sampai diterima, lalu menunggu cek pengelola.") }
    }
}
