package com.inofarm.jogja.feature.admin

import com.inofarm.jogja.core.design.FarmFullScreenDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.design.FarmCheckbox
import com.inofarm.jogja.core.design.FarmChoiceChips
import com.inofarm.jogja.core.design.FarmControlIcon
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmIcon
import com.inofarm.jogja.core.design.FarmOption
import com.inofarm.jogja.core.design.FarmPicker
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.LocalFarmDialogPicker
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.Choice
import com.inofarm.jogja.feature.FarmFormBar
import com.inofarm.jogja.feature.FarmFormFrame
import com.inofarm.jogja.feature.FarmFormHeader
import com.inofarm.jogja.feature.FarmFormSection
import com.inofarm.jogja.feature.FarmReviewFrame
import com.inofarm.jogja.feature.FarmRoleCards
import com.inofarm.jogja.feature.FinanceDateField
import com.inofarm.jogja.feature.FinanceTimeField
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.Notice
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Kelola below the tab (21 Nov 2026): one full-screen host with a stack of pages, organised by object. A greenhouse opens
 * its settings page, a list opens per greenhouse, a row opens in a sheet, an account opens its own page. Every save still
 * goes through [AdminViewModel] (journal, summary step, conflicts), so this file only decides what is shown.
 *
 * Routes are plain text so the stack survives rotation: "gh:<id>", "list:<RESOURCE>:<id or ->", "people",
 * "account:<id>" and "off:<id>".
 */
