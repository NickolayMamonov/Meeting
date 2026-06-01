package com.whysoezzy.common.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    private const val DATE_TIME_PATTERN = "dd MMMM yyyy, HH:mm"
    private val ruLocale = Locale.forLanguageTag("ru")
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    /**
     * Форматирует epoch-millis в "dd MMMM yyyy, HH:mm" в зоне UTC.
     * UTC согласован с parseDateToTimestamp (R-036): naive-время бэкенда
     * показываем без сдвига на TZ устройства.
     */
    fun formatDateTime(timestamp: Long): String =
        SimpleDateFormat(DATE_TIME_PATTERN, ruLocale)
            .apply { timeZone = utc }
            .format(Date(timestamp))
}
