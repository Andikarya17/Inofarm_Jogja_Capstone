package com.inofarm.jogja

import com.inofarm.jogja.feature.admin.AdminForm
import com.inofarm.jogja.feature.admin.AdminResource
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
        assertEquals("2026-11-02T00:00:00Z",payload.getString("effective_from"));assertFalse(payload.has("planting_group_id"))
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
}