@Composable fun AdminHost(
    session: FinanceSession, repository: AdminRepository, onAccepted: () -> Unit,
    initialResource: AdminResource = AdminResource.GREENHOUSE,
    onReviewPeriod: ((String) -> Unit)? = null,
    onClose: () -> Unit,
    initialGreenhouse: String? = null,
    initialEdit: Boolean = false,
    /** Adding a greenhouse goes through the three-step wizard, the same as from Beranda and Kelola. */
    onAddGreenhouse: (() -> Unit)? = null,
    /** Opens the wizard on its first sowing step for a greenhouse that has nothing planted. */
    onStartPlanting: ((String) -> Unit)? = null,
    /** Records of one greenhouse still waiting on this device; a greenhouse is only turned off once they are sent. */
    unsentFor: (String) -> Int = { 0 },
) {
    if(session.role!=UserRole.MANAGER) {LaunchedEffect(Unit) {onClose()};return}
    val vm:AdminViewModel=viewModel(key="admin-${session.scope.key}-${repository.vault.generation}",factory=object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>)=AdminViewModel(repository,session) as T
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val first = remember(initialResource, initialGreenhouse) { initialRoute(initialResource, initialGreenhouse) }
    var stack by rememberSaveable(first) { mutableStateOf(listOf(first)) }
    val route = stack.last()
    // Which fields the sheet shows, and whether it opens on its summary; set by the page that opened the form.
    var sheetKeys by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetSummary by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.form) { if (state.form == null) { sheetKeys = null; sheetSummary = false } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {vm.flush()}
    val close={if(!state.busy && vm.flush()) onClose()}
    val push: (String) -> Unit = { next -> if (!state.busy && vm.flush()) stack = stack + next }
    val replaceTop: (String) -> Unit = { next -> if (!state.busy) stack = stack.dropLast(1) + next }
    val back: () -> Unit = { if (stack.size > 1) { if (!state.busy) stack = stack.dropLast(1) } else close() }
    // Every page reads its data again when it comes to the top, after the view model has finished what it was doing.
    LaunchedEffect(route) {
        vm.state.first { !it.busy }
        enterRoute(vm, route)
    }
    var addOpened by rememberSaveable(first) { mutableStateOf(false) }
    // Opened from an empty picker in a form: once the list for that greenhouse is loaded, go straight to its add form.
    LaunchedEffect(state.resource, state.greenhouse, state.busy, state.listChecked) {
        val target = parseList(first)
        if (!addOpened && initialEdit && target != null && route == first && state.resource == target.first &&
            state.greenhouse == target.second && !state.busy && state.listChecked) {
            addOpened = true
            vm.open()
        }
    }
    val edit: (JSONObject?, Set<String>?, Map<String, String>) -> Unit = { row, keys, changes ->
        sheetKeys = keys?.joinToString(",")
        sheetSummary = changes.isNotEmpty()
        when {
            row == null -> vm.open()
            changes.isEmpty() -> vm.open(row)
            else -> vm.openChanged(row, changes)
        }
    }
    val greenhouseNames = state.options["greenhouses"].orEmpty()
    fun nameOf(id: String?) = greenhouseNames.find { it.optString("id") == id }?.optString("name").orEmpty()
    FarmFullScreenDialog(onDismissRequest = close) {
        BackHandler { if (state.editing) vm.requestCancel() else back() }
        val statusStrip = MaterialTheme.colorScheme.surface
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CompositionLocalProvider(LocalFarmDialogPicker provides true) {
                val form = state.form
                // A new account needs role cards and a password pair, so it keeps the full-screen form.
                val fullScreen = state.editing && form != null && form.resource == AdminResource.USER && form.version == null
                if (fullScreen) Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = FarmSpace.Page)) {
                    AdminEditor(state, vm, onAccepted)
                } else Column(Modifier.fillMaxSize()
                    // Every Kelola page opens with a surface-coloured top bar; the status bar strip continues it.
                    .drawBehind { drawRect(statusStrip, size = androidx.compose.ui.geometry.Size(size.width, statusTop.toPx())) }
                    .safeDrawingPadding().imePadding()) {
                    val resume: @Composable () -> Unit = {
                        if (form != null && !state.editing) Button(onClick = vm::resume, enabled = !state.busy, shape = FarmShape.Control,
                            modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("admin-resume")) {
                            Text(if (state.pending) "Kirim ulang ${form.resource.label.lowercase()}" else "Lanjutkan isian ${form.resource.label.lowercase()} yang belum disimpan")
                        }
                    }
                    when {
                        route.startsWith("gh:") -> GreenhouseSettingsPage(route.removePrefix("gh:"), state, vm, back, push, edit, resume,
                            onReviewPeriod = onReviewPeriod?.let { review -> { id: String -> if (vm.flush()) review(id) } },
                            onStartPlanting = onStartPlanting?.let { start -> { id: String -> if (vm.flush()) start(id) } },
                            onCopy = { source -> vm.copyMasters(source, onAccepted) })
                        route.startsWith("off:") -> DeactivatePage(route.removePrefix("off:"), state, vm, back, unsentFor, onAccepted)
                        route == "people" -> PeoplePage(session, state, back, push, edit, resume)
                        route.startsWith("account:") -> AccountPage(session, route.removePrefix("account:"), state, back, edit)
                        else -> parseList(route)?.let { (resource, gh) ->
                            MasterListPage(resource, gh, nameOf(gh), state, vm, back, resume,
                                onSwitch = { other -> replaceTop(listRoute(other, gh)) },
                                onAdd = { if (resource == AdminResource.GREENHOUSE && onAddGreenhouse != null) { if (vm.flush()) onAddGreenhouse() } else edit(null, null, emptyMap()) },
                                onOpen = { row -> if (resource == AdminResource.GREENHOUSE) push("gh:${row.getString("id")}") else edit(row, null, emptyMap()) },
                                onCopy = { source -> vm.copyMasters(source, onAccepted) })
                        }
                    }
                }
                if (state.editing && form != null && !fullScreen) AdminSheetEditor(state, vm, onAccepted,
                    sheetKeys?.split(',')?.filter(String::isNotBlank)?.toSet(), sheetSummary, nameOf(form.values["greenhouse_id"] ?: state.greenhouse))
            }
        }
        state.replace?.let { target ->
            val currentLabel = state.form?.resource?.label?.lowercase().orEmpty()
            AlertDialog(onDismissRequest = vm::keepCurrent, containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(if (state.pending) "Batalkan pengiriman sebelumnya?" else "Buang isian sebelumnya?") },
                text = { Text(if (state.pending) "Pengiriman $currentLabel belum selesai. Perubahan itu mungkin sudah tersimpan; periksa daftarnya nanti."
                    else "Isian $currentLabel yang belum disimpan akan dibuang supaya Anda bisa membuka ${target.resource.label.lowercase()} ini.") },
                confirmButton = { Button(onClick = vm::confirmReplace, shape = FarmShape.Control, modifier = Modifier.testTag("admin-replace-confirm")) { Text("Buang dan lanjut") } },
                dismissButton = { TextButton(onClick = vm::resume, modifier = Modifier.testTag("admin-replace-keep")) { Text("Buka isian sebelumnya") } })
        }
        if (state.confirmCancel) AlertDialog(onDismissRequest = vm::dismissCancel, containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (state.pending) "Batalkan pengiriman?" else "Buang perubahan?") },
            text = { Text(if (state.pending) "Perubahan ini mungkin sudah sampai ke server. Setelah dibatalkan, periksa daftarnya."
                else "Isian ini belum disimpan dan akan dibuang. Data yang sudah tersimpan tidak berubah.") },
            confirmButton = { TextButton(onClick = vm::discard, modifier = Modifier.testTag("admin-cancel-confirm")) { Text(if (state.pending) "Batalkan pengiriman" else "Buang") } },
            dismissButton = { TextButton(onClick = vm::dismissCancel) { Text("Lanjut mengisi") } })
    }
}

