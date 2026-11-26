package com.inofarm.jogja.feature.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.feature.*
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.expenses.moneyLabel
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

internal fun saleStock(context: CultivationUiState, credit: Map<String, BigDecimal> = emptyMap()) =
    SaleStock(context.harvests, context.harvestBalances, context.groups, context.varieties, context.grades, credit)

private val BuyerTypeCards = listOf(
    Triple("consumer", "Perorangan", "Pembeli akhir, harga tetap"),
    Triple("retail", "Retail", "Toko atau pedagang, harga ditawar"),
    Triple("partner", "Mitra", "Tengkulak, grade A, B, C"),
)

/** Sold share on the stock bars of the Ringkasan, the Perorangan sales orange of the finance charts. */
private val SoldShare = Color(0xFFE8C48A)

private fun greenhouseName(context: CultivationUiState, id: String) = context.greenhouses.find { it.optString("id") == id }?.optString("name")?.ifBlank { null } ?: "Greenhouse"
private fun seasonName(context: CultivationUiState, id: String) = context.seasons.find { it.optString("id") == id }?.optString("name")?.ifBlank { null }

/**
 * Catat penjualan (25 Nov 2026): buyer type first, then the fruit of the greenhouse's running season as one card per
 * variety and Layak/Cacat with its stock, then payment and the running total. Step 1 of 2; Simpan opens the Ringkasan.
 */
