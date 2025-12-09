package com.whysoezzy.common.utils

import java.time.Instant.ofEpochMilli
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMMM", Locale.getDefault())

    fun formatDate(dateTime: LocalDateTime): String {
        return dateTime.format(dateFormatter)
    }

    fun toTimestamp(dateTime: LocalDateTime): Long {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun fromTimestamp(timestamp: Long): LocalDateTime {
        return LocalDateTime.ofInstant(
            ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )
    }
}