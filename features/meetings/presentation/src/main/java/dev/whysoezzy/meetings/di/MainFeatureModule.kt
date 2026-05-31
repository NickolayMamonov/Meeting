package dev.whysoezzy.meetings.di

import com.whysoezzy.domain.usecase.GetMeetingParticipantsUseCase
import com.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import dev.whysoezzy.meetings.details.presentation.MeetingDetailsViewModel
import dev.whysoezzy.meetings.participants.presentation.MeetingParticipantsViewModel
import dev.whysoezzy.meetings.presentation.MainScreenViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val mainFeatureModule = module {
    factory { ManageCommunitySubscriptionUseCase(get()) }

    // ViewModels
    viewModel { MainScreenViewModel(
        getMainScreenDataUseCase = get(),
        manageCommunitySubscriptionUseCase = get(),
        dispatchers = get()
    ) }
    viewModel {
        MeetingDetailsViewModel(
            getMeetingByIdUseCase = get(),
            joinMeetingUseCase = get(),
            leaveMeetingUseCase = get(),
            isLoggedInUseCase = get(),
            dispatchers = get(),
        )
    }
    viewModel {
        MeetingParticipantsViewModel(
            getMeetingParticipantsUseCase = get(),
            dispatchers = get(),
        )
    }
}
