package dev.whysoezzy.meet.push

import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService

/**
 * Deliberately keeps the FID-only Messaging surface in source so dependency upgrades cannot
 * silently remove one of the four required APIs.
 */
internal object FirebaseMessagingV2511CompileSmoke {
    fun register(messaging: FirebaseMessaging): Task<Void> = messaging.register()

    fun unregister(messaging: FirebaseMessaging): Task<Void> = messaging.unregister()

    class Callback : FirebaseMessagingService() {
        override fun onRegistered(fid: String) = Unit

        override fun onUnregistered(fid: String) = Unit
    }
}
