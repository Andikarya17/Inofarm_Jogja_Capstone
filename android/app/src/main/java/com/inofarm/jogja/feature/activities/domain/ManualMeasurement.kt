package com.inofarm.jogja.feature.activities.domain

import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.database.LocalRecord
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/** A pair is always one revision of one human activity; missing EC is never synthesized. */
data class ManualMeasurement(val id:String,val at:Instant,val ec:String?,val ppm:String,val recorder:String?,val queued:Boolean) {
    fun message(today:LocalDate):String = if(at.atZone(ActivityDraft.WIB).toLocalDate()==today) "Pengukuran hari ini" else
        "Belum ada pengukuran hari ini. Data ${at.atZone(ActivityDraft.WIB).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'WIB'",java.util.Locale.forLanguageTag("id-ID")))}."
    companion object {
        fun from(record:LocalRecord,today:LocalDate):ManualMeasurement? {
            if(record.syncState !in setOf(SyncState.ACCEPTED,SyncState.QUEUED)) return null
            if(record.syncState==SyncState.QUEUED && record.baseVersion!=null && (record.serverVersion ?: 0)>record.baseVersion) return null
            val body=record.body
            if(body.has("_local_form") || body.optString("record_state","active")!="active" || body.optString("work_state")!="completed") return null
            val at=runCatching {OffsetDateTime.parse(body.getString("occurred_at")).toInstant()}.getOrNull() ?: return null
            if(at.atZone(ActivityDraft.WIB).toLocalDate()>today) return null
            fun decimal(key:String):String? {
                if(body.isNull(key) || !body.has(key)) return null
                return runCatching {com.inofarm.jogja.core.contracts.WireContract.decimal(body.getString(key)).also {require(it>=BigDecimal.ZERO)}.stripTrailingZeros().toPlainString()}.getOrNull()
            }
            val ppm=decimal("manual_ppm") ?: return null
            val ec=decimal("manual_ec")
            // Historical PPM-only accepted rows remain readable. New/pending pairs must be complete.
            if(ec==null && (record.syncState==SyncState.QUEUED || body.optString("activity_type")=="measurement" || (!body.isNull("manual_ec") && body.has("manual_ec")))) return null
            fun recorder(source:org.json.JSONObject?)=source?.optString("recorded_by_name")?.takeUnless {it.isBlank() || it=="null"}
            return ManualMeasurement(record.id,at,ec,ppm,recorder(body) ?: recorder(record.serverBody),record.syncState==SyncState.QUEUED)
        }
    }
}
