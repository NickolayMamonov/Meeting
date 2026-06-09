package dev.whysoezzy.meetings.details.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

fun openMapIntent(context: Context, latitude: Double, longitude: Double, address: String) {
    try {
        val uri = "geo:$latitude,$longitude?q=${Uri.encode(address)}".toUri()
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (e: ActivityNotFoundException) {
        // нет карт — игнорируем
    }
}

fun shareIntent(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться встречей"))
}

fun openUrlIntent(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // нет приложения, способного открыть ссылку — игнорируем
    }
}