internal fun listRoute(resource: AdminResource, greenhouse: String?) = "list:${resource.name}:${greenhouse ?: "-"}"

internal fun parseList(route: String): Pair<AdminResource, String?>? {
    val parts = route.split(':')
    if (parts.size != 3 || parts[0] != "list") return null
    val resource = AdminResource.entries.find { it.name == parts[1] } ?: return null
    return resource to parts[2].takeUnless { it == "-" }
}

internal fun initialRoute(resource: AdminResource, greenhouse: String?): String = when {
    resource == AdminResource.USER -> "people"
    resource == AdminResource.GREENHOUSE && greenhouse != null -> "gh:$greenhouse"
    else -> listRoute(resource, greenhouse)
}

/** Loads what a page needs. A list that is already on screen for the same greenhouse is read again too: it may have changed. */
private fun enterRoute(vm: AdminViewModel, route: String) {
    val now = vm.state.value
    when {
        route.startsWith("gh:") -> vm.openGreenhouse(route.removePrefix("gh:"))
        route.startsWith("off:") -> vm.openGreenhouse(route.removePrefix("off:"))
        route == "people" -> vm.choose(AdminResource.USER, null)
        // The account page shows a row of the people list; it is read only when that list is not loaded.
        route.startsWith("account:") -> if (now.resource != AdminResource.USER || !now.listChecked) vm.choose(AdminResource.USER, null)
        else -> parseList(route)?.let { (resource, gh) -> vm.choose(resource, gh) }
    }
}

internal fun deactivateNote(resource: AdminResource): String = when (resource) {
    AdminResource.GREENHOUSE -> "Data lama tidak dihapus. Greenhouse dinonaktifkan dan riwayatnya tetap ada."
    AdminResource.USER -> "Akun ini tidak bisa masuk lagi. Riwayatnya tetap ada."
    AdminResource.ASSIGNMENT -> "Pekerja tidak lagi tercatat bekerja di greenhouse ini."
    else -> "Data lama tidak dihapus. Riwayatnya tetap ada."
}

/** A value as the summary shows it: chosen names instead of ids, Ya/Tidak for switches. */
internal fun adminDisplay(field: AdminField, form: AdminForm, state: AdminState): String {
    val value = form.values[field.key].orEmpty()
    return if (field.type == "boolean") {
        if (value == "true") "Ya" else "Tidak"
    } else field.choices[value] ?: state.options[field.source]?.find { it.optString("id") == value }
        ?.optString("name") ?: value.ifBlank { "Belum diisi" }
}

/** What the summary asks before a change that has consequences, with the label of its save button. */
internal fun adminConfirmation(form: AdminForm, state: AdminState, password: String): Triple<String, String, String>? {
    val original = state.rows.find { it.optString("id") == form.id }
    return when {
        form.action == "close" -> Triple("Tutup musim?", "Catatan baru ditahan sampai musim dibuka lagi.", "Tutup musim")
        form.action == "reopen" -> Triple("Buka kembali musim?", "Catatan baru bisa masuk lagi ke musim ini.", "Buka kembali")
        form.version != null && original?.optBoolean("active", true) != false && form.values["active"] == "false" ->
            Triple("Nonaktifkan ${form.resource.label.lowercase()}?", deactivateNote(form.resource), "Nonaktifkan")
        form.version != null && original?.optBoolean("active", true) == false && form.values["active"] == "true" ->
            Triple("Aktifkan lagi ${form.resource.label.lowercase()}?", "Bisa dipilih lagi di form.", "Aktifkan")
        form.resource == AdminResource.USER && form.version != null && original != null && original.optString("role") != form.values["role"] ->
            Triple("Ubah peran akun?", "Akun ini perlu masuk ulang.", "Ubah peran")
        form.resource == AdminResource.USER && form.version != null && password.isNotEmpty() ->
            Triple("Reset password?", "Akun ini keluar dari semua perangkat dan perlu masuk ulang.", "Reset password")
        else -> null
    }
}