@Composable fun SaleForm(
    state: SaleEntryState, context: CultivationUiState, vm: SaleEntryViewModel,
    buyerSearch: (suspend (String, String) -> List<com.inofarm.jogja.feature.customers.Customer>)? = null,
    consumerPrices: (suspend () -> List<JSONObject>)? = null,
    pastSales: (suspend () -> List<JSONObject>)? = null,
) {
    val draft = state.draft ?: return
    val cancel = rememberEntryCancel(state.busy, vm::hasChanges, vm::cancel)
    val stock = remember(context.harvests, context.harvestBalances, context.groups, context.varieties, context.grades, state.credit) { saleStock(context, state.credit) }
    var prices by remember { mutableStateOf(emptyList<JSONObject>()) }
    var sales by remember { mutableStateOf(emptyList<JSONObject>()) }
    LaunchedEffect(consumerPrices) { if (consumerPrices != null) runCatching { consumerPrices() }.onSuccess { prices = it } }
    LaunchedEffect(pastSales) { if (pastSales != null) runCatching { pastSales() }.onSuccess { sales = it } }
    val openSeason = SaleFormModel.openSeason(context.seasons, draft.greenhouseId)
    LaunchedEffect(draft.greenhouseId, openSeason) { if (openSeason != null) vm.settleSeason(openSeason) }
    // Prices that arrive after a Perorangan line was added fill its empty price; typed prices stay.
    LaunchedEffect(prices, draft.buyerType, draft.items.size) {
        if (SaleFormModel.fillConsumerPrices(draft, stock, prices) != null) vm.edit { SaleFormModel.fillConsumerPrices(it, stock, prices) ?: it }
    }
    var pickGreenhouse by remember { mutableStateOf(false) }
    var pendingType by remember { mutableStateOf<String?>(null) }
    val correcting = draft.baseVersion != null || draft.lockedSeason
    FarmFormFrame(header = {
        FarmFormHeader(if (draft.baseVersion == null) "Catat penjualan" else "Koreksi penjualan", state.saved, "sale-draft-status", "sale-close", cancel, !state.busy,
            showDraftStatus = false, closeLabel = "Batal", step = 1)
    }, bar = { FarmFormBar("sale-cancel", cancel, "sale-review", "Simpan", vm::review, enabled = !state.busy, busy = state.busy, saveLabel = "Batal") }) {
        val season = seasonName(context, draft.seasonId)
        FarmContextStrip(listOfNotNull(greenhouseName(context, draft.greenhouseId), season).joinToString(" · "),
            if (correcting) "Musim penjualan ini tetap" else if (season != null) "Musim berjalan, dipilih otomatis" else "Belum ada musim berjalan", null,
            action = if (correcting) null else ({
                OutlinedButton({ pickGreenhouse = true }, enabled = !state.busy, shape = FarmShape.Control,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    contentPadding = PaddingValues(horizontal = FarmSpace.Medium), modifier = Modifier.heightIn(min = 44.dp).testTag("sale-gh-change")) { Text("Ganti GH") }
            }))
        if (!correcting && openSeason == null) Notice("Greenhouse ini belum punya musim berjalan. Mulai semai dulu atau pilih greenhouse lain.", true)
        state.errors["season"]?.let { FieldError(it) }

        BuyerTypePicker(draft.buyerType) { type ->
            // Leaving Mitra drops grade B and C lines; the manager confirms first instead of losing kg silently.
            if (type != draft.buyerType) { if (SaleFormModel.switchLoss(draft, type) != null) pendingType = type else vm.edit { SaleFormModel.switchType(it, type, stock, prices) } }
        }
        state.errors["buyer_type"]?.let { FieldError(it) }
        com.inofarm.jogja.feature.customers.BuyerSection(draft, state.errors["customer"], buyerSearch) { change -> vm.edit(change) }

        FruitSection(draft, context, stock, prices, sales, state.errors, vm)

        FarmFormSection("Pembayaran") {
            FarmFieldPair({ FinanceDateField("Tanggal lunas", draft.date, { value -> vm.edit { it.copy(date = value) } }, "sale-date", state.errors["paid_at"]) },
                { FinanceTimeField("Jam (WIB)", draft.time, { value -> vm.edit { it.copy(time = value) } }, "sale-time") }, minPairWidth = 320.dp)
            FarmChoiceChips("Cara bayar", draft.paymentMethod.ifBlank { null },
                listOf("cash" to "Tunai", "transfer" to "Transfer", "qris" to "QRIS").map { (id, name) -> JSONObject().put("id", id).put("name", name) }, "sale-payment") { id ->
                vm.edit { it.copy(paymentMethod = if (it.paymentMethod == id) "" else id) }
            }
            FarmCheckbox("Saya memastikan pembayaran ini sudah lunas.", draft.paid, { value -> vm.edit { it.copy(paid = value) } }, Modifier.testTag("sale-paid"))
            state.errors["paid"]?.let { FieldError(it) }
            FinanceField("Keterangan (opsional)", draft.notes, { value -> vm.edit { it.copy(notes = value) } }, "sale-notes", state.errors["notes"], singleLine = false)
            if (draft.baseVersion != null) FinanceField(vm.reasonLabel, draft.reason, { value -> vm.edit { it.copy(reason = value) } }, "sale-reason", state.errors["reason"], singleLine = false)
        }

        TotalCard(SaleFormModel.totals(draft, stock))
        val lineErrors = state.errors.keys.any { it.startsWith("items.") }
        val shown = setOf("season", "buyer_type", "customer", "paid_at", "paid", "notes", "reason")
        val rest = state.errors.filterKeys { it !in shown && !it.startsWith("items.") }.values.distinct()
        if (lineErrors) Notice("Periksa baris buah yang ditandai merah.", true)
        rest.forEach { Notice(it, true) }
        state.message?.let { Notice(it, true) }
    }
    pendingType?.let { type ->
        val loss = SaleFormModel.switchLoss(draft, type)
        if (loss == null) { LaunchedEffect(type) { pendingType = null }; return@let }
        AlertDialog(onDismissRequest = { pendingType = null }, containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Ganti ke ${BuyerTypes.label(type)}?") },
            text = { Text(loss, Modifier.testTag("sale-switch-loss")) },
            confirmButton = { TextButton({ pendingType = null; vm.edit { SaleFormModel.switchType(it, type, stock, prices) } }, Modifier.testTag("sale-switch-confirm")) { Text("Ganti dan hapus") } },
            dismissButton = { TextButton({ pendingType = null }, Modifier.testTag("sale-switch-cancel")) { Text("Tetap Mitra") } })
    }
    if (pickGreenhouse) FarmPicker(
        title = "Pilih greenhouse",
        options = context.greenhouses.filter { it.optBoolean("active", true) }.map { FarmOption(it.getString("id"), it.optString("name")) },
        selected = draft.greenhouseId, optionTag = { "sale-gh-option-$it" }, onDismiss = { pickGreenhouse = false },
        onSelect = { id -> pickGreenhouse = false; vm.changeGreenhouse(id) }, sheet = true)
}

