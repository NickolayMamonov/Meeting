package dev.whysoezzy.meet.push

import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

internal interface FcmRegistrationClient {
    fun register(): Deferred<Result<Unit>>

    fun unregister(): Deferred<Result<Unit>>
}

internal class FirebaseMessagingRegistrationClient(
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
) : FcmRegistrationClient {
    override fun register(): Deferred<Result<Unit>> =
        observeTask(messaging.register()) { Unit }

    override fun unregister(): Deferred<Result<Unit>> =
        observeTask(messaging.unregister()) { Unit }
}

/**
 * Observes the Firebase task independently of any caller coroutine.
 *
 * Firebase's task listener is the completion authority. A waiter can cancel its
 * `await` without canceling or hiding this deferred result, and duplicate task
 * callbacks are reduced to the first terminal completion by `CompletableDeferred`.
 */
private fun <T, R> observeTask(
    task: Task<T>,
    map: (T) -> R,
): Deferred<Result<R>> {
    val completion = CompletableDeferred<Result<R>>()
    task.addOnCompleteListener { completed ->
        val result = if (completed.isSuccessful) {
            runCatching { map(completed.result) }
        } else {
            Result.failure(
                completed.exception
                    ?: IllegalStateException("Firebase Messaging task failed"),
            )
        }
        completion.complete(result)
    }
    return completion
}
