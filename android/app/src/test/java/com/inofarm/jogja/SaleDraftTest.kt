package com.inofarm.jogja

import com.inofarm.jogja.feature.sales.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SaleDraftTest {
    private val gh=UUID.randomUUID().toString();private val season=UUID.randomUUID().toString()
    private val group=UUID.randomUUID().toString();private val grade=UUID.randomUUID().toString()
    private fun line(quantity:String="1,000001",price:String="0,005")=SaleLine(groupId=group,gradeId=grade,quantity=quantity,price=price)
    private fun ready()=SaleDraft(greenhouseId=gh,seasonId=season,date="2026-10-30",time="08:12:34",paid=true,buyerType="consumer",items=listOf(line()))
    private fun withBuyer(type:String)=ready().copy(buyerType=type,buyerId=UUID.randomUUID().toString(),customer="Pembeli")
    @Test fun rawInvalidInputsAndUnconfirmedPaidRemainDraftAcrossSerialization() {
        val draft=ready().copy(date="belum",paid=false,items=listOf(SaleLine(quantity="1,2,3",price="")))
        assertEquals(draft,SaleDraft.from(draft.id,gh,draft.localJson(),null));assertTrue(draft.validation().containsKey("paid"));assertTrue(draft.validation().containsKey("items.0.price"));assertThrows(IllegalArgumentException::class.java) {draft.payload()}
    }
    @Test fun wireAmountsAreExactAndTotalsRemainServerOwned() {
        val draft=ready();val body=draft.payload();val row=body.getJSONArray("lines").getJSONObject(0)
        assertEquals("1.000001",row.getString("quantity_kg"));assertEquals("0.005000",row.getString("price_per_kg"));assertEquals("0.01",draft.preview().total.toPlainString())
        assertFalse(body.has("total_amount"));assertFalse(body.has("recorded_by"));assertFalse(body.has("items"));assertEquals("paid",body.getString("payment_status"));assertEquals("2026-10-30T08:12:34+07:00",body.getString("paid_at"))
        assertEquals(group,row.getString("planting_group_id"));assertEquals(grade,row.getString("grade_id"))
    }
    @Test fun payloadSendsLinesWithFruitCountNullWhenBlank() {
        val draft=ready().copy(items=listOf(line(),line().copy(id=UUID.randomUUID().toString(),gradeId=UUID.randomUUID().toString(),fruitCount="12")))
        val rows=draft.payload().getJSONArray("lines")
        assertTrue(rows.getJSONObject(0).has("fruit_count") && rows.getJSONObject(0).isNull("fruit_count"));assertEquals(12,rows.getJSONObject(1).getInt("fruit_count"))
        assertTrue(draft.copy(items=listOf(line().copy(fruitCount="0"))).validation().containsKey("items.0.fruit_count"))
        assertTrue(draft.copy(items=listOf(line().copy(fruitCount="1,5"))).validation().containsKey("items.0.fruit_count"))
    }
    @Test fun buyerTypeRequired() {
        val draft=ready().copy(buyerType="")
        assertTrue(draft.validation().containsKey("buyer_type"));assertThrows(IllegalArgumentException::class.java) {draft.payload()}
        assertFalse(draft.copy(baseVersion=2,reason="x").validation().containsKey("buyer_type"))
    }
    @Test fun retailRequiresBuyer() {
        assertTrue(ready().copy(buyerType="retail").validation().containsKey("customer"))
        assertTrue(ready().copy(buyerType="partner").validation().containsKey("customer"))
        val payload=withBuyer("retail").payload();assertEquals("retail",payload.getString("customer_type"));assertEquals("retail",payload.getJSONObject("customer").getString("type"))
    }
    @Test fun consumerBuyerOptional() {
        val draft=ready();assertTrue(draft.validation().isEmpty())
        val payload=draft.payload();assertEquals("consumer",payload.getString("customer_type"));assertTrue(payload.isNull("customer"))
        assertEquals("Pembeli",withBuyer("consumer").payload().getJSONObject("customer").getString("name"))
    }
    @Test fun buyerGradeOnlyForPartner() {
        val graded=withBuyer("partner").copy(items=listOf(line().copy(buyerGrade="A")))
        assertTrue(graded.validation().isEmpty());assertEquals("A",graded.payload().getJSONArray("lines").getJSONObject(0).getString("buyer_grade"))
        assertTrue(graded.copy(buyerType="retail").validation().containsKey("items"))
        assertTrue(graded.copy(items=listOf(line().copy(buyerGrade="D"))).validation().containsKey("items"))
    }
    @Test fun duplicateLineKeyRejectedUnlessPartnerGradesDiffer() {
        val a=line().copy(buyerGrade="A");val b=line().copy(id=UUID.randomUUID().toString(),buyerGrade="B")
        val partner=withBuyer("partner")
        assertTrue(partner.copy(items=listOf(a,b)).validation().isEmpty())
        assertTrue(partner.copy(items=listOf(a,b.copy(buyerGrade="A"))).validation().containsKey("items"))
        assertTrue(withBuyer("retail").copy(items=listOf(line(),line().copy(id=UUID.randomUUID().toString()))).validation().containsKey("items"))
    }
    @Test fun repeatedKeyAllowedWhenEveryLineIsAlreadyOnTheServerSale() {
        // A backfilled sale holds one line per old harvest item, so two lines can share group + grade; the server keeps them.
        val first=line();val second=line().copy(id=UUID.randomUUID().toString())
        val correcting=withBuyer("retail").copy(items=listOf(first,second),baseVersion=2,lockedSources=setOf(first.id,second.id),reason="Harga dikoreksi")
        assertFalse(correcting.validation().containsKey("items"))
        val added=line().copy(id=UUID.randomUUID().toString())
        assertTrue(correcting.copy(items=listOf(first,second,added)).validation().containsKey("items"))
        assertTrue(correcting.copy(baseVersion=null).validation().containsKey("items"))
    }
    @Test fun acceptedLineIdsSeasonAndWibSecondsArePreservedForReasonedCorrection() {
        val draft=ready();val accepted=draft.payload().put("paid_at","2026-10-30T01:12:34.000000Z")
        val restored=SaleDraft.from(draft.id,gh,JSONObject().put("season_id",season).put("paid_at",accepted.getString("paid_at")).put("payment_status","paid").put("customer_type","consumer")
            .put("lines",JSONArray().put(JSONObject().put("id",draft.items.single().id).put("planting_group_id",group).put("grade_id",grade).put("quantity_kg","1.000001").put("fruit_count",JSONObject.NULL).put("price_per_kg","0.005000").put("buyer_grade",JSONObject.NULL))),2)
        assertTrue(restored.lockedSeason);assertEquals(setOf(draft.items.single().id),restored.lockedSources);assertEquals("08:12:34",restored.time);assertEquals("",restored.items.single().fruitCount)
        assertTrue(restored.validation().containsKey("reason"));assertFalse(restored.copy(reason="Koreksi nyata").validation().containsKey("reason"))
    }
    private fun legacyLocal(sourceId:String)=JSONObject().put("season_id",season).put("_local_form",JSONObject().put("season_id",season).put("date","2026-11-20").put("time","10:00").put("paid",true)
        .put("buyer_type","consumer").put("items",JSONArray().put(JSONObject().put("id",UUID.randomUUID().toString()).put("source_id",sourceId).put("quantity","3").put("price","1000").put("grade",""))))
    private fun stock()=SaleStock(listOf(JSONObject().put("id","h1").put("planting_group_id",group).put("occurred_at","2026-11-17T08:00:00+07:00").put("version",1).put("record_state","active")
        .put("items",JSONArray().put(JSONObject().put("id","i1").put("grade_id",grade).put("grade_name","Layak").put("weight_kg","10")))),emptyList(),emptyList(),emptyList(),emptyList())
    @Test fun legacyDraftMapsSourceToGroupAndGrade() {
        val restored=SaleDraft.fromLocal(UUID.randomUUID().toString(),gh,legacyLocal("i1"),null,stock())
        assertEquals(group,restored.items.single().groupId);assertEquals(grade,restored.items.single().gradeId);assertEquals("3",restored.items.single().quantity)
    }
    @Test fun legacyDraftWithUnknownSourceKeepsLine() {
        val restored=SaleDraft.fromLocal(UUID.randomUUID().toString(),gh,legacyLocal("hilang"),null,stock())
        assertEquals("",restored.items.single().groupId);assertEquals("3",restored.items.single().quantity)
        assertEquals("Pilih buah lagi.",restored.validation()["items.0.source"])
    }
    @Test fun saleBodyWithoutLinesStillParses() {
        val itemId=UUID.randomUUID().toString()
        val body=JSONObject().put("season_id",season).put("paid_at","2026-11-20T10:00:00+07:00").put("payment_status","paid").put("customer_name","")
            .put("items",JSONArray().put(JSONObject().put("id",itemId).put("harvest_item_id","i1").put("grade_name","Layak").put("quantity_kg","2.500000").put("price_per_kg","1000.000000").put("buyer_grade","B")))
        val restored=SaleDraft.from(UUID.randomUUID().toString(),gh,body,2)
        assertEquals(itemId,restored.items.single().id);assertEquals("B",restored.items.single().buyerGrade);assertEquals("",restored.items.single().groupId)
        assertEquals(group,SaleDraft.from(UUID.randomUUID().toString(),gh,body,2,stock()).items.single().groupId)
    }
    @Test fun lineFieldMapsServerKeyToIndexAndField() {
        assertEquals(2 to "fruit_count",SaleErrors.lineField("operation.payload.lines.2.fruit_count"))
        assertEquals(0 to "quantity_kg",SaleErrors.lineField("payload.lines.0.quantity_kg"));assertNull(SaleErrors.lineField("operation.payload.notes"))
    }
}
