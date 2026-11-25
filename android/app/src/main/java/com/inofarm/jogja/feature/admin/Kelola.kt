package com.inofarm.jogja.feature.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.FarmBusyReveal
import com.inofarm.jogja.core.design.FarmColors
import com.inofarm.jogja.core.design.FarmControlIcon
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmHeroCard
import com.inofarm.jogja.core.design.FarmIcon
import com.inofarm.jogja.core.design.FarmIconTile
import com.inofarm.jogja.core.design.FarmRowDivider
import com.inofarm.jogja.core.design.FarmSegmentedTabs
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.FarmStatusChip
import com.inofarm.jogja.core.design.FarmTone
import com.inofarm.jogja.core.design.LocalFarmListBottomInset
import com.inofarm.jogja.core.design.LocalFarmTheme
import com.inofarm.jogja.core.design.ThemeMode
import com.inofarm.jogja.core.design.farmRowFocus
import com.inofarm.jogja.core.design.farmToneColors
import com.inofarm.jogja.core.design.initialsOf
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.FarmEntrySteps
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.LocalCanopyForm
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

private typealias Edit = (JSONObject?, Set<String>?, Map<String, String>) -> Unit

// ---------------------------------------------------------------------------------------------------------------------
// Kelola home (the tab)
// ---------------------------------------------------------------------------------------------------------------------

/**
 * The Kelola tab: greenhouses first (each opens its settings page), then the people, then the manager's own account with
 * the light/dark choice and Keluar. [refresh] changes whenever the data may have changed (a sync, the host closing).
 */
