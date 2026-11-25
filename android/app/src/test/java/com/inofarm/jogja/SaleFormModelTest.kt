package com.inofarm.jogja

import com.inofarm.jogja.feature.sales.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class SaleFormModelTest {
    private val gh = "10000000-0000-4000-8000-000000000001"
    private val season = "20000000-0000-4000-8000-000000000001"
    private val golden = "30000000-0000-4000-8000-000000000001"
    private val inthanon = "30000000-0000-4000-8000-000000000002"
    private val layak = "70000000-0000-4000-8000-000000000001"
    private val cacat = "70000000-0000-4000-8000-000000000002"

    private fun item(id: String, grade: String, kg: String) = JSONObject().put("id", id).put("grade_id", grade).put("grade_name", if (grade == layak) "Layak" else "Cacat")
        .put("weight_kg", kg).put("quality", if (grade == layak) "saleable" else "defective")
    private fun harvest(id: String, group: String, at: String, vararg items: JSONObject) = JSONObject().put("id", id).put("planting_group_id", group)
        .put("occurred_at", at).put("version", 1).put("record_state", "active").put("items", JSONArray(items.toList()))
    private val stock = SaleStock(
        listOf(harvest("h1", golden, "2026-11-17T08:00:00+07:00", item("g1", layak, "32"), item("g1c", cacat, "40")),
            harvest("h2", golden, "2026-11-20T08:00:00+07:00", item("g2", layak, "308")),
            harvest("h3", inthanon, "2026-11-18T08:00:00+07:00", item("i1", layak, "210"), item("i1c", cacat, "25"))),
        emptyList(),
        listOf(JSONObject().put("id", golden).put("season_id", season).put("variety_id", "v1"), JSONObject().put("id", inthanon).put("season_id", season).put("variety_id", "v2")),
        listOf(JSONObject().put("id", "v1").put("name", "Golden"), JSONObject().put("id", "v2").put("name", "Inthanon")),
        listOf(JSONObject().put("id", layak).put("name", "Layak"), JSONObject().put("id", cacat).put("name", "Cacat")))
    private val prices = listOf(JSONObject().put("variety_name", "golden").put("quality", "saleable").put("price_per_kg", "34000.00"))
    private fun option(group: String, grade: String) = stock.options(season).single { it.groupId == group && it.gradeId == grade }
    private fun draft(type: String) = SaleDraft(greenhouseId = gh, seasonId = season, buyerType = type, items = emptyList())

    @Test fun retailTwoVarietiesTotalAndStockAfter() {
        var d = SaleFormModel.addLine(draft("retail"), option(golden, layak), null)
        d = d.copy(items = d.items.map { it.copy(quantity = "50", fruitCount = "38", price = "28000") })
        d = SaleFormModel.addLine(d, option(inthanon, layak), null)
        d = d.copy(items = d.items.mapIndexed { i, l -> if (i == 1) l.copy(quantity = "20", fruitCount = "16", price = "26000") else l })
        val totals = SaleFormModel.totals(d, stock)
        assertEquals("Rp1.920.000", SaleFormModel.money(totals.total!!));assertEquals("70 kg · 54 buah · 2 varietas", totals.meta())
        assertEquals("Rp27.429", SaleFormModel.money(totals.average!!))
        val cards = SaleFormModel.cards(d, stock)
        assertEquals(listOf(golden, inthanon), cards.map { it.groupId })
        assertEquals(0, BigDecimal("340").compareTo(cards[0].availableKg));assertEquals(0, BigDecimal("290").compareTo(cards[0].afterKg))
        assertEquals("Diambil dari panen 17 Nov (32 kg) dan 20 Nov (18 kg), yang paling lama lebih dulu", cards[0].fifoText())
        assertEquals("Diambil dari panen 18 Nov (20 kg)", cards[1].fifoText())
        assertEquals("50 kg · 38 buah", cards[0].sum())
        assertEquals(listOf("Golden Cacat", "Inthanon Cacat"), SaleFormModel.more(d, stock).map { SaleFormModel.optionName(it) })
    }

    @Test fun partnerLinesGetGradesAndAddGradeTakesNextFree() {
        var d = SaleFormModel.addLine(draft("partner"), option(golden, layak), null)
        assertEquals("A", d.items.single().buyerGrade)
        d = SaleFormModel.addGrade(d, golden, layak);d = SaleFormModel.addGrade(d, golden, layak)
        assertEquals(listOf("A", "B", "C"), d.items.map { it.buyerGrade })
        assertFalse(SaleFormModel.cards(d, stock).single().canAddGrade)
        assertSame(d, SaleFormModel.addGrade(d, golden, layak))
        // Cacat fruit is not graded by the mitra.
        val defective = SaleFormModel.addLine(draft("partner"), option(golden, cacat), null)
        assertEquals("", defective.items.single().buyerGrade);assertFalse(SaleFormModel.cards(defective, stock).single().canAddGrade)
    }

    @Test fun switchingTypeKeepsOneLinePerFruitAndFillsConsumerPrice() {
        var d = SaleFormModel.addLine(draft("partner"), option(golden, layak), null)
        d = SaleFormModel.addGrade(d, golden, layak).let { it.copy(items = it.items.map { l -> l.copy(quantity = "5") }) }
        val retail = SaleFormModel.switchType(d, "retail", stock, prices)
        assertEquals(1, retail.items.size);assertEquals("", retail.items.single().buyerGrade);assertEquals("", retail.items.single().price)
        val consumer = SaleFormModel.switchType(retail, "consumer", stock, prices)
        assertEquals("34000", consumer.items.single().price)
        val back = SaleFormModel.switchType(consumer, "partner", stock, prices)
        assertEquals("A", back.items.single().buyerGrade)
        assertNull(SaleFormModel.fillConsumerPrices(consumer, stock, prices))
    }

    @Test fun consumerAddPrefillsAndHintsSayWhereThePriceComesFrom() {
        val d = SaleFormModel.addLine(draft("consumer"), option(golden, layak), SaleFormModel.consumerPrice(prices, option(golden, layak)))
        assertEquals("34000", d.items.single().price)
        assertEquals("Harga perorangan Golden Layak, diisi dari Kelola. Masih bisa diubah.", SaleFormModel.priceHint(d, option(golden, layak), prices, emptyList()))
        assertEquals("Grade dinilai mitra, harga ditawar.", SaleFormModel.priceHint(d.copy(buyerType = "partner"), option(golden, layak), prices, emptyList()))
        assertEquals("Harga ditawar. Belum ada penjualan sebelumnya ke pembeli ini.",
            SaleFormModel.priceHint(d.copy(buyerType = "retail", buyerId = "c1", customer = "Toko"), option(golden, layak), prices, emptyList()))
        val past = JSONObject().put("id", "s0").put("customer_id", "c1").put("paid_at", "2026-11-12T03:00:00Z").put("record_state", "active")
            .put("lines", JSONArray().put(JSONObject().put("variety_name", "Golden").put("price_per_kg", "28000.000000").put("quantity_kg", "60.000000")))
        assertEquals("Harga ditawar. Terakhir ke Toko Buah Segar: Rp28.000/kg untuk 60 kg, 12 Nov.",
            SaleFormModel.priceHint(d.copy(buyerType = "retail", buyerId = "c1", customer = "Toko Buah Segar"), option(golden, layak), prices, listOf(past)))
        // The sale being corrected is not its own last price.
        assertEquals("Harga ditawar. Belum ada penjualan sebelumnya ke pembeli ini.",
            SaleFormModel.priceHint(d.copy(id = "s0", buyerType = "retail", buyerId = "c1", customer = "Toko"), option(golden, layak), prices, listOf(past)))
    }

    @Test fun shortfallShowsAvailable() {
        val d = SaleFormModel.addLine(draft("retail"), option(inthanon, cacat), null).let { it.copy(items = it.items.map { l -> l.copy(quantity = "30", price = "1") }) }
        val card = SaleFormModel.cards(d, stock).single()
        assertTrue(card.short);assertEquals("Sisa hanya 25 kg", card.after())
    }

    @Test fun openSeasonPicksLatestOpenSeasonOfGreenhouse() {
        val seasons = listOf(JSONObject().put("id", "a").put("greenhouse_id", gh).put("status", "open").put("start_date", "2026-01-01"),
            JSONObject().put("id", "b").put("greenhouse_id", gh).put("status", "open").put("start_date", "2026-08-01"),
            JSONObject().put("id", "c").put("greenhouse_id", gh).put("status", "closed").put("start_date", "2026-10-01"),
            JSONObject().put("id", "d").put("greenhouse_id", "other").put("status", "open").put("start_date", "2026-11-01"))
        assertEquals("b", SaleFormModel.openSeason(seasons, gh));assertNull(SaleFormModel.openSeason(seasons, "none"))
    }

    @Test fun serverErrorsMapToLineIndexes() {
        val lines = listOf(SaleLine(id = "l0"), SaleLine(id = "l1"))
        val error = JSONObject().put("code", "validation_failed").put("field_errors", JSONObject().put("operation.payload.lines.1.price_per_kg", JSONArray().put("Harga tidak sah.")))
        assertEquals(mapOf("items.1.price" to "Harga tidak sah."), SaleErrors.fromServer(error, lines))
        val exceeded = JSONObject().put("code", "harvest_exceeded").put("reasons", JSONArray().put(JSONObject().put("line_id", "l0").put("available_kg", "25.000000")))
        assertEquals(mapOf("items.0.quantity" to "Sisa hanya 25 kg"), SaleErrors.fromServer(exceeded, lines))
        assertTrue(SaleErrors.fromServer(null, lines).isEmpty())
    }

    @Test fun creditOfOwnServerLines() {
        val body = JSONObject().put("lines", JSONArray().put(JSONObject().put("allocations", JSONArray().put(JSONObject().put("harvest_item_id", "g1").put("quantity_kg", "10.000000")))))
        val credit = SaleStock.creditOf(body)
        assertEquals(0, BigDecimal("10").compareTo(credit.getValue("g1")))
        val credited = SaleStock(listOf(harvest("h1", golden, "2026-11-17T08:00:00+07:00", item("g1", layak, "32"))),
            listOf(JSONObject().put("id", "g1").put("harvest_id", "h1").put("harvest_version", 1).put("available_weight_kg", "0").put("active", true)),
            listOf(JSONObject().put("id", golden).put("season_id", season).put("variety_id", "v1")), emptyList(), emptyList(), credit)
        assertEquals(0, BigDecimal("10").compareTo(credited.options(season).single().availableKg))
    }

    @Test fun paymentMethodRoundTripsAndIsSentOnlyWhenChosen() {
        val ready = SaleDraft(greenhouseId = gh, seasonId = season, date = "2026-11-25", time = "10:15", paid = true, buyerType = "consumer",
            items = listOf(SaleLine(groupId = golden, gradeId = layak, quantity = "3", price = "34000")))
        assertFalse(ready.payload().has("payment_method"))
        val transfer = ready.copy(paymentMethod = "transfer")
        assertEquals("transfer", transfer.payload().getString("payment_method"))
        assertEquals(transfer, SaleDraft.from(transfer.id, gh, transfer.localJson(), null))
        assertTrue(ready.copy(paymentMethod = "cek").validation().containsKey("payment_method"))
        assertEquals("Pilih buah yang dijual.", ready.copy(items = emptyList()).validation()["items"])
    }
}
