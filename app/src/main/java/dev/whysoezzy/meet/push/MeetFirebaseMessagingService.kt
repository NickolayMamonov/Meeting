package dev.whysoezzy.meet.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

class MeetFirebaseMessagingService internal constructor(
    private val injectedMessageHandoff: PushMessageHandoff? = null,
) : FirebaseMessagingService() {
    override fun onRegistered(fid: String) {
        coordinator().onRegistered(fid)
    }

    override fun onUnregistered(fid: String) {
        coordinator().onUnregistered()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val handoff = injectedMessageHandoff ?: coordinator()
        val ingressExitEpoch = handoff.captureExitEpoch()
        // FirebaseMessagingService has no BroadcastReceiver-style goAsync(). Keep the
        // callback alive until the encrypted ledger handoff completes so returning from
        // this worker thread cannot lose an ingress job.
        runBlocking(Dispatchers.IO) {
            handoff.handleDataMessage(
                data = message.data,
                hasNotificationBlock = message.notification != null,
                ingressExitEpoch = ingressExitEpoch,
            )
        }
    }

    private fun coordinator(): PushRegistrationCoordinator =
        GlobalContext.get().get()
}

internal interface PushMessageHandoff {
    fun captureExitEpoch(): Long

    suspend fun handleDataMessage(
        data: Map<String, String>,
        hasNotificationBlock: Boolean,
        ingressExitEpoch: Long,
    )
}
