package com.inofarm.jogja.feature.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.FarmBusyReveal
import com.inofarm.jogja.core.design.FarmControlIcon
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmIcon
import com.inofarm.jogja.core.design.FarmRowDivider
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.IdrGrouping
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.FarmEntrySteps
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.LocalCanopyForm
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.auth.userMessage
import com.inofarm.jogja.feature.sales.SalePricing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val QUALITIES = listOf("saleable" to "Layak", "defective" to "Cacat")
private val WIB = ZoneId.of("Asia/Jakarta")
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id-ID"))

/** "Diubah 25 Nov 2026" (WIB). The API sends no editor name, so only the date is shown. */
internal fun changedOn(updatedAt: String?): String? =
    updatedAt?.let { runCatching { "Diubah " + SHORT_DATE.format(Instant.parse(it).atZone(WIB)) }.getOrNull() }

private fun tagKey(name: String) = SalePricing.key(name).replace(' ', '-')

private fun typed(price: ConsumerPrice?): String = price?.let {
    BigDecimal(it.pricePerKg).stripTrailingZeros().let { value -> if (value.scale() < 0) value.setScale(0) else value }.toPlainString().replace('.', ',')
}.orEmpty()

/**
 * Harga perorangan (Kelola, Data master): one per-kg price per variety and quality for the final consumer. It only fills the
 * price when a sale is recorded; the sale keeps its own price. Retail and partner prices are negotiated per sale, so they are not here.
 */
