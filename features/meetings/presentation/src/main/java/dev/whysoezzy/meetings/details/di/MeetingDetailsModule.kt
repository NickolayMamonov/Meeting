package dev.whysoezzy.meetings.details.di

import dev.whysoezzy.meetings.details.presentation.MeetingDetailsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val meetingDetailsModule = module {
    viewModel { (meetingId: Long) ->
        MeetingDetailsViewModel()
    }
}
