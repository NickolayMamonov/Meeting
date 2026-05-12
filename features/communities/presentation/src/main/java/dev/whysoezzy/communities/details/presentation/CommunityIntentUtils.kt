package dev.whysoezzy.communities.details.presentation

import android.content.Context
import android.content.Intent

fun shareCommunityIntent(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться сообществом"))
}
