package dev.whysoezzy.meet.push

import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface FcmRegistrationClient {
    suspend fun register()

    fun unregister()
}

internal class FirebaseMessagingRegistrationClient(
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
) : FcmRegistrationClient {
    override suspend fun register() {
        messaging.register().awaitTask()
    }

    override fun unregister() {
        messaging.unregister()
    }
}

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Firebase Messaging task failed"),
                )
            }
        }
    }
