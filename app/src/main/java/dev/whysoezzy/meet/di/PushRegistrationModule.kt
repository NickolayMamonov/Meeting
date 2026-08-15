package dev.whysoezzy.meet.di

import com.whysoezzy.domain.repository.PushInstallationRepository
import com.whysoezzy.domain.usecase.AccountExitCoordinator
import dev.whysoezzy.meet.di.BoundedAccountExitCoordinator
import dev.whysoezzy.meet.push.AndroidReminderPresentationGateway
import dev.whysoezzy.meet.push.EncryptedPushStateStore
import dev.whysoezzy.meet.push.FcmRegistrationClient
import dev.whysoezzy.meet.push.FirebaseMessagingRegistrationClient
import dev.whysoezzy.meet.push.PushRegistrationCoordinator
import dev.whysoezzy.meet.push.PushStateStore
import dev.whysoezzy.meet.push.ReminderPresentationGateway
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val pushRegistrationModule =
    module {
        single<FcmRegistrationClient> { FirebaseMessagingRegistrationClient() }
        single<PushStateStore> { EncryptedPushStateStore(androidContext()) }
        single<ReminderPresentationGateway> {
            AndroidReminderPresentationGateway(androidContext())
        }
        single<PushRegistrationCoordinator> {
            PushRegistrationCoordinator(
                authSessionRepository = get(),
                installationRepository = get<PushInstallationRepository>(),
                fcm = get(),
                stateStore = get(),
                presentation = get(),
            )
        }
        single<AccountExitCoordinator> {
            BoundedAccountExitCoordinator(
                deleteCurrentUserProfile = get(),
                logoutUseCase = get(),
                authSessionRepository = get(),
                pushRegistrationCoordinator = get(),
            )
        }
    }
