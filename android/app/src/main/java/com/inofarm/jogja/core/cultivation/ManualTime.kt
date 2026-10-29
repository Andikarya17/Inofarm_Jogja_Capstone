package com.inofarm.jogja.core.cultivation

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ManualTime {
    val WIB:ZoneId=ZoneId.of("Asia/Jakarta")
    fun encode(date:String,time:String):String {
        require(Regex("([01][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?").matches(time)) {"Jam harus HH:mm, detik opsional."}
        return LocalDateTime.of(LocalDate.parse(date),LocalTime.parse(time)).atZone(WIB).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
    }
    fun decode(occurredAt:String):Pair<String,String> {
        val occurred=OffsetDateTime.parse(occurredAt).atZoneSameInstant(WIB)
        return occurred.toLocalDate().toString() to occurred.toLocalTime().format(DateTimeFormatter.ofPattern(if(occurred.second==0) "HH:mm" else "HH:mm:ss"))
    }
}
