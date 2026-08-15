package dev.whysoezzy.meet.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.whysoezzy.meet.MainActivity

internal interface ReminderPresentationGateway {
    fun present(event: LedgerRecord.OwnedReminderEvent)

    fun cancel(eventIds: Collection<String>) = Unit
}

internal class AndroidReminderPresentationGateway(
    private val context: Context,
) : ReminderPresentationGateway {
    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    init {
        val systemManager = context.getSystemService(NotificationManager::class.java)
        systemManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Meeting reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminders for joined meetings"
            },
        )
    }

    override fun present(event: LedgerRecord.OwnedReminderEvent) {
        if (!notificationManager.areNotificationsEnabled()) return
        val offsetText = if (event.reminderOffsetMinutes == 60) "1 hour" else "24 hours"
        val tapIntent = Intent(context, MainActivity::class.java)
            .putExtra(PushTapIntent.EVENT_ID, event.eventId)
            .putExtra(PushTapIntent.MEETING_ID, event.meetingId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            event.eventId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notificationManager.notify(
            event.eventId,
            NOTIFICATION_ID,
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(dev.whysoezzy.meet.R.drawable.ic_launcher_foreground)
                .setContentTitle("Meeting reminder")
                .setContentText("Your joined meeting starts in $offsetText.")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    override fun cancel(eventIds: Collection<String>) {
        eventIds.forEach { notificationManager.cancel(it, NOTIFICATION_ID) }
    }

    private companion object {
        const val CHANNEL_ID = "meeting_reminders_v1"
        const val NOTIFICATION_ID = 1001
    }
}

internal data class PushTapCommand(
    val eventId: String,
    val meetingId: Long,
) {
    init {
        require(isCanonicalUuid(eventId))
        require(meetingId > 0L)
    }
}

internal object PushTapIntent {
    const val EVENT_ID = "push_event_id"
    const val MEETING_ID = "push_meeting_id"

    fun read(intent: Intent): PushTapCommand? {
        val eventId = intent.getStringExtra(EVENT_ID) ?: return null
        val meetingId = intent.getLongExtra(MEETING_ID, -1L)
        return runCatching { PushTapCommand(eventId, meetingId) }.getOrNull()
    }
}

internal object NoOpReminderPresentationGateway : ReminderPresentationGateway {
    override fun present(event: LedgerRecord.OwnedReminderEvent) = Unit
}
