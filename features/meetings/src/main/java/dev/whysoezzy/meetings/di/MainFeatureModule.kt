package dev.whysoezzy.meetings.di

import dev.whysoezzy.domain.usecase.GetMainScreenDataUseCase
import dev.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import dev.whysoezzy.domain.usecase.SearchUseCase
import dev.whysoezzy.meetings.data.mockDataModule
import dev.whysoezzy.meetings.details.presentation.MeetingDetailsViewModel
import dev.whysoezzy.meetings.participants.presentation.MeetingParticipantsViewModel
import dev.whysoezzy.meetings.presentation.MainScreenViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val mainFeatureModule = module {

    // Включаем mock данные
    includes(mockDataModule)

    // Use Cases
    factory { GetMainScreenDataUseCase(get(), get(), get()) }
    factory { SearchUseCase(get(), get()) }
    factory { ManageCommunitySubscriptionUseCase(get()) }

    // ViewModels
    viewModel { MainScreenViewModel(get(), get(), get()) }

    // Meeting Details and Participants - simple ViewModels without parameters for now
    viewModel { MeetingDetailsViewModel() }
    viewModel { MeetingParticipantsViewModel() }
}