package com.whysoezzy.common.utils

object AddressUtils {
    fun extractMetroFromAddress(address: String): String =
        if (address.contains("М.")) {
            address.substringAfter("М.").substringBefore(",").trim()
        } else {
            "Не указано"
        }
}
