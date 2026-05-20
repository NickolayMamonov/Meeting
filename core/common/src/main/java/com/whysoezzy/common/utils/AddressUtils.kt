package com.whysoezzy.common.utils

object AddressUtils {
    fun extractMetroFromAddress(address: String): String {
        return if (address.contains("М.")) {
            address.substringAfter("М.").substringBefore(",").trim()
        } else {
            "Не указано"
        }
    }
}