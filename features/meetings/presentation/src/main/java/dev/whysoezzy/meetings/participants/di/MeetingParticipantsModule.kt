package dev.whysoezzy.meetings.participants.di

import dev.whysoezzy.meetings.participants.presentation.MeetingParticipantsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val meetingParticipantsModule = module {
    viewModel {
        MeetingParticipantsViewModel(
            getMeetingByIdUseCase = get()
        )
    }
}