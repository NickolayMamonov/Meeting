package dev.whysoezzy.meetings.details.di

import com.whysoezzy.domain.usecase.GetMeetingByIdUseCase
import com.whysoezzy.domain.usecase.JoinMeetingUseCase
import com.whysoezzy.domain.usecase.LeaveMeetingUseCase
import dev.whysoezzy.meetings.details.presentation.MeetingDetailsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val meetingDetailsModule = module {
    viewModel {
        MeetingDetailsViewModel(
            getMeetingByIdUseCase = get<GetMeetingByIdUseCase>(),
            joinMeetingUseCase = get<JoinMeetingUseCase>(),
            leaveMeetingUseCase = get<LeaveMeetingUseCase>()
        )
    }
}