/** One field of an admin form as an input, the same in the sheet and in the full-screen form. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AdminFieldInput(field: AdminField, state: AdminState, vm: AdminViewModel) {
    val form = state.form ?: return
    val user = form.resource == AdminResource.USER
    val value = form.values[field.key].orEmpty()
    var unassignedEmail by rememberSaveable(form.id, form.values["greenhouse_id"]) { mutableStateOf("") }
    if (state.pending || state.busy || form.version != null && field.key == "greenhouse_id") { FarmFact(field.label, adminDisplay(field, form, state)); return }
    when {
        field.type == "boolean" -> FarmCheckbox(field.label, value == "true", { vm.edit(field.key, it.toString()) })
        user && field.key == "role" -> {
            FarmRoleCards(value, field.choices, "admin-role") { vm.edit(field.key, it) }
            if (value.isBlank()) Text("Pilih peran akun untuk melanjutkan.", style = MaterialTheme.typography.bodySmall)
        }
        field.source != null || field.choices.isNotEmpty() -> {
            val options = if (field.source != null) state.options[field.source].orEmpty().map { row -> JSONObject(row.toString()).put("name", row.optString("name").ifBlank { row.optString("id") }) }
                else field.choices.map { (id, label) -> JSONObject().put("id", id).put("name", label) }
            if (field.choices.isNotEmpty() || user && field.key == "greenhouse_id")
                FarmChoiceChips(if (user && field.key == "greenhouse_id") "Pilih satu greenhouse" else field.label, value, options, "admin-${field.key}", loading = state.optionsLoading) { vm.edit(field.key, it) }
            else Choice(field.label, value, options, "admin-${field.key}", loading = state.optionsLoading) { vm.edit(field.key, it) }
            if (field.source == "assignment-users" && !form.values["greenhouse_id"].isNullOrBlank()) {
                if (options.isEmpty() && !state.optionsLoading && state.message == null)
                    Text("Belum ada pekerja lain. Cari akun pekerja dengan email lengkap.")
                FinanceField("Email pekerja", unassignedEmail, { unassignedEmail = it }, "admin-unassigned-email", keyboard = KeyboardType.Email)
                TextButton(onClick = { vm.findUnassigned(unassignedEmail) }, enabled = unassignedEmail.isNotBlank() && !state.optionsLoading,
                    modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("admin-find-unassigned")) { Text("Cari akun") }
            }
        }
        field.type == "date" -> FinanceDateField(field.label + (if (field.optional) " (opsional)" else ""), value, { vm.edit(field.key, it) }, "admin-${field.key}")
        // A moment (schedule time, target validity, assignment) is picked as a WIB date and time, never typed as
        // "YYYY-MM-DDTHH:mm:ss+07:00" (QA 28 Nov 2026). The form keeps one value with an explicit +07:00 offset.
        field.type == "timestamp" -> {
            val decoded = remember(form.id, field.key) { runCatching { com.inofarm.jogja.core.cultivation.ManualTime.decode(value) }.getOrDefault("" to "") }
            var day by remember(form.id, field.key) { mutableStateOf(decoded.first) }
            var time by remember(form.id, field.key) { mutableStateOf(decoded.second) }
            // Half-typed text stays in the fields; only a whole date and time becomes the stored moment.
            fun store() = vm.edit(field.key, if (day.isBlank()) "" else runCatching {
                com.inofarm.jogja.core.cultivation.ManualTime.encode(day.trim(), time.trim().ifBlank { "00:00" }) }.getOrDefault("$day $time"))
            val label = field.label + (if (field.optional) " (opsional)" else "")
            FinanceDateField("$label · tanggal", day, { day = it; store() }, "admin-${field.key}")
            FinanceTimeField("$label · jam (WIB)", time, { time = it; store() }, "admin-${field.key}-time")
        }
        else -> FinanceField(field.label + (if (field.optional) " (opsional)" else ""), value, { vm.edit(field.key, it) }, "admin-${field.key}", keyboard = when (field.type) {
            "integer" -> KeyboardType.Number
            "decimal" -> KeyboardType.Decimal
            "email" -> KeyboardType.Email
            else -> KeyboardType.Text
        }, hint = null,
            leadingIcon = if (field.type == "email") ({ FarmControlIcon(FarmIcon.Mail, null) }) else null)
    }
}

/** Password and its repeat, for a new account and for a reset. */
@Composable internal fun AdminPasswordFields(state: AdminState, password: String, onPassword: (String) -> Unit,
                                             confirmation: String, onConfirmation: (String) -> Unit, required: Boolean) {
    val form = state.form ?: return
    var showPassword by remember { mutableStateOf(false) }
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.testTag("admin-password-toggle")) {
            FarmControlIcon(if (showPassword) FarmIcon.EyeOff else FarmIcon.Eye, if (showPassword) "Sembunyikan password" else "Tampilkan password")
        }
    }
    val visual = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation()
    if (!state.pending || state.needsPassword) FinanceField(
        if (state.pending) "Password yang sama seperti sebelumnya" else if (form.version == null) "Password awal" else if (required) "Password baru" else "Password baru (opsional)",
        password, onPassword, "admin-password", hint = if (!state.pending) "Minimal 12 karakter." else null,
        keyboard = KeyboardType.Password, enabled = !state.busy, visual = visual,
        leadingIcon = { FarmControlIcon(FarmIcon.Lock, null) }, trailingAction = toggle)
    if (!state.pending && (form.version == null || password.isNotEmpty() || required)) FinanceField("Ulangi password", confirmation, onConfirmation, "admin-password-confirmation",
        error = if (confirmation.isNotEmpty() && confirmation != password) "Isian harus sama dengan password di atas." else null,
        keyboard = KeyboardType.Password, enabled = !state.busy, visual = visual, leadingIcon = { FarmControlIcon(FarmIcon.Lock, null) })
    if (state.pending) Text("Masukkan password yang sama untuk mengirim ulang.")
}