@Composable private fun BuyerTypePicker(selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        Text("Ke siapa?", Modifier.padding(horizontal = FarmSpace.Tiny).semantics { heading() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val cards: @Composable (Modifier) -> Unit = { modifier ->
            BuyerTypeCards.forEach { (id, name, about) ->
                val on = id == selected
                Surface(modifier.testTag("sale-type-$id").selectable(on, role = Role.RadioButton, onClick = { onSelect(id) }),
                    shape = FarmShape.Card, color = if (on) colors.primaryContainer else colors.surface, contentColor = if (on) colors.onPrimaryContainer else colors.onSurface,
                    border = BorderStroke(if (on) 2.dp else 1.dp, if (on) colors.primary else colors.outlineVariant)) {
                    Column(Modifier.heightIn(min = 104.dp).padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        FarmIconTile(when (id) { "retail" -> FarmIcon.Store; "partner" -> FarmIcon.Truck; else -> FarmIcon.Account },
                            tone = if (on) FarmTone.Brand else FarmTone.Good, size = 32.dp, shape = RoundedCornerShape(10.dp))
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(about, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (LocalDensity.current.fontScale > 1.3f) Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) { cards(Modifier.fillMaxWidth()) }
        else Row(Modifier.selectableGroup().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) { cards(Modifier.weight(1f).fillMaxHeight()) }
    }
}

@Composable private fun QualityChip(quality: String?) {
    val label = SaleFormModel.qualityLabel(quality)
    if (label.isNotBlank()) FarmStatusChip(label, tone = if (quality == "defective") FarmTone.Amber else FarmTone.Good)
}

@Composable private fun FruitSection(draft: SaleDraft, context: CultivationUiState, stock: SaleStock, prices: List<JSONObject>, sales: List<JSONObject>,
                                     errors: Map<String, String>, vm: SaleEntryViewModel) {
    val colors = MaterialTheme.colorScheme
    val options = stock.options(draft.seasonId)
    val byKey = options.associateBy { it.groupId to it.gradeId }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Tiny), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            Text("Buah yang dijual", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            seasonName(context, draft.seasonId)?.let { Text("Stok ${greenhouseName(context, draft.greenhouseId)} · $it", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
        }
        fun indexOf(line: SaleLine) = draft.items.indexOfFirst { it.id == line.id }
        fun editLine(updated: SaleLine) = vm.edit { d -> d.copy(items = d.items.map { if (it.id == updated.id) updated else it }) }
        val cards = SaleFormModel.cards(draft, stock)
        val groupCounts = cards.groupingBy { it.groupId }.eachCount()
        cards.forEach { card -> key(card.groupId, card.gradeId) {
            val option = byKey[card.groupId to card.gradeId]
            Surface(Modifier.fillMaxWidth().testTag("sale-stock-${card.groupId}-${card.gradeId}"), shape = FarmShape.Card, color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant)) {
                Column(Modifier.padding(start = FarmSpace.Large, end = FarmSpace.Large, top = 6.dp, bottom = FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        Text(card.varietyName.ifBlank { "Buah" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        QualityChip(card.quality)
                        Spacer(Modifier.weight(1f))
                        Text("sisa ${SaleFormModel.kg(card.availableKg)} kg", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant)
                        IconButton({ vm.edit { SaleFormModel.removeCard(it, card.groupId, card.gradeId) } }, Modifier.offset(x = 10.dp).testTag("sale-remove-${card.groupId}-${card.gradeId}")) {
                            CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                                FarmControlIcon(FarmIcon.Close, "Hapus ${SaleFormModel.optionName(option ?: StockOption(card.groupId, card.gradeId, card.varietyName, card.quality, card.availableKg))}")
                            }
                        }
                    }
                    card.lines.forEach { line -> key(line.id) {
                        val index = indexOf(line)
                        val graded = line.buyerGrade.isNotBlank()
                        val body: @Composable ColumnScope.() -> Unit = {
                            if (graded) Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Grade ${line.buyerGrade}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                    color = farmToneColors(FarmTone.Amber).second)
                                if (card.lines.size > 1) TextButton({ vm.edit { d -> d.copy(items = d.items.filterNot { it.id == line.id }) } },
                                    Modifier.heightIn(min = FarmSpace.Touch).testTag("sale-remove-grade-${line.id}")) { Text("Hapus grade") }
                            }
                            errors["items.$index.buyer_grade"]?.let { FieldError(it) }
                            FarmFieldPair({
                                FinanceField("Bobot", line.quantity, { editLine(line.copy(quantity = it)) }, "sale-kg-${line.id}", errors["items.$index.quantity"],
                                    KeyboardType.Decimal, suffix = "kg", prominent = true)
                            }, {
                                FinanceField(if (graded) "Buah (opsional)" else "Jumlah buah (opsional)", line.fruitCount, { editLine(line.copy(fruitCount = it)) }, "sale-fruit-${line.id}", errors["items.$index.fruit_count"],
                                    KeyboardType.Number, suffix = "buah", prominent = true)
                            })
                            FinanceField("Harga per kg", line.price, { editLine(line.copy(price = it)) }, "sale-price-${line.id}", errors["items.$index.price"],
                                KeyboardType.Decimal, suffix = "/kg", prominent = true, visual = IdrGrouping, leadingIcon = { Text("Rp", color = colors.onSurfaceVariant) })
                            Text(SaleFormModel.priceHint(draft, option, prices, sales, line), Modifier.testTag("sale-price-hint-${line.id}"),
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            errors["items.$index.source"]?.let { FieldError(it) }
                            SaleFormModel.lineCalc(line)?.let { (calc, amount) ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text(calc, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant)
                                    Text(amount, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (graded) Surface(Modifier.fillMaxWidth(), shape = FarmShape.Tile, color = colors.surfaceVariant.copy(alpha = 0.35f), border = BorderStroke(1.dp, colors.outlineVariant)) {
                            Column(Modifier.padding(FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(10.dp), content = body)
                        } else Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = body)
                    } }
                    if (card.canAddGrade) {
                        val tag = if ((groupCounts[card.groupId] ?: 0) > 1) "sale-add-grade-${card.groupId}-${card.gradeId}" else "sale-add-grade-${card.groupId}"
                        OutlinedButton({ vm.edit { SaleFormModel.addGrade(it, card.groupId, card.gradeId) } }, shape = FarmShape.Control,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag(tag)) { Text("Tambah grade ${card.varietyName}") }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        Text(card.after(), Modifier.weight(1f).testTag("sale-after-${card.groupId}-${card.gradeId}"), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = if (card.short) colors.error else colors.onSurfaceVariant, fontWeight = if (card.short) FontWeight.Bold else null)
                        Text(card.sum(), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant)
                    }
                }
            }
        } }
        // Lines from older drafts whose harvest is no longer known: they stay, with their numbers, until fruit is picked again.
        draft.items.filter { it.groupId.isBlank() || it.gradeId.isBlank() }.forEach { line -> key(line.id) {
            val index = indexOf(line)
            FarmFormSection("Buah belum dipilih") {
                FieldError(errors["items.$index.source"] ?: "Pilih buah lagi.")
                Text("Bobot ${line.quantity.ifBlank { "-" }} kg, harga ${line.price.ifBlank { "-" }} per kg.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                FarmChoiceChips("Buah dan kualitas", null, options.map { JSONObject().put("id", "${it.groupId}|${it.gradeId}").put("name", SaleFormModel.optionName(it)) }, "sale-pick-${line.id}") { id ->
                    editLine(line.copy(groupId = id.substringBefore('|'), gradeId = id.substringAfter('|')))
                }
                TextButton({ vm.edit { d -> d.copy(items = d.items.filterNot { it.id == line.id }) } }, Modifier.heightIn(min = FarmSpace.Touch)) { Text("Hapus baris ini") }
            }
        } }
        val more = SaleFormModel.more(draft, stock)
        if (more.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            Text(if (cards.isEmpty()) "Pilih buah" else "Tambah buah lain", Modifier.padding(horizontal = FarmSpace.Tiny), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                more.forEach { option ->
                    OutlinedButton({ vm.edit { SaleFormModel.addLine(it, option, if (it.buyerType == "consumer") SaleFormModel.consumerPrice(prices, option) else null) } },
                        enabled = draft.items.size < 30, shape = FarmShape.Control, contentPadding = PaddingValues(horizontal = FarmSpace.Medium, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 44.dp).testTag("sale-add-${option.groupId}-${option.gradeId}")) {
                        CompositionLocalProvider(LocalContentColor provides colors.primary) { Box(Modifier.size(18.dp)) { FarmControlIcon(FarmIcon.Add, null) } }
                        Spacer(Modifier.width(6.dp))
                        Text(SaleFormModel.optionName(option), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text("sisa ${SaleFormModel.kg(option.availableKg)} kg", color = colors.onSurfaceVariant, fontWeight = FontWeight.Normal)
                    }
                }
            }
        } else if (cards.isEmpty() && draft.seasonId.isNotBlank()) Notice("Belum ada panen yang bisa dijual di musim ini.")
        errors["items"]?.let { FieldError(it) }
    }
}

@Composable private fun TotalCard(totals: SaleTotals) {
    FarmHeroCard(motif = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Total penjualan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(totals.meta(), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(totals.total?.let(SaleFormModel::money) ?: "Rp0", Modifier.testTag("sale-total"), style = FarmType.HeroNumber, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/** Ringkasan, step 2 of 2: total, who and when, the fruit sold, and how much stock each harvest keeps after the sale. */
@Composable fun SaleReview(draft: SaleDraft, context: CultivationUiState, busy: Boolean, onBack: () -> Unit, onQueue: () -> Unit, credit: Map<String, BigDecimal> = emptyMap()) {
    val colors = MaterialTheme.colorScheme
    val stock = remember(context, credit) { saleStock(context, credit) }
    val cards = SaleFormModel.cards(draft, stock)
    val totals = SaleFormModel.totals(draft, stock)
    FarmFormFrame(header = {
        Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                IconButton(onBack, enabled = !busy, modifier = Modifier.testTag("sale-back-top")) { FarmControlIcon(FarmIcon.ChevronLeft, "Kembali ke isian") }
                Text("Ringkasan penjualan", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            FarmEntrySteps(2)
        }
    }, bar = { FarmFormBar("sale-back", onBack, "sale-queue", "Simpan", onQueue, enabled = !busy, mainIcon = true, saveLabel = "Ubah", busy = busy) }) {
        FarmHeroCard(motif = false) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (draft.customer.isNotBlank()) "Total penjualan ke ${draft.customer}" else "Total penjualan", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(totals.total?.let(SaleFormModel::money) ?: "Rp0", Modifier.testTag("sale-total-fact"),
                    style = FarmType.HeroNumber.copy(fontSize = 34.sp, lineHeight = 40.sp), color = MaterialTheme.colorScheme.secondary)
                Text(listOfNotNull("${SaleFormModel.kg(totals.kg)} kg", totals.fruit?.let { "$it buah" }, totals.average?.let { "rata-rata ${SaleFormModel.money(it)}/kg" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Data transaksi", Modifier.padding(horizontal = FarmSpace.Tiny).semantics { heading() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
            Column(Modifier.padding(horizontal = FarmSpace.Large, vertical = 6.dp)) {
                val rows = buildList {
                    add(Triple("Pembeli", draft.customer.ifBlank { "Tidak dicatat" }, listOfNotNull(BuyerTypes.label(draft.buyerType),
                        draft.buyerWhatsapp.ifBlank { null }, if (draft.buyerId.isNotBlank() && !draft.buyerExisting) "pembeli baru, masuk daftar" else null).joinToString(" · ")))
                    add(Triple("Greenhouse", listOfNotNull(greenhouseName(context, draft.greenhouseId), seasonName(context, draft.seasonId)).joinToString(" · "), null))
                    add(Triple("Lunas", whenLabel(draft.date, draft.time).substringAfter(", ").replace(" · ", ", "), null))
                    if (draft.paymentMethod.isNotBlank()) add(Triple("Cara bayar", when (draft.paymentMethod) { "cash" -> "Tunai"; "qris" -> "QRIS"; else -> "Transfer" }, null))
                    if (draft.notes.isNotBlank()) add(Triple("Keterangan", draft.notes, null))
                    if (draft.reason.isNotBlank()) add(Triple("Alasan koreksi", draft.reason, null))
                }
                rows.forEachIndexed { index, (label, value, note) ->
                    if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = FarmSpace.Medium).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End) }
                        }
                    }
                }
            }
        }
        Text("Buah yang dijual", Modifier.padding(horizontal = FarmSpace.Tiny).semantics { heading() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
            Column {
                draft.items.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = FarmSpace.Large), color = colors.outlineVariant)
                    val card = cards.find { it.lines.any { l -> l.id == line.id } }
                    Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Large, vertical = 14.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                            Text(card?.varietyName?.ifBlank { null } ?: "Buah belum dipilih", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            QualityChip(card?.quality)
                            if (line.buyerGrade.isNotBlank()) FarmStatusChip("Grade ${line.buyerGrade}", tone = FarmTone.Amber)
                            Spacer(Modifier.weight(1f))
                            SaleFormModel.lineCalc(line)?.let { Text(it.second, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold) }
                        }
                        val price = runCatching { SaleFormModel.money(SaleDraft.decimal(line.price, false)) + "/kg" }.getOrDefault("harga belum diisi")
                        Text(SaleFormModel.amountLine(listOf(line)) + " · " + price, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        if (cards.isNotEmpty()) {
            Text("Stok panen sesudah disimpan", Modifier.padding(horizontal = FarmSpace.Tiny).semantics { heading() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
                Column(Modifier.padding(horizontal = FarmSpace.Large, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    cards.forEach { card ->
                        val name = listOf(card.varietyName.ifBlank { "Buah" }, SaleFormModel.qualityLabel(card.quality)).filter { it.isNotBlank() }.joinToString(" ")
                        Column(Modifier.fillMaxWidth().testTag("sale-summary-stock-${card.groupId}-${card.gradeId}").semantics(mergeDescendants = true) {},
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${SaleFormModel.kg(card.availableKg)} kg → ", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant)
                                Text(if (card.short) "kurang" else "${SaleFormModel.kg(card.afterKg)} kg", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.Bold, color = if (card.short) colors.error else colors.onSurface)
                            }
                            val left = if (card.availableKg.signum() > 0) card.afterKg.divide(card.availableKg, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f) else 0f
                            Row(Modifier.fillMaxWidth().height(8.dp).background(colors.surfaceVariant, RoundedCornerShape(4.dp))) {
                                if (left > 0f) Box(Modifier.weight(left).fillMaxHeight().background(colors.primary, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
                                if (left < 1f) Box(Modifier.weight(1f - left).fillMaxHeight().background(SoldShare, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
                            }
                            (if (card.short) card.after() else card.fifoText())?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = if (card.short) colors.error else colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        FarmInfoCard {
            Text("Sesudah disimpan, penjualan ini masuk ke Penjualan ${seasonName(context, draft.seasonId) ?: "musim ini"} di tab Keuangan sebagai ${BuyerTypes.label(draft.buyerType)}.",
                style = MaterialTheme.typography.bodyMedium)
        }
        if (draft.replaceValidationRejected) Notice("Perbaikan ini dikirim sebagai catatan baru. Catatan yang ditolak tetap ada di riwayat.")
    }
}

/** Detail of a sale in Riwayat: the server's total, buyer, payment and the fruit lines. */
@Composable private fun SaleDraftFacts(draft: SaleDraft, context: CultivationUiState, acceptedTotal: String?) {
    val stock = saleStock(context)
    val preview = runCatching { draft.preview() }.getOrNull()
    if (acceptedTotal != null) FarmFact("Total tercatat", moneyLabel(acceptedTotal), Modifier.testTag("sale-accepted-total").semantics(mergeDescendants = true) {}, prominent = true)
    else if (preview != null) FarmFact("Total penjualan", moneyLabel(preview.total.toPlainString()), Modifier.testTag("sale-total-fact").semantics(mergeDescendants = true) {}, prominent = true)
    else Notice("Total belum dapat dihitung dari isian ini.", true)
    preview?.let { FarmFact("Bobot terjual", "${measureText(it.weightKg.toPlainString())} kg") }
    FarmFact("Musim", seasonName(context, draft.seasonId) ?: "Nama belum tersedia")
    FarmFact("Pembayaran", "${whenLabel(draft.date, draft.time)} · ${if (draft.paid) "Lunas" else "Belum dikonfirmasi"}")
    if (draft.customer.isNotBlank()) FarmFact("Pembeli", draft.customer)
    if (draft.hasBuyer()) FarmFact("Tipe pembeli", BuyerTypes.label(draft.buyerType))
    val names = stock.options(draft.seasonId).associateBy { it.groupId to it.gradeId }
    draft.items.forEach { line ->
        HorizontalDivider()
        val name = names[line.groupId to line.gradeId]?.let(SaleFormModel::optionName) ?: "Buah"
        FarmFact(name + if (line.buyerGrade.isNotBlank()) " · grade ${line.buyerGrade}" else "",
            SaleFormModel.amountLine(listOf(line)) + " · " + (line.price.takeIf(String::isNotBlank)?.let { "${moneyLabel(it.replace(',', '.'))}/kg" } ?: "harga belum diisi"))
    }
    if (draft.notes.isNotBlank()) FarmFact("Keterangan", draft.notes)
    if (draft.reason.isNotBlank()) FarmFact("Alasan koreksi", draft.reason)
}

@Composable fun SaleFacts(record: LocalRecord, context: CultivationUiState) {
    val draft = runCatching { SaleDraft.from(record.id, record.greenhouseId, record.body, record.baseVersion, saleStock(context)) }.getOrNull()
    if (draft == null) { Notice("Rincian penjualan belum lengkap. Data tetap tersimpan.", true); return }
    SaleDraftFacts(draft, context, record.body.optString("total_amount").takeUnless { record.body.isNull("total_amount") || it.isBlank() })
    saleStock(context).withLines(record.body).optJSONArray("lines")?.let { lines ->
        repeat(lines.length()) { i -> val line = lines.getJSONObject(i); if (line.has("variety_name") && !line.isNull("variety_name")) Text("${line.getString("variety_name")} · ${line.optString("grade_name")}", style = MaterialTheme.typography.bodySmall) }
    }
    if (record.body.optString("record_state") == "cancelled") Notice("Penjualan dibatalkan dan tidak dihitung lagi di penjualan aktif.", true)
    record.body.optString("recorded_by_name").takeIf { it.isNotBlank() }?.let { Text("Dicatat oleh: $it") }
}
