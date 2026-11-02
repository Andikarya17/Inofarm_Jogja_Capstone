package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.sync.OperationRoute
import com.inofarm.jogja.feature.reports.SeasonReportDraft
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SeasonReportContractTest {
    private fun fixture()=JSONObject(File("../../contracts/v2/fixtures/season-report-public.json").readText())
    private fun preview()=fixture().getJSONObject("preview")
    private fun reviewed():SeasonReportDraft {
        val p=preview()
        return SeasonReportDraft(greenhouseId=p.getString("greenhouse_id"),seasonId=p.getString("season_id"),costs=p.getJSONObject("coverage").getString("costs"),
            sales=p.getJSONObject("coverage").getString("sales"),pending=if(p.isNull("known_pending_count")) "" else p.getInt("known_pending_count").toString(),
            requestedState=p.getString("report_state"),note=p.getString("review_note"),previewText=p.toString(),previewFetchedAt=42L)
    }
    @Test fun blankFormNeverAssumesZeroOrCompletenessAndRawTextSurvivesRestore() {
        val draft=SeasonReportDraft(greenhouseId=preview().getString("greenhouse_id"),pending=" 1,5 ",note="  Catatan\nα  ")
        assertFalse(draft.validation().isEmpty());assertThrows(IllegalArgumentException::class.java) {draft.input()}
        assertEquals(draft,SeasonReportDraft.from(draft.id,draft.greenhouseId,draft.localJson()))
    }
    @Test fun pendingUnknownAndKnownZeroRemainDifferentAndMalformedValuesAreRejected() {
        val draft=reviewed()
        assertTrue(draft.copy(pending="").input().isNull("known_pending_count"))
        assertEquals(0,draft.copy(pending="0").input().get("known_pending_count"))
        for(value in listOf("01","-1","0.0","1e3","1,0"," 0 ","2147483648")) assertTrue(value,draft.copy(pending=value).validation().containsKey("pending"))
        assertEquals(Int.MAX_VALUE,draft.copy(pending=Int.MAX_VALUE.toString()).input().getInt("known_pending_count"))
    }
    @Test fun everyReviewedInputMustStillMatchBeforeQueueing() {
        val draft=reviewed();assertEquals(SeasonReportContract.VERSION,draft.payload().getString("expected_formula_version"))
        val changes=listOf(draft.copy(note="Tinjauan berubah"),draft.copy(pending="8"),draft.copy(costs="partial"),draft.copy(requestedState="reviewed_final"),draft.copy(seasonId="60000000-0000-0000-0000-000000000004"))
        for(changed in changes) assertThrows(IllegalArgumentException::class.java) {changed.payload()}
        assertThrows(IllegalStateException::class.java) {draft.copy(previewText=null).payload()}
        assertEquals(draft,SeasonReportDraft.from(draft.id,draft.greenhouseId,draft.localJson()))
    }
    @Test fun actualServerSnapshotUsesExactStringsAndCanonicalIntegrity() {
        val data=fixture();val accepted=data.getJSONObject("receipt").getJSONObject("data")
        SeasonReportContract.validate(accepted,true);SeasonReportContract.validate(preview(),false)
        assertEquals(accepted.getString("snapshot_hash"),WireContract.hash(data.getJSONObject("full")))
        val metrics=accepted.getJSONObject("metrics")
        assertEquals("8000000.00",metrics.getJSONObject("TC").getString("value"))
        assertEquals("266.666667",metrics.getJSONObject("BEP_Q").getString("value"))
        assertTrue(metrics.getJSONObject("TR").get("value") is String)
    }
    @Test fun unsupportedOrCoercedReportMetadataIsRejected() {
        val bad=listOf(preview().put("formula_version","unknown-v2"),preview().put("source_count","7"),preview().put("source_count",1.5),preview().put("known_pending_count",false),
            preview().also {it.getJSONObject("metrics").getJSONObject("TC").put("value",8000000.0)},
            preview().also {it.getJSONObject("metrics").getJSONObject("RC").put("display_scale","8")},
            preview().also {it.getJSONObject("metrics").getJSONObject("BEP_Q").getJSONObject("exact_fraction").put("denominator","0")})
        for(value in bad) assertThrows(IllegalArgumentException::class.java) {SeasonReportContract.validate(value,false)}
    }
    @Test fun reportRouteRequiresManagerAndImmutableCreate() {
        val id=reviewed().id
        assertEquals(OperationRoute("season-reports","POST"),OperationRoute.forOperation("season_report","create",id,UserRole.MANAGER))
        for(action in listOf("create","update","cancel")) assertThrows(IllegalArgumentException::class.java) {OperationRoute.forOperation("season_report",action,id,UserRole.WORKER)}
        for(action in listOf("update","cancel")) assertThrows(IllegalArgumentException::class.java) {OperationRoute.forOperation("season_report",action,id,UserRole.MANAGER)}
    }
}