/** Values the server has now, shown after a version conflict so the manager can compare before saving again. */
@Composable internal fun AdminConflict(state: AdminState, vm: AdminViewModel) {
    val form = state.form ?: return
    state.server?.let { row ->
        Notice("Data ini sudah berubah di server sejak form dibuka. Isian Anda tetap tersimpan.")
        form.fields.forEach { field ->
            val value = when {
                row.isNull(field.key) -> "Belum diisi"
                row.opt(field.key) is Boolean -> if (row.optBoolean(field.key)) "Ya" else "Tidak"
                else -> row.optString(field.key)
            }
            Text("Terbaru · ${field.label}: $value")
        }
        TextButton(onClick = vm::useServer, enabled = !state.busy) { Text("Pakai data terbaru") }
    }
    if (state.rejected) TextButton(onClick = vm::reviseRejected, enabled = !state.busy, modifier = Modifier.testTag("admin-revise")) { Text("Tetap pakai isian saya") }
}

/** The full-screen form, kept for a new account (role cards and a password pair). Other records open in the sheet. */
@Composable private fun AdminEditor(state:AdminState,vm:AdminViewModel,onAccepted:()->Unit) {
    val form=state.form ?: return
    var password by remember(form.id,state.pending) {mutableStateOf("")}
    var passwordConfirmation by remember(form.id,state.pending) {mutableStateOf("")}
    val confirmPassword=form.resource==AdminResource.USER && !state.pending && (form.version==null || password.isNotEmpty())
    val action = if (form.version == null) "Tambah" else "Ubah"
    val confirmation = adminConfirmation(form, state, password)
    val passwordMatches=!confirmPassword || password.length>=12 && password==passwordConfirmation
    val user = form.resource==AdminResource.USER
    // Simpan opens the summary; its own Simpan sends. A retry after a lost connection sends at once with the same content.
    var summary by rememberSaveable(form.id) {mutableStateOf(false)}
    // A refusal reopens the form at the field to fix, not the summary.
    LaunchedEffect(state.message) { if (state.message != null) summary = false }
    val showSummary = summary && !state.pending && !state.rejected && state.server == null
    BackHandler(enabled = showSummary && !state.busy) { summary = false }
    if (showSummary) {
        val name = form.resource.label.lowercase()
        val icon = when (form.resource) { AdminResource.GREENHOUSE -> FarmIcon.Greenhouse; AdminResource.USER -> FarmIcon.Account; else -> FarmIcon.CheckList }
        val subtitle = listOfNotNull(action, form.values["name"]?.takeIf { it.isNotBlank() }).joinToString(" · ")
        FarmReviewFrame(name, subtitle, icon, state.busy, "admin-save", "admin-summary-back", { summary = false }, { vm.submit(password, onAccepted) },
            mainLabel = confirmation?.third ?: "Simpan", summary = true,
            summaryHint = "Periksa sekali lagi. Simpan langsung mengirim perubahan ke server, jadi perlu koneksi internet.") {
            state.message?.let { Notice(it, true) }
            confirmation?.let { Notice(it.second) }
            FarmFormSection("Rincian") {
                form.fields.filterNot { user && it.key == "greenhouse_id" }.forEach { FarmFact(it.label, adminDisplay(it, form, state)) }
                if (user && password.isNotEmpty()) FarmFact(if (form.version == null) "Password awal" else "Password baru", "Sudah diisi")
            }
        }
        return
    }
    // The form's own rules (format, order of dates, units) run before the summary, so a known problem never reaches
    // the server's refusal (QA 28 Nov 2026: email, end date and target unit only failed after Simpan).
    var problem by remember(form.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(form.values) { problem = null }
    FarmFormFrame(errors = listOfNotNull(problem).associateBy { "form" },
        header = { FarmFormHeader("$action ${form.resource.label}", state.saved, "admin-saved", "admin-close", vm::requestCancel, !state.busy,
            showDraftStatus = false, closeLabel = "Batal", step = if (state.pending) null else 1) },
        bar = { FarmFormBar("admin-cancel", vm::requestCancel, "admin-submit", if (state.pending) "Kirim ulang" else "Simpan",
            { if (state.pending) vm.submit(password, onAccepted) else {
                problem = runCatching { form.payload(password) }.exceptionOrNull()?.let { it.message ?: "Periksa isian." }
                if (problem == null) summary = true
            } },
            enabled = !state.busy, busy = state.busy, mainEnabled = !state.busy && !state.rejected && passwordMatches, saveLabel = "Batal") }
    ) {
        state.message?.let {Notice(it)}
        if(state.busy) { Text("Memuat…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(state.pending) Notice("Belum terkirim. Kirim ulang dengan isi yang sama, atau Batal untuk membatalkan.")
        val dataFields = form.fields.filter { !(user && (it.key=="role" || it.key=="greenhouse_id")) }
        if(user) {
            form.fields.filter { it.key=="role" }.forEach { AdminFieldInput(it, state, vm) }
            // Accounts are not tied to a greenhouse any more; greenhouse_id is only the API's record placement, filled for the user.
            FarmFormSection("Data akun") {
                dataFields.forEach { AdminFieldInput(it, state, vm) }
                AdminPasswordFields(state, password, { password = it }, passwordConfirmation, { passwordConfirmation = it }, required = false)
            }
        } else FarmFormSection("Isian ${form.resource.label.lowercase()}") { dataFields.forEach { AdminFieldInput(it, state, vm) } }
        AdminConflict(state, vm)
        if(confirmPassword && !passwordMatches) Text("Isi password minimal 12 karakter dan ulangi dengan isian yang sama.")
    }
}

/** Copy the master lists of another greenhouse: pick the source, then confirm what will be added. */
@Composable internal fun CopyMastersFlow(state: AdminState, open: Boolean, onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    val greenhouses = state.options["greenhouses"].orEmpty()
    val sources = greenhouses.filter { it.optString("id") != state.greenhouse }
    if (open) FarmPicker(
        title = "Salin dari greenhouse",
        options = sources.map { FarmOption(it.getString("id"), it.optString("name")) },
        selected = null,
        optionTag = { "admin-copy-source-$it" },
        onDismiss = onDismiss,
        onSelect = { onDismiss(); source = it },
        sheet = false)
    source?.let { id ->
        val from = greenhouses.find { it.optString("id") == id }?.optString("name").orEmpty()
        val to = greenhouses.find { it.optString("id") == state.greenhouse }?.optString("name").orEmpty()
        AlertDialog(onDismissRequest = { source = null }, containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Salin daftar ke $to?") },
            text = { Text("Varietas, bahan, satuan, kualitas panen dan kategori biaya dari $from ditambahkan ke $to. Nama yang sudah ada di $to dilewati, tidak ada yang diubah atau dihapus.") },
            confirmButton = { Button(onClick = { source = null; onCopy(id) }, shape = FarmShape.Control,
                modifier = Modifier.testTag("admin-copy-confirm")) { Text("Salin") } },
            dismissButton = { TextButton(onClick = { source = null }) { Text("Batal") } })
    }
}