@Composable
internal fun KelolaHome(
    session: FinanceSession, repository: AdminRepository, refresh: Any?, enabled: Boolean,
    onOpenGreenhouse: (String) -> Unit, onAddGreenhouse: () -> Unit, onStartPlanting: (String) -> Unit,
    onPeople: () -> Unit, onLogout: () -> Unit,
) {
    var overview by remember(session.token) { mutableStateOf<KelolaOverview?>(null) }
    var error by remember(session.token) { mutableStateOf<String?>(null) }
    var loading by remember(session.token) { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(session.token, refresh, retry) {
        loading = true
        try {
            overview = repository.overview(session, repository.vault.generation); error = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = userMessage(failure) }
        finally { loading = false }
    }
    var showInactive by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize().testTag("manage-list"), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium),
        contentPadding = PaddingValues(top = FarmSpace.Medium, bottom = FarmSpace.Large + LocalFarmListBottomInset.current)) {
        val data = overview
        item("gh-head") {
            Row(Modifier.fillMaxWidth().padding(top = FarmSpace.Small), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Greenhouse", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    data?.let { d ->
                        val active = d.greenhouses.count { it.active }
                        val off = d.greenhouses.size - active
                        Text(if (off > 0) "$active aktif · $off nonaktif" else "$active aktif", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                OutlinedButton(onClick = onAddGreenhouse, enabled = enabled, shape = FarmShape.Control,
                    contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manage-add-greenhouse")) {
                    FarmControlIcon(FarmIcon.Add, null); Spacer(Modifier.width(6.dp)); Text("Tambah")
                }
            }
        }
        error?.let { message -> item("error") {
            Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Notice(if (data == null) message else "$message Daftar di bawah dari pembacaan terakhir.", error = data == null)
                TextButton(onClick = { retry++ }, enabled = !loading, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manage-retry")) { Text("Coba lagi") }
            }
        } }
        if (data == null && loading) item("loading") { FarmBusyReveal { Text("Memuat greenhouse dan akun…"); LinearProgressIndicator(Modifier.fillMaxWidth()) } }
        if (data != null) {
            val active = data.greenhouses.filter { it.active }
            val inactive = data.greenhouses.filterNot { it.active }
            item("gh-list") {
                KelolaCard {
                    if (active.isEmpty()) Text("Belum ada greenhouse aktif. Tambah greenhouse untuk mulai mencatat.",
                        Modifier.padding(FarmSpace.Large), style = MaterialTheme.typography.bodyMedium)
                    active.forEachIndexed { index, gh ->
                        if (index > 0) FarmRowDivider(FarmSpace.Large)
                        KelolaGreenhouseRow(gh, enabled, { onOpenGreenhouse(gh.id) }, { onStartPlanting(gh.id) })
                    }
                    if (inactive.isNotEmpty()) {
                        if (active.isNotEmpty()) FarmRowDivider(0.dp)
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).farmRowFocus().clickable(role = Role.Button) { showInactive = !showInactive }
                            .semantics { stateDescription = if (showInactive) "Terbuka" else "Tertutup" }
                            .padding(horizontal = FarmSpace.Large).testTag("manage-inactive-toggle"), verticalAlignment = Alignment.CenterVertically) {
                            Text("${inactive.size} greenhouse nonaktif", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                            Box(Modifier.graphicsLayer { rotationZ = if (showInactive) 180f else 0f }) { FarmControlIcon(FarmIcon.ChevronDown, null) }
                        }
                        if (showInactive) inactive.forEach { gh ->
                            FarmRowDivider(FarmSpace.Large)
                            KelolaGreenhouseRow(gh, enabled, { onOpenGreenhouse(gh.id) }, {})
                        }
                    }
                }
            }
        }
        item("people-head") { KelolaSectionTitle("Orang") }
        item("people") {
            KelolaCard {
                KelolaRow("Pekerja dan akun", data?.let { "${it.managers} pengelola · ${it.workers} pekerja" + if (it.inactiveAccounts > 0) " · ${it.inactiveAccounts} nonaktif" else "" },
                    Modifier.testTag("manage-users"), enabled = enabled, icon = FarmIcon.Account, onClick = onPeople)
            }
        }
        item("me-head") { KelolaSectionTitle("Akun saya") }
        item("me") {
            KelolaCard {
                Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                    Column {
                        Text(session.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Pengelola · semua greenhouse dan akun", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    val theme = LocalFarmTheme.current
                    Text("Tampilan", Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    FarmSegmentedTabs(listOf("light" to "Terang", "dark" to "Gelap"), if (theme.dark) "dark" else "light",
                        { theme.select(if (it == "dark") ThemeMode.DARK else ThemeMode.LIGHT) }, tagPrefix = "theme-", equalWidth = true)
                    OutlinedButton(onClick = onLogout, enabled = enabled, shape = FarmShape.Control,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                        modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("manage-logout")) {
                        FarmControlIcon(FarmIcon.Logout, null); Spacer(Modifier.width(FarmSpace.Small)); Text("Keluar")
                    }
                    Text("Draf yang belum terkirim tetap tersimpan di perangkat.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun KelolaGreenhouseRow(gh: KelolaGreenhouse, enabled: Boolean, onOpen: () -> Unit, onStart: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val amber = farmToneColors(FarmTone.Amber).second
    val notPlanted = gh.active && gh.periodState == "no_active_period"
    val (detail, warn) = when {
        !gh.active -> "Nonaktif · riwayat tetap ada" to false
        gh.periodState == "ready" -> listOfNotNull(gh.variety ?: gh.groupName, "${gh.workers} pekerja").joinToString(" · ") to false
        gh.periodState == "needs_review" -> "Tanam saat ini perlu ditetapkan" to true
        notPlanted -> "Belum mulai semai" to true
        else -> "${gh.workers} pekerja" to false
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).farmRowFocus().clickable(enabled = enabled, role = Role.Button, onClick = onOpen)
        .padding(horizontal = FarmSpace.Large, vertical = FarmSpace.Small).testTag("manage-gh-${gh.id}"),
        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
        FarmIconTile(FarmIcon.Greenhouse, size = 40.dp, tone = if (warn) FarmTone.Amber else FarmTone.Neutral, shape = FarmShape.Control)
        Column(Modifier.weight(1f)) {
            Text(gh.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (warn) amber else colors.onSurfaceVariant, maxLines = 2)
        }
        when {
            notPlanted -> FilledTonalButton(onClick = onStart, enabled = enabled, shape = FarmShape.Control,
                contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 40.dp).testTag("manage-start-${gh.id}")) { Text("Mulai semai") }
            gh.hasTargets == false -> FarmStatusChip("Target belum ada", tone = FarmTone.Amber)
            else -> FarmControlIcon(FarmIcon.ChevronRight, null)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------------------------------

@Composable private fun KelolaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = FarmShape.Card, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(content = content)
    }
}

@Composable private fun KelolaSectionTitle(title: String) {
    Text(title, Modifier.padding(top = FarmSpace.Small).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

/** A small heading above a group of rows on a settings page. */
@Composable private fun KelolaGroupTitle(title: String) {
    Text(title, Modifier.padding(start = FarmSpace.Tiny, top = FarmSpace.Small).semantics { heading() },
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One tappable row: a label, an optional value or status chip on the right, and a chevron. */
@Composable private fun KelolaRow(
    label: String, value: String?, modifier: Modifier = Modifier, enabled: Boolean = true, chip: String? = null,
    danger: Boolean = false, icon: FarmIcon? = null, chevron: Boolean = true, hint: String? = null, onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val text = if (danger) colors.error else colors.onSurface
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).farmRowFocus().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = FarmSpace.Large, vertical = FarmSpace.Small),
        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { FarmIconTile(it, size = 40.dp, shape = FarmShape.Control) }
        if (icon != null) Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = text)
            value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
        } else {
            // A hint explains what the action does to the account, so it sits under the label instead of in the value slot.
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = text)
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
            value?.let { Text(it, Modifier.widthIn(max = 160.dp), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        chip?.let { FarmStatusChip(it, tone = FarmTone.Amber) }
        if (chevron && enabled) CompositionLocalProvider(LocalContentColor provides if (danger) colors.error else colors.outline) {
            FarmControlIcon(FarmIcon.ChevronRight, null)
        }
    }
}

/** Title bar of a page inside the Kelola host: back, title with a second line, and an optional status on the right. */
@Composable private fun KelolaTopBar(title: String, subtitle: String?, onBack: () -> Unit, backLabel: String,
                                     backIcon: FarmIcon = FarmIcon.ChevronRight, trailing: (@Composable () -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(start = FarmSpace.Tiny, end = FarmSpace.Page, top = FarmSpace.Small, bottom = FarmSpace.Small),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
            IconButton(onClick = onBack, modifier = Modifier.size(FarmSpace.Touch).testTag("kelola-back").semantics { contentDescription = backLabel }) {
                Box(if (backIcon == FarmIcon.ChevronRight) Modifier.graphicsLayer { rotationZ = 180f } else Modifier) { FarmControlIcon(backIcon, null) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing?.invoke()
        }
    }
}

/** A sticky bar at the bottom of a page with its one main action. */
@Composable private fun KelolaBottomBar(content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page, vertical = FarmSpace.Medium),
                horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), content = content)
        }
    }
}

@Composable private fun KelolaMainButton(label: String, tag: String, enabled: Boolean, modifier: Modifier, icon: FarmIcon? = null, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shape = FarmShape.Control, modifier = modifier.heightIn(min = 52.dp).testTag(tag)) {
        icon?.let { FarmControlIcon(it, null); Spacer(Modifier.width(FarmSpace.Small)) }
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun ActiveChip(active: Boolean) =
    FarmStatusChip(if (active) "Aktif" else "Nonaktif", tone = if (active) FarmTone.Good else FarmTone.Amber)

/** The page list with the shared padding; [footer] stays below it. */
@Composable private fun ColumnScope.KelolaPageList(tag: String, content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small),
        contentPadding = PaddingValues(horizontal = FarmSpace.Page, vertical = FarmSpace.Large), content = content)
}

@Composable private fun Loading(state: AdminState, label: String) {
    if (state.busy) FarmBusyReveal { Text(label); LinearProgressIndicator(Modifier.fillMaxWidth()) }
}

// ---------------------------------------------------------------------------------------------------------------------
// Greenhouse settings
// ---------------------------------------------------------------------------------------------------------------------

@Composable
internal fun ColumnScope.GreenhouseSettingsPage(
    id: String, state: AdminState, vm: AdminViewModel, back: () -> Unit, push: (String) -> Unit, edit: Edit,
    resume: @Composable () -> Unit, onReviewPeriod: ((String) -> Unit)?, onStartPlanting: ((String) -> Unit)?, onCopy: (String) -> Unit,
) {
    val row = state.rows.find { it.optString("id") == id }.takeIf { state.settingsFor == id }
    val settings = state.settings.takeIf { state.settingsFor == id }
    val active = row?.optBoolean("active", true) ?: true
    var copyOpen by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.busy
    KelolaTopBar(row?.optString("name") ?: "Greenhouse", "Pengaturan greenhouse", back, "Kembali",
        trailing = row?.let { { ActiveChip(active) } })
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    KelolaPageList("gh-settings") {
        item("resume") { resume() }
        state.message?.let { item("message") { Notice(it) } }
        if (settings == null) {
            item("loading") {
                Loading(state, "Memuat pengaturan…")
                if (!state.busy && state.message != null) TextButton(onClick = { vm.openGreenhouse(id) }, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Coba lagi") }
            }
            return@KelolaPageList
        }
        val period = settings.period
        item("hero") { PeriodHero(id, period, active, enabled, onReviewPeriod, onStartPlanting) }
        item("grow-title") { KelolaGroupTitle("Budidaya") }
        item("grow") {
            KelolaCard {
                KelolaRow("Musim dan kelompok tanam", "${settings.openSeasons} musim berjalan", Modifier.testTag("gh-seasons"), enabled) { push(listRoute(AdminResource.SEASON, id)) }
                FarmRowDivider(FarmSpace.Large)
                KelolaRow("Varietas", "${settings.varieties}", Modifier.testTag("gh-varieties"), enabled) { push(listRoute(AdminResource.VARIETY, id)) }
                FarmRowDivider(FarmSpace.Large)
                KelolaRow("Jadwal kegiatan", "${settings.schedules}", Modifier.testTag("gh-schedules"), enabled) { push(listRoute(AdminResource.SCHEDULE, id)) }
                FarmRowDivider(FarmSpace.Large)
                KelolaRow("Target EC, PPM dan pH", if (settings.targets > 0) "${settings.targets}" else null, Modifier.testTag("gh-targets"), enabled,
                    chip = if (settings.targets == 0) "Belum diatur" else null) { push(listRoute(AdminResource.TARGET, id)) }
            }
        }
        item("supply-title") { KelolaGroupTitle("Bahan, panen dan biaya") }
        item("supply") {
            KelolaCard {
                KelolaRow("Bahan", "${settings.materials} aktif", Modifier.testTag("gh-materials"), enabled) { push(listRoute(AdminResource.MATERIAL, id)) }
                FarmRowDivider(FarmSpace.Large)
                KelolaRow("Satuan", shortNames(settings.units), Modifier.testTag("gh-units"), enabled) { push(listRoute(AdminResource.UNIT, id)) }
                FarmRowDivider(FarmSpace.Large)
                KelolaRow("Kualitas panen", shortNames(settings.grades), Modifier.testTag("gh-grades"), enabled) { push(listRoute(AdminResource.GRADE, id)) }
                FarmRowDivider(FarmSpace.Large)
                KelolaRow("Kategori biaya", "${settings.categories}", Modifier.testTag("gh-categories"), enabled) { push(listRoute(AdminResource.CATEGORY, id)) }
            }
        }
        if (state.options["greenhouses"].orEmpty().any { it.optString("id") != id }) item("copy") {
            OutlinedButton(onClick = { copyOpen = true }, enabled = enabled, shape = FarmShape.Control,
                modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("admin-copy-masters")) {
                FarmControlIcon(FarmIcon.CheckList, null); Spacer(Modifier.width(FarmSpace.Small)); Text("Salin daftar dari greenhouse lain")
            }
        }
        item("people-title") { KelolaGroupTitle("Orang dan status") }
        item("people") {
            KelolaCard {
                KelolaRow("Pekerja di greenhouse ini", "${settings.workers}", Modifier.testTag("gh-workers"), enabled) { push(listRoute(AdminResource.ASSIGNMENT, id)) }
                FarmRowDivider(FarmSpace.Large)
                KelolaRow("Ubah nama", null, Modifier.testTag("gh-rename"), enabled && row != null) { edit(row, setOf("name", "reason"), emptyMap()) }
                FarmRowDivider(FarmSpace.Large)
                if (active) KelolaRow("Nonaktifkan greenhouse", null, Modifier.testTag("admin-toggle-active"), enabled, danger = true) { push("off:$id") }
                else KelolaRow("Aktifkan lagi", "Bisa dipakai mencatat lagi", Modifier.testTag("admin-toggle-active"), enabled && row != null) {
                    edit(row, setOf("active", "reason"), mapOf("active" to "true"))
                }
            }
        }
    }
    CopyMastersFlow(state, copyOpen, { copyOpen = false }, onCopy)
}

@Composable
private fun PeriodHero(id: String, period: JSONObject?, active: Boolean, enabled: Boolean,
                       onReviewPeriod: ((String) -> Unit)?, onStartPlanting: ((String) -> Unit)?) {
    fun text(key: String) = period?.optString(key)?.takeIf { !period.isNull(key) && it.isNotBlank() }
    val stateName = period?.optString("state")
    FarmHeroCard(motif = false) {
        Text("Tanam saat ini", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
                when {
                    !active || stateName == "inactive" -> Text("Greenhouse nonaktif", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    stateName == "ready" -> {
                        Text(text("variety") ?: text("group_name") ?: "Kelompok tanam", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(text("group_name"), text("season_name"), text("sowing_date")?.let { "semai $it" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    stateName == "needs_review" -> Text("Perlu ditetapkan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    else -> Text("Belum mulai semai", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            if (active && stateName in setOf("ready", "needs_review") && onReviewPeriod != null)
                OutlinedButton(onClick = { onReviewPeriod(id) }, enabled = enabled, shape = FarmShape.Control,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("admin-setup-period")) { Text(if (stateName == "ready") "Ganti" else "Tetapkan") }
            else if (active && stateName == "no_active_period" && onStartPlanting != null)
                Button(onClick = { onStartPlanting(id) }, enabled = enabled, shape = FarmShape.Control,
                    modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("gh-start-planting")) { Text("Mulai semai") }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// One master list
// ---------------------------------------------------------------------------------------------------------------------

@Composable
internal fun ColumnScope.MasterListPage(
    resource: AdminResource, greenhouse: String?, greenhouseName: String, state: AdminState, vm: AdminViewModel, back: () -> Unit,
    resume: @Composable () -> Unit, onSwitch: (AdminResource) -> Unit, onAdd: () -> Unit, onOpen: (JSONObject) -> Unit, onCopy: (String) -> Unit,
) {
    val loaded = state.resource == resource && state.greenhouse == greenhouse
    val rows = if (loaded) state.rows else emptyList()
    var filter by rememberSaveable(resource, greenhouse) { mutableStateOf("on") }
    var search by rememberSaveable(resource, greenhouse) { mutableStateOf("") }
    var copyOpen by rememberSaveable { mutableStateOf(false) }
    val title = masterListTitle(resource)
    val (onLabel, offLabel) = masterFilterLabels(resource)
    val on = rows.count { masterRowOn(resource, it) }
    val visible = remember(rows, filter, search) {
        rows.filter { masterRowOn(resource, it) == (filter == "on") }
            .filter { search.isBlank() || masterRowTitle(resource, it).contains(search.trim(), true) || (masterRowDetail(resource, it)?.contains(search.trim(), true) == true) }
    }
    KelolaTopBar(if (resource == AdminResource.GROUP) "Musim dan kelompok tanam" else if (resource == AdminResource.SEASON) "Musim dan kelompok tanam" else title,
        listOf(greenhouseName, masterListPurpose(resource)).filter { it.isNotBlank() }.joinToString(" · "), back, "Kembali")
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page).padding(bottom = FarmSpace.Medium),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            if (resource == AdminResource.SEASON || resource == AdminResource.GROUP)
                FarmSegmentedTabs(listOf(AdminResource.SEASON.name to "Musim", AdminResource.GROUP.name to "Kelompok tanam"), resource.name,
                    { onSwitch(AdminResource.valueOf(it)) }, tagPrefix = "admin-list-", equalWidth = true)
            if (rows.size > 6) FinanceField("Cari ${title.lowercase()}", search, { search = it }, "admin-search",
                leadingIcon = null)
            if (rows.isNotEmpty()) FarmSegmentedTabs(listOf("on" to "$onLabel · $on", "off" to "$offLabel · ${rows.size - on}"), filter,
                { filter = it }, tagPrefix = "admin-filter-", equalWidth = true)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    KelolaPageList("admin-list") {
        item("resume") { resume() }
        state.message?.let { item("message") { Notice(it) } }
        item("loading") { Loading(state, "Memuat…") }
        if (resource != AdminResource.GREENHOUSE && greenhouse == null) item("no-gh") { Text("Pilih greenhouse untuk melihat dan menambah data.") }
        if (loaded && state.listChecked && rows.isEmpty()) item("empty") {
            Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Text("Belum ada ${title.lowercase()}.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (resource in MasterResources) "Tambah satu per satu dengan tombol di bawah, atau salin daftar dari greenhouse lain."
                    else "Tambah dengan tombol di bawah.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (resource in MasterResources && state.options["greenhouses"].orEmpty().any { it.optString("id") != greenhouse })
                    OutlinedButton(onClick = { copyOpen = true }, enabled = !state.busy, shape = FarmShape.Control,
                        modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("admin-copy-masters")) { Text("Salin dari greenhouse lain") }
            }
        }
        if (rows.isNotEmpty() && visible.isEmpty()) item("no-match") {
            Text(if (search.isNotBlank()) "Tidak ada yang cocok dengan \"${search.trim()}\"." else "Tidak ada ${title.lowercase()} yang ${(if (filter == "on") onLabel else offLabel).lowercase()}.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (visible.isNotEmpty()) item("rows") {
            KelolaCard {
                visible.forEachIndexed { index, row ->
                    if (index > 0) FarmRowDivider(FarmSpace.Large)
                    KelolaRow(masterRowTitle(resource, row), masterRowDetail(resource, row), Modifier.testTag("admin-row-${row.optString("id")}"), !state.busy) { onOpen(row) }
                }
            }
        }
        if (state.cursor != null && loaded) item("more") {
            TextButton(onClick = { vm.reload(true) }, enabled = !state.busy, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Muat berikutnya") }
        }
    }
    KelolaBottomBar {
        KelolaMainButton(masterAddLabel(resource), "admin-new", !state.busy && (resource == AdminResource.GREENHOUSE || greenhouse != null),
            Modifier.fillMaxWidth(), FarmIcon.Add, onAdd)
    }
    CopyMastersFlow(state, copyOpen, { copyOpen = false }, onCopy)
}

// ---------------------------------------------------------------------------------------------------------------------
// People and one account
// ---------------------------------------------------------------------------------------------------------------------

@Composable
internal fun ColumnScope.PeoplePage(session: FinanceSession, state: AdminState, back: () -> Unit, push: (String) -> Unit, edit: Edit, resume: @Composable () -> Unit) {
    val rows = if (state.resource == AdminResource.USER) state.rows else emptyList()
    var filter by rememberSaveable { mutableStateOf("all") }
    var search by rememberSaveable { mutableStateOf("") }
    val workers = rows.count { it.optString("role") == "worker" }
    val visible = remember(rows, filter, search) {
        rows.filter { filter == "all" || it.optString("role") == filter }
            .filter { search.isBlank() || it.optString("name").contains(search.trim(), true) || it.optString("email").contains(search.trim(), true) }
            .sortedWith(compareBy({ !it.optBoolean("active", true) }, { it.optString("name").lowercase() }))
    }
    KelolaTopBar("Pekerja dan akun", "Pekerja bisa mencatat di semua greenhouse aktif", back, "Kembali")
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page).padding(bottom = FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            FarmSegmentedTabs(listOf("all" to "Semua · ${rows.size}", "worker" to "Pekerja · $workers", "manager" to "Pengelola · ${rows.size - workers}"),
                filter, { filter = it }, tagPrefix = "people-filter-", equalWidth = true)
            if (rows.size > 8) FinanceField("Cari nama atau email", search, { search = it }, "admin-search")
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    KelolaPageList("people-list") {
        item("resume") { resume() }
        state.message?.let { item("message") { Notice(it) } }
        item("loading") { Loading(state, "Memuat akun…") }
        if (state.resource == AdminResource.USER && state.greenhouse == null && state.listChecked) item("no-gh") {
            Text("Tambah greenhouse dulu sebelum membuat akun.")
        }
        visible.forEach { row ->
            item(row.optString("id")) { PersonCard(row, row.optString("id") == session.scope.accountId) { push("account:${row.optString("id")}") } }
        }
        item("note") {
            Text("Tidak ada pendaftaran sendiri. Akun baru dibuat di sini, lalu passwordnya diberikan langsung ke orangnya.",
                Modifier.padding(top = FarmSpace.Small, start = FarmSpace.Tiny, end = FarmSpace.Tiny),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    KelolaBottomBar {
        KelolaMainButton("Tambah akun", "admin-new", !state.busy && state.greenhouse != null, Modifier.fillMaxWidth(), FarmIcon.Add) { edit(null, null, emptyMap()) }
    }
}

@Composable private fun Avatar(name: String, manager: Boolean, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(if (manager) FarmColors.Brand else FarmColors.Sage, CircleShape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Text(initialsOf(name), style = if (size > 48.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold, color = if (manager) FarmColors.Surface else FarmColors.Ink)
    }
}

private fun roleLabel(row: JSONObject) = if (row.optString("role") == "manager") "Pengelola" else "Pekerja"

@Composable private fun PersonCard(row: JSONObject, self: Boolean, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onOpen, Modifier.fillMaxWidth().testTag("person-${row.optString("id")}"), shape = FarmShape.Card, color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)) {
        Row(Modifier.padding(horizontal = FarmSpace.Large, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(row.optString("name"), row.optString("role") == "manager", 44.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.optString("name"), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (self) Text(" (Anda)", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                }
                Text("${roleLabel(row)} · ${row.optString("email")}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ActiveChip(row.optBoolean("active", true))
        }
    }
}

@Composable
internal fun ColumnScope.AccountPage(session: FinanceSession, id: String, state: AdminState, back: () -> Unit, edit: Edit) {
    val row = state.rows.find { it.optString("id") == id }.takeIf { state.resource == AdminResource.USER }
    val self = id == session.scope.accountId
    val colors = MaterialTheme.colorScheme
    KelolaTopBar("Akun", null, back, "Kembali ke pekerja dan akun")
    HorizontalDivider(color = colors.outlineVariant)
    KelolaPageList("account-page") {
        state.message?.let { item("message") { Notice(it) } }
        if (row == null) {
            item("loading") {
                Loading(state, "Memuat akun…")
                if (!state.busy) Text("Akun ini tidak ada di daftar yang dimuat.")
            }
            return@KelolaPageList
        }
        val active = row.optBoolean("active", true)
        val manager = row.optString("role") == "manager"
        item("hero") {
            FarmHeroCard(motif = false) {
                Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Large), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).background(FarmColors.Sage, CircleShape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                        Text(initialsOf(row.optString("name")), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = FarmColors.Ink)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
                        Text(row.optString("name") + if (self) " (Anda)" else "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${row.optString("email")} · ${roleLabel(row)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FarmStatusChip(if (active) "Aktif" else "Nonaktif", tone = if (active) FarmTone.Forest else FarmTone.Amber)
                    }
                }
            }
        }
        item("work-title") { KelolaGroupTitle("Kerja") }
        item("work") {
            KelolaCard {
                Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                    FarmFact("Greenhouse", if (manager) "Semua greenhouse, termasuk yang nonaktif" else "Semua greenhouse yang aktif")
                    FarmFact("Yang bisa dilakukan", if (manager) "Mencatat, mengecek catatan pekerja, keuangan dan Kelola" else "Mencatat kegiatan, pengukuran dan panen")
                }
            }
        }
        item("actions-title") { KelolaGroupTitle("Tindakan") }
        item("actions") {
            val enabled = !state.busy
            KelolaCard {
                KelolaRow("Ubah nama dan email", null, Modifier.testTag("account-edit"), enabled) { edit(row, setOf("name", "email", "reason"), emptyMap()) }
                if (!self) {
                    FarmRowDivider(FarmSpace.Large)
                    KelolaRow(if (manager) "Jadikan pekerja" else "Jadikan pengelola", null, Modifier.testTag("account-role"), enabled, hint = "Akun perlu masuk ulang") {
                        edit(row, setOf("role", "reason"), mapOf("role" to if (manager) "worker" else "manager"))
                    }
                }
                FarmRowDivider(FarmSpace.Large)
                KelolaRow("Atur ulang password", null, Modifier.testTag("account-password"), enabled, hint = "Akun keluar dari semua perangkat") { edit(row, setOf("password"), emptyMap()) }
                if (!self) {
                    FarmRowDivider(FarmSpace.Large)
                    if (active) KelolaRow("Nonaktifkan akun", null, Modifier.testTag("account-deactivate"), enabled, danger = true) {
                        edit(row, setOf("active", "reason"), mapOf("active" to "false"))
                    } else KelolaRow("Aktifkan lagi", null, Modifier.testTag("account-activate"), enabled, hint = "Akun bisa masuk lagi") {
                        edit(row, setOf("active", "reason"), mapOf("active" to "true"))
                    }
                }
            }
        }
        item("note") {
            Text(if (self) "Anda tidak bisa menonaktifkan atau mengubah peran akun sendiri." else "Catatan yang sudah dibuat akun ini tetap ada setelah dinonaktifkan.",
                Modifier.padding(horizontal = FarmSpace.Tiny), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Turning a greenhouse off
// ---------------------------------------------------------------------------------------------------------------------

@Composable
internal fun ColumnScope.DeactivatePage(id: String, state: AdminState, vm: AdminViewModel, back: () -> Unit, unsentFor: (String) -> Int, onAccepted: () -> Unit) {
    val row = state.rows.find { it.optString("id") == id }.takeIf { state.settingsFor == id }
    val name = row?.optString("name") ?: "greenhouse"
    val unsent = unsentFor(id)
    val period = state.settings?.period.takeIf { state.settingsFor == id }
    // The greenhouse list does not carry running_group_id, so the manager-home period decides. The server refuses to
    // deactivate while any group is designated; needs_review may hide a stale designation, so it blocks as well.
    val periodState = period?.optString("state")
    val running = periodState == "ready" || periodState == "needs_review"
    var sent by rememberSaveable { mutableStateOf(false) }
    // The save succeeded once the reloaded row says inactive; then the settings page shows the new status.
    LaunchedEffect(row?.optBoolean("active", true), sent) { if (sent && row?.optBoolean("active", true) == false) back() }
    LaunchedEffect(state.message) { if (state.message != null && row?.optBoolean("active", true) != false) sent = false }
    val ready = row != null && period != null && row.optBoolean("active", true) && !running && unsent == 0
    KelolaTopBar("Nonaktifkan $name?", if (ready) "Syarat sudah beres" else "Selesaikan dulu yang ditandai", back, "Batal, kembali ke pengaturan", FarmIcon.Close)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    KelolaPageList("deactivate-page") {
        state.message?.let { item("message") { Notice(it) } }
        if (row == null || period == null) { item("loading") { Loading(state, "Memeriksa greenhouse…") }; return@KelolaPageList }
        item("period") {
            fun text(key: String) = period?.optString(key)?.takeIf { !period.isNull(key) && it.isNotBlank() }
            Condition(!running, when (periodState) { "ready" -> "Masih ada tanam saat ini"; "needs_review" -> "Tanam saat ini perlu ditinjau"; else -> "Tidak ada tanam saat ini" },
                if (running) listOfNotNull(text("group_name") ?: text("variety"), text("season_name")).joinToString(" · ")
                    .let { if (it.isBlank()) "" else "$it. " } + "Kosongkan dulu. Catatan baru tidak bisa masuk ke greenhouse nonaktif."
                else "Catatan baru tidak lagi ditujukan ke greenhouse ini.") {
                if (running) Button(onClick = { vm.clearPeriod(onAccepted) }, enabled = !state.busy, shape = FarmShape.Control,
                    modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("deactivate-clear-period")) { Text("Kosongkan tanam saat ini") }
            }
        }
        item("unsent") {
            Condition(unsent == 0, if (unsent == 0) "Semua catatan sudah terkirim" else "$unsent catatan belum terkirim",
                if (unsent == 0) "Tidak ada catatan $name yang tertahan di perangkat ini."
                else "Kirim dulu dari Beranda: ketuk \"belum terkirim\" di atas, lalu Kirim sekarang. Setelah itu buka halaman ini lagi.") {}
        }
        item("kept") {
            Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Yang tetap tersimpan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Riwayat kegiatan, panen, penjualan dan laporan $name. Greenhouse bisa diaktifkan lagi dari halaman pengaturannya di Kelola.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    KelolaBottomBar {
        OutlinedButton(onClick = back, enabled = !state.busy, shape = FarmShape.Control,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("deactivate-cancel")) { Text("Batal") }
        Button(onClick = { if (row != null) { sent = true; vm.setActive(row, false, onAccepted) } }, enabled = ready && !state.busy,
            shape = FarmShape.Control, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("admin-toggle-confirm")) {
            Text(if (state.busy && sent) "Menyimpan…" else "Nonaktifkan", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun Condition(done: Boolean, title: String, detail: String, action: @Composable ColumnScope.() -> Unit) {
    KelolaCard {
        Row(Modifier.padding(FarmSpace.Large), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FarmIconTile(if (done) FarmIcon.Check else FarmIcon.AlertTriangle, size = 32.dp, tone = if (done) FarmTone.Good else FarmTone.Amber,
                shape = CircleShape, description = if (done) "Beres" else "Perlu diselesaikan")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                action()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// The sheet that edits one row
// ---------------------------------------------------------------------------------------------------------------------

private fun switchText(resource: AdminResource): Pair<String, String> = when (resource) {
    AdminResource.GREENHOUSE -> "Greenhouse aktif" to "Greenhouse nonaktif tidak menerima catatan baru. Riwayatnya tetap ada."
    AdminResource.USER -> "Akun aktif" to "Akun nonaktif tidak bisa masuk. Catatan yang sudah dibuat tetap ada."
    AdminResource.ASSIGNMENT -> "Masih bertugas" to "Matikan bila pekerja tidak lagi bekerja di greenhouse ini."
    else -> "Tampil di form" to "Matikan bila tidak dipakai lagi. Riwayat yang memakainya tetap ada."
}

/**
 * Edits one record in a bottom sheet, in the two manager steps: the fields with Batal / Simpan, then the summary with
 * Ubah / Simpan, which sends. [keys] limits the fields shown (null: every field the form has); [summaryFirst] opens a
 * change that was already made (a new role, active off) on its summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminSheetEditor(state: AdminState, vm: AdminViewModel, onAccepted: () -> Unit, keys: Set<String>?, summaryFirst: Boolean, greenhouseName: String) {
    val form = state.form ?: return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden })
    var password by remember(form.id, state.pending) { mutableStateOf("") }
    var passwordConfirmation by remember(form.id, state.pending) { mutableStateOf("") }
    val passwordStep = keys?.contains("password") == true
    var summary by rememberSaveable(form.id) { mutableStateOf(summaryFirst) }
    LaunchedEffect(state.message) { if (state.message != null) summary = false }
    val showSummary = summary && !state.pending && !state.rejected && state.server == null
    val fields = form.fields.filter { it.key != "active" && (it.key != "greenhouse_id" || form.resource == AdminResource.USER && form.version == null) &&
        (keys == null || it.key in keys) && !(form.resource == AdminResource.USER && it.key == "greenhouse_id") }
    val hasSwitch = form.fields.any { it.key == "active" } && (keys == null || "active" in keys)
    val passwordOk = !passwordStep || state.pending || password.length >= 12 && password == passwordConfirmation
    val confirmation = adminConfirmation(form, state, password)
    val noun = form.resource.label.lowercase()
    val action = when { form.version == null -> "Tambah"; form.action == "close" -> "Tutup"; form.action == "reopen" -> "Buka kembali"; else -> "Ubah" }
    val context = listOf(greenhouseName.takeIf { form.resource != AdminResource.USER && form.resource != AdminResource.GREENHOUSE }.orEmpty(),
        form.values["name"].orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
    ModalBottomSheet(onDismissRequest = vm::requestCancel, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface,
        shape = FarmShape.Sheet, contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal) }) {
        CompositionLocalProvider(LocalCanopyForm provides true) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FarmSpace.Page).padding(bottom = FarmSpace.Large).testTag("admin-sheet"),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (showSummary) "Ringkasan $noun" else "$action $noun", Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (context.isNotBlank()) Text(context, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = vm::requestCancel, enabled = !state.busy, modifier = Modifier.testTag("admin-close")) { FarmControlIcon(FarmIcon.Close, "Batal") }
            }
            if (!state.pending) FarmEntrySteps(if (showSummary) 2 else 1)
            if (showSummary) {
                Text("Periksa sekali lagi. Simpan langsung mengirim perubahan ke server, jadi perlu koneksi internet.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.message?.let { Notice(it, true) }
                confirmation?.let { Notice(it.second) }
                KelolaCard {
                    Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        fields.filter { it.key != "reason" || form.values["reason"].orEmpty().isNotBlank() }.forEach { FarmFact(it.label, adminDisplay(it, form, state)) }
                        if (hasSwitch) FarmFact(switchText(form.resource).first, if (form.values["active"] == "true") "Ya" else "Tidak")
                        if (password.isNotEmpty()) FarmFact("Password baru", "Sudah diisi")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                    OutlinedButton(onClick = { summary = false }, enabled = !state.busy, shape = FarmShape.Control,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("admin-summary-back")) { Text("Ubah") }
                    Button(onClick = { vm.submit(password, onAccepted) }, enabled = !state.busy, shape = FarmShape.Control,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("admin-save")) {
                        Text(if (state.busy) "Menyimpan…" else confirmation?.third ?: "Simpan", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                state.message?.let { Notice(it) }
                if (state.pending) Notice("Belum terkirim. Kirim ulang dengan isi yang sama, atau Batal untuk membatalkan.")
                fields.forEach { AdminFieldInput(it, state, vm) }
                if (hasSwitch) ActiveSwitch(form.resource, form.values["active"] == "true", !state.busy && !state.pending) { vm.edit("active", it.toString()) }
                if (passwordStep) {
                    Text("Akun ini akan keluar dari semua perangkat dan perlu masuk dengan password baru.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AdminPasswordFields(state, password, { password = it }, passwordConfirmation, { passwordConfirmation = it }, required = true)
                }
                if (form.resource == AdminResource.SEASON && form.version != null && form.action == "update" && !state.pending) {
                    val original = state.rows.find { it.optString("id") == form.id }
                    if (original != null) TextButton(onClick = { vm.discard(); vm.open(original, if (original.optString("status") == "closed") "reopen" else "close") },
                        enabled = !state.busy, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("admin-season-status")) {
                        Text(if (original.optString("status") == "closed") "Buka kembali musim ini" else "Tutup musim ini")
                    }
                }
                AdminConflict(state, vm)
                Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                    OutlinedButton(onClick = vm::requestCancel, enabled = !state.busy, shape = FarmShape.Control,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("admin-cancel")) { Text("Batal") }
                    Button(onClick = { if (state.pending) vm.submit(password, onAccepted) else summary = true },
                        enabled = !state.busy && !state.rejected && passwordOk, shape = FarmShape.Control,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("admin-submit")) {
                        Text(if (state.busy) "Mengirim…" else if (state.pending) "Kirim ulang" else "Simpan", fontWeight = FontWeight.Bold)
                    }
                }
                if (passwordStep && !passwordOk && password.isNotEmpty()) Text("Isi password minimal 12 karakter dan ulangi dengan isian yang sama.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
    }
}

@Composable private fun ActiveSwitch(resource: AdminResource, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val (label, note) = switchText(resource)
    Row(Modifier.fillMaxWidth().clip(FarmShape.Tile).background(MaterialTheme.colorScheme.secondaryContainer)
        .toggleable(on, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(14.dp).testTag("admin-active"),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Switch(on, onCheckedChange = null, enabled = enabled)
    }
}
