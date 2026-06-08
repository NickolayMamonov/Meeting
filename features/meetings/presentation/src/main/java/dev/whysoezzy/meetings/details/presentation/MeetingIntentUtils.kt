package dev.whysoezzy.meetings.details.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

fun openMapIntent(context: Context, latitude: Double, longitude: Double, address: String) {
    val uri = "geo:$latitude,$longitude?q=${Uri.encode(address)}".toUri()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
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
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}