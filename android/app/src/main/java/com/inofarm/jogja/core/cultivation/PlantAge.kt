package com.inofarm.jogja.core.cultivation

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class PlantAge(val hss: Long?, val hst: Long?, val reasons: List<String>) {
    companion object {
        fun calculate(sowing: String, transplant: String?, occurred: String): PlantAge {
            val start = LocalDate.parse(sowing)
            val moved = transplant?.let(LocalDate::parse)
            require(moved == null || !moved.isBefore(start)) { "Pindah tanam tidak boleh sebelum semai." }
            val day = OffsetDateTime.parse(occurred).atZoneSameInstant(ZoneId.of("Asia/Jakarta")).toLocalDate()
            if(day.isBefore(start)) return PlantAge(null, null, listOf("Kegiatan sebelum semai memerlukan kebijakan pengelola."))
            val hst = moved?.takeUnless(day::isBefore)?.let { ChronoUnit.DAYS.between(it, day) + 1 }
            return PlantAge(ChronoUnit.DAYS.between(start, day) + 1, hst, if(hst == null) listOf("HST belum berlaku sebelum pindah tanam.") else emptyList())
        }
    }
}
