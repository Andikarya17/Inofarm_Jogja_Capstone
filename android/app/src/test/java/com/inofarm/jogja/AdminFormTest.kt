package com.inofarm.jogja

import com.inofarm.jogja.feature.admin.AdminForm
import com.inofarm.jogja.feature.admin.AdminResource
import com.inofarm.jogja.feature.admin.AdminViewModel
import com.inofarm.jogja.core.network.FinanceApiFailure
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdminFormTest {
    private val id="11111111-1111-4111-8111-111111111111"
    @Test fun groupDraftRetainsRawInputAndRequiresValidDatesAndIntegerPopulation() {
        val draft=AdminForm(AdminResource.GROUP,values=mapOf("name" to "Kelompok A","season_id" to id,"variety_id" to id,"population" to "","sowing_date" to "2026-11-02","transplant_date" to ""))
        assertTrue(runCatching {draft.payload()}.isFailure)
        assertEquals("",AdminForm.parse(draft.json()).values["population"])
        val payload=draft.copy(values=draft.values+("population" to "0")).payload()
        assertEquals(0,payload.getInt("population"));assertTrue(payload.isNull("transplant_date"))
    }
    @Test fun referenceRoundtripKeepsMissingDifferentFromZeroAndNormalizesServerTimestamp() {
        val row=JSONObject().put("id","id").put("version",3).put("activity_type","nutrition").put("parameter","manual_ppm").put("unit","ppm")
            .put("effective_from","2026-11-02T00:00:00.000000Z").put("effective_until",JSONObject.NULL).put("target_value","0").put("minimum",JSONObject.NULL).put("maximum",JSONObject.NULL).put("tolerance",JSONObject.NULL).put("active",true)
        val draft=AdminForm.edit(AdminResource.TARGET,row).let {it.copy(values=it.values+("reason" to "Koreksi"))}
        val payload=draft.payload()
        assertEquals("0",payload.getString("target_value"));assertTrue(payload.isNull("minimum"))
        // The server accepts Y-m-d\TH:i:sP only: an explicit offset, never "Z".
        assertEquals("2026-11-02T07:00:00+07:00",payload.getString("effective_from"));assertFalse(payload.has("planting_group_id"))
    }
    @Test fun editingAScheduleShowsWibAndResendsAnOffsetTheServerAccepts() {
        val row=JSONObject().put("id",id).put("version",1).put("planting_group_id",id).put("activity_type","measurement")
            .put("planned_at","2026-11-29T00:00:00.000000Z").put("notes",JSONObject.NULL).put("active",true)
        val draft=AdminForm.edit(AdminResource.SCHEDULE,row)
        assertEquals("2026-11-29T07:00:00+07:00",draft.values["planned_at"])
        val payload=draft.copy(values=draft.values+("active" to "false")).payload()
        assertEquals("2026-11-29T07:00:00+07:00",payload.getString("planned_at"));assertFalse(payload.getBoolean("active"))
        val typedWrong=AdminForm(AdminResource.SCHEDULE,values=mapOf("planting_group_id" to id,"activity_type" to "measurement","planned_at" to "29/11/2026 07:00"))
        val message=runCatching {typedWrong.payload()}.exceptionOrNull()?.message.orEmpty()
        assertTrue(message,message.startsWith("Waktu rencana belum sesuai"))
    }
    @Test fun userPasswordsNeverEnterDraftAndEditEmailKeepsAccountId() {
        val form=AdminForm(AdminResource.USER,values=mapOf("greenhouse_id" to id,"name" to "Pekerja","email" to "worker@example.invalid","role" to "worker"))
        val password="synthetic-passphrase-only"
        assertEquals(password,form.payload(password).getString("password"));assertFalse(form.json().toString().contains(password))
        assertTrue(runCatching {form.payload()}.isFailure)
        val current=AdminForm.edit(AdminResource.USER,JSONObject().put("id","u").put("version",1).put("greenhouse_id",id).put("name","Pekerja").put("email","old@example.invalid").put("role","worker").put("active",true))
        assertEquals("old@example.invalid",current.values["email"])
        val updated=current.copy(values=current.values+mapOf("email" to " New@example.invalid ", "reason" to "Koreksi email"))
        assertEquals("new@example.invalid",updated.payload().getString("email"))
        assertFalse(updated.payload().has("password"))
        assertEquals("u",AdminForm.parse(updated.json()).id)
        assertThrows(IllegalArgumentException::class.java) { updated.copy(values=updated.values+("email" to "salah")).payload() }
    }
    @Test fun closeSeasonOnlySubmitsReasonAndRetainsVersion() {
        val form=AdminForm.edit(AdminResource.SEASON,JSONObject().put("id","s").put("version",4),"close")
        assertEquals(emptySet<String>(),form.payload().keys().asSequence().toSet())
        assertEquals(setOf("reason"),form.copy(values=mapOf("reason" to "Review selesai")).payload().keys().asSequence().toSet())
        assertEquals(4L,AdminForm.parse(form.json()).version)
    }
    @Test fun greenhouseSqlBooleanIsNormalizedAndInvalidDatesAreRejectedLocally() {
        val form=AdminForm.edit(AdminResource.GREENHOUSE,JSONObject().put("id",id).put("version",1).put("name","GH").put("active",1))
        assertTrue(form.copy(values=form.values+("reason" to "Koreksi nama")).payload().getBoolean("active"))
        val season=AdminForm(AdminResource.SEASON,values=mapOf("name" to "Musim","greenhouse_id" to id,"start_date" to "2026-11-02","end_date" to "2026-11-01"))
        assertTrue(runCatching {season.payload()}.isFailure)
        assertTrue(runCatching {season.copy(values=season.values+("greenhouse_id" to "invalid")).payload()}.isFailure)
    }
    @Test fun finalRefusalsUnlockTheFormButRetryableFailuresKeepTheOperation() {
        fun failure(status:Int,code:String="")=FinanceApiFailure(status,JSONObject().put("code",code).put("message","x"))
        listOf(failure(422),failure(409,"season_closed"),failure(409,"entity_exists"),failure(409,"version_conflict"),failure(403),failure(404))
            .forEach {assertTrue("${it.status} ${it.code}",AdminViewModel.definitive(it))}
        listOf(failure(401),failure(408),failure(429),failure(500),failure(503)).forEach {assertFalse("${it.status}",AdminViewModel.definitive(it))}
        assertFalse(AdminViewModel.definitive(java.io.IOException("offline")))
    }
    @Test fun openingAnotherRecordIsNotTheSameTargetAsTheUnfinishedForm() {
        val row=JSONObject().put("id",id).put("version",2).put("name","GH A").put("active",true)
        val edit=AdminForm.edit(AdminResource.GREENHOUSE,row)
        val other=AdminForm.edit(AdminResource.GREENHOUSE,JSONObject(row.toString()).put("id","22222222-2222-4222-8222-222222222222"))
        val create=AdminForm(AdminResource.GREENHOUSE)
        assertTrue(AdminViewModel.sameTarget(edit,edit.copy(values=edit.values+("name" to "GH B"))))
        assertFalse(AdminViewModel.sameTarget(edit,other))
        assertFalse(AdminViewModel.sameTarget(edit,create))
        assertFalse(AdminViewModel.sameTarget(create,AdminForm(AdminResource.GREENHOUSE)))
        assertFalse(AdminViewModel.sameTarget(create,AdminForm(AdminResource.SEASON)))
        assertFalse(AdminViewModel.sameTarget(edit,AdminForm.edit(AdminResource.GREENHOUSE,row,"close")))
    }
}
