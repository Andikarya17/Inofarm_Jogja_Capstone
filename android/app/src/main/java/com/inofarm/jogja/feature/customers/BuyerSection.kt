package com.inofarm.jogja.feature.customers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.auth.userMessage
import com.inofarm.jogja.feature.sales.SaleDraft
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Buyer part of the sale form (Inofarm 20 Nov 2026, buyer types 25 Nov 2026): the type is picked above it, this part
 * searches the buyer list of that type (imported from Excel or added before) and picks one, or fills a new buyer that
 * joins the list when the sale is saved. A Perorangan buyer may stay unnamed.
 */
@Composable internal fun BuyerSection(draft: SaleDraft, error: String?, whatsappError: String? = null, search: (suspend (String, String) -> List<Customer>)?, edit: ((SaleDraft) -> SaleDraft) -> Unit) {
    if (!draft.hasBuyer()) {
        // A sale saved before buyer types existed keeps its typed name; it stays editable until a type is chosen.
        if (draft.customer.isNotBlank() || error != null) com.inofarm.jogja.feature.FarmFormSection("Pembeli") {
            FinanceField("Nama pembeli (boleh kosong)", draft.customer, { value -> edit { it.copy(customer = value) } }, "sale-customer", error)
        }
        return
    }
    if (draft.buyerExisting) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
            Row(Modifier.padding(start = FarmSpace.Large, end = FarmSpace.Medium, top = FarmSpace.Medium, bottom = FarmSpace.Medium),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                Text(draft.customer.trim().take(1).uppercase(), Modifier.size(44.dp).background(colors.primary, RoundedCornerShape(22.dp)).wrapContentSize(),
                    color = colors.onPrimary, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(buyerRole(draft.buyerType), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Text(draft.customer, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(draft.buyerWhatsapp.ifBlank { "Tanpa WhatsApp" }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                OutlinedButton({ edit { it.copy(buyerId = "", buyerExisting = false, customer = "", buyerWhatsapp = "") } }, shape = FarmShape.Control,
                    modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("sale-buyer-change")) { Text("Ganti") }
            }
        }
        return
    }
    com.inofarm.jogja.feature.FarmFormSection(if (draft.buyerType == "consumer") "Pembeli (boleh kosong)" else "Pembeli") {
        BuyerPicker(draft, error, whatsappError, search, edit)
    }
}

/** "Pembeli retail", "Mitra (tengkulak)": the line above the buyer's name. */
internal fun buyerRole(type: String) = when (type) { "partner" -> "Mitra (tengkulak)"; "retail" -> "Pembeli retail"; else -> "Pembeli perorangan" }

@Composable private fun BuyerPicker(draft: SaleDraft, error: String?, whatsappError: String?, search: (suspend (String, String) -> List<Customer>)?, edit: ((SaleDraft) -> SaleDraft) -> Unit) {
    if (draft.buyerId.isNotBlank()) {
        FinanceField("Nama pembeli baru", draft.customer, { value -> edit { it.copy(customer = value) } }, "sale-customer", error)
        FinanceField("Nomor WhatsApp (opsional)", draft.buyerWhatsapp, { value -> edit { it.copy(buyerWhatsapp = value) } }, "sale-buyer-whatsapp", whatsappError, KeyboardType.Phone,
            hint = "Hanya terlihat oleh pengelola. Pembeli ini masuk daftar setelah penjualan disimpan.")
        TextButton({ edit { it.copy(buyerId = "", customer = "", buyerWhatsapp = "") } }, Modifier.heightIn(min = FarmSpace.Touch)) { Text("Cari di pembeli terdaftar") }
        return
    }
    var query by remember(draft.buyerType) { mutableStateOf("") }
    var hits by remember(draft.buyerType) { mutableStateOf<List<Customer>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(draft.buyerType, query) {
        if (search == null) return@LaunchedEffect
        delay(250)
        runCatching { search(draft.buyerType, query) }.onSuccess { hits = it; failure = null }.onFailure { hits = emptyList(); failure = userMessage(it) }
    }
    FinanceField("Cari pembeli terdaftar", query, { query = it }, "sale-buyer-search", null, hint = "Nama atau nomor WhatsApp")
    when {
        search == null || failure != null -> Text("Daftar pembeli butuh koneksi. Isi sebagai pembeli baru; server tidak membuat data ganda untuk nomor yang sama.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        hits == null -> Text("Mencari…", style = MaterialTheme.typography.bodySmall)
        hits!!.isEmpty() -> Text(if (query.isBlank()) "Belum ada pembeli ${com.inofarm.jogja.feature.sales.BuyerTypes.label(draft.buyerType).lowercase()} terdaftar." else "Tidak ada pembeli bernama atau bernomor \"$query\".",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Surface(shape = FarmShape.Tile, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column {
                hits!!.take(5).forEachIndexed { index, hit ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        edit { it.copy(buyerId = hit.id, buyerExisting = true, customer = hit.name, buyerWhatsapp = hit.whatsappDisplay.orEmpty()) }
                    }.heightIn(min = 56.dp).padding(horizontal = FarmSpace.Medium, vertical = FarmSpace.Small).testTag("sale-buyer-hit-${hit.id}"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                        Text(hit.name.take(1).uppercase(), Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(18.dp))
                            .wrapContentSize(), fontWeight = FontWeight.Bold)
                        Column(Modifier.weight(1f)) {
                            Text(hit.name, fontWeight = FontWeight.Bold)
                            Text("${hit.whatsappDisplay ?: "Tanpa WhatsApp"} · ${hit.purchases} kali beli", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    OutlinedButton({ edit { it.copy(buyerId = UUID.randomUUID().toString(), buyerExisting = false, customer = query.takeIf { q -> q.any(Char::isLetter) }.orEmpty(), buyerWhatsapp = "") } },
        shape = FarmShape.Control, modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("sale-buyer-new")) {
        Text("Belum terdaftar? Isi pembeli baru")
    }
}
