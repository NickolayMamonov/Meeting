package dev.whysoezzy.meet.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.core.context.GlobalContext

class MeetFirebaseMessagingService : FirebaseMessagingService() {
    override fun onRegistered(fid: String) {
        coordinator().onRegistered(fid)
    }

    override fun onUnregistered(fid: String) {
        coordinator().onUnregistered()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        coordinator().onDataMessage(
            data = message.data,
            hasNotificationBlock = message.notification != null,
        )
    }

    private fun coordinator(): PushRegistrationCoordinator =
        GlobalContext.get().get()
}