@Composable
internal fun ConsumerPriceScreen(session: FinanceSession, ticket: Long, repository: ConsumerPriceRepository, onBack: () -> Unit) {
    var prices by remember { mutableStateOf<List<ConsumerPrice>?>(null) }
    var varieties by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ConsumerPriceDraft?>(null) }
    LaunchedEffect(session.scope.key, reload) {
        try {
            coroutineScope {
                val list = async { repository.list(session, ticket) }
                val names = async { repository.varietyNames(session, ticket) }
                prices = list.await(); varieties = names.await(); error = null
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = userMessage(failure); if (prices == null) prices = emptyList() }
    }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        KelolaTopBar("Harga perorangan", "Kelola · Data master", onBack, "Kembali")
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("consumer-price-list"), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium),
            contentPadding = PaddingValues(horizontal = FarmSpace.Page, vertical = FarmSpace.Large)) {
            item("intro") {
                Text("Harga per kg untuk pembeli akhir, sama berapa pun beratnya. Dipakai sebagai isian awal saat mencatat penjualan perorangan, dan masih bisa diubah di transaksinya.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            saved?.let { message -> item("saved") { Notice(message) } }
            error?.let { message -> item("error") {
                Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                    Notice("$message Butuh koneksi untuk melihat dan mengubah harga.", true)
                    TextButton({ reload++ }, Modifier.heightIn(min = FarmSpace.Touch).testTag("consumer-price-retry")) { Text("Coba lagi") }
                }
            } }
            if (prices == null) item("loading") { FarmBusyReveal { Text("Memuat harga…"); LinearProgressIndicator(Modifier.fillMaxWidth()) } }
            val list = prices
            if (list != null && varieties.isEmpty() && error == null) item("none") {
                Text("Belum ada varietas. Tambah varietas di pengaturan greenhouse, lalu atur harganya di sini.", style = MaterialTheme.typography.bodyMedium)
            }
            if (list != null) QUALITIES.forEach { (quality, label) ->
                item("section-$quality") { KelolaSectionTitle(label) }
                item("card-$quality") {
                    KelolaCard {
                        varieties.forEachIndexed { index, name ->
                            if (index > 0) FarmRowDivider(FarmSpace.Large)
                            val price = ConsumerPrices.find(list, name, quality)
                            KelolaRow(name, if (price != null) ConsumerPrices.display(price) else "Atur",
                                Modifier.testTag("consumer-price-$quality-${tagKey(name)}"),
                                hint = if (price != null) changedOn(price.updatedAt) else "Belum diatur, diketik saat menjual") {
                                saved = null
                                editing = ConsumerPriceDraft(price?.id, name, quality, typed(price), price?.version)
                            }
                        }
                    }
                }
            }
            item("note") {
                Text("Retail dan mitra tidak punya harga di sini karena harganya ditawar per transaksi. Mengubah harga tidak mengubah penjualan yang sudah tersimpan.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
    editing?.let { draft ->
        ConsumerPriceSheet(session, ticket, repository, draft, previous = ConsumerPrices.find(prices.orEmpty(), draft.varietyName, draft.quality), onDismiss = { editing = null }) { price ->
            prices = prices.orEmpty().filterNot { it.id == price.id } + price
            saved = "Harga ${price.varietyName} (${QUALITIES.first { it.first == price.quality }.second}) tersimpan: ${ConsumerPrices.display(price)}."
            editing = null
        }
    }
}

/** Step 1 of 2 is the price; step 2 is the Ringkasan with Ubah / Simpan. Save goes straight to the server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsumerPriceSheet(session: FinanceSession, ticket: Long, repository: ConsumerPriceRepository, start: ConsumerPriceDraft,
                               previous: ConsumerPrice?, onDismiss: () -> Unit, onSaved: (ConsumerPrice) -> Unit) {
    var price by rememberSaveable(start.varietyName, start.quality) { mutableStateOf(start.price) }
    var summary by rememberSaveable(start.varietyName, start.quality) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val qualityLabel = QUALITIES.first { it.first == start.quality }.second
    val draft = start.copy(price = price)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface, shape = FarmShape.Sheet,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal) }) {
        CompositionLocalProvider(LocalCanopyForm provides true) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FarmSpace.Page).padding(bottom = FarmSpace.Large).testTag("consumer-price-sheet"),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (summary) "Ringkasan harga" else if (previous == null) "Atur harga" else "Ubah harga", Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${start.varietyName} · $qualityLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.testTag("consumer-price-close")) { FarmControlIcon(FarmIcon.Close, "Batal") }
                }
                FarmEntrySteps(if (summary) 2 else 1)
                failure?.let { Notice(it, true) }
                if (summary) {
                    Text("Periksa sekali lagi. Simpan langsung mengirim harga ke server, jadi perlu koneksi internet.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    KelolaCard {
                        Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                            FarmFact("Varietas", start.varietyName)
                            FarmFact("Kualitas", qualityLabel)
                            FarmFact("Harga baru", com.inofarm.jogja.feature.financehome.rupiah(BigDecimal(draft.wirePrice())) + "/kg", prominent = true)
                            previous?.let { FarmFact("Harga sebelumnya", ConsumerPrices.display(it)) }
                        }
                    }
                    Text("Mengubah harga tidak mengubah penjualan yang sudah tersimpan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                        OutlinedButton(onClick = { summary = false; failure = null }, enabled = !busy, shape = FarmShape.Control,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("consumer-price-back")) { Text("Ubah") }
                        Button(onClick = {
                            busy = true; failure = null
                            scope.launch {
                                runCatching { repository.save(session, ticket, draft) }.onSuccess { onSaved(it) }.onFailure { failure = userMessage(it) }
                                busy = false
                            }
                        }, enabled = !busy, shape = FarmShape.Control, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("consumer-price-save")) {
                            Text(if (busy) "Menyimpan…" else "Simpan", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    FinanceField("Harga per kg", price, { price = it; error = null }, "consumer-price-input", error, KeyboardType.Decimal,
                        hint = previous?.let { "Sekarang ${ConsumerPrices.display(it)}" }, suffix = "IDR/kg", prominent = true, visual = IdrGrouping)
                    Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                        OutlinedButton(onClick = onDismiss, shape = FarmShape.Control, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("consumer-price-cancel")) { Text("Batal") }
                        Button(onClick = { runCatching { draft.wirePrice() }.onSuccess { summary = true }.onFailure { error = it.message } },
                            shape = FarmShape.Control, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("consumer-price-submit")) { Text("Simpan", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
