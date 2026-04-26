package dev.whysoezzy.meetings.di

import com.whysoezzy.auth.domain.usecase.IsLoggedInUseCase
import com.whysoezzy.domain.usecase.GetHeroMeetingsUseCase
import com.whysoezzy.domain.usecase.GetMainScreenDataUseCase
import com.whysoezzy.domain.usecase.GetMeetingParticipantsUseCase
import com.whysoezzy.domain.usecase.GetPopularMeetingsUseCase
import com.whysoezzy.domain.usecase.GetRecommendedCommunitiesUseCase
import com.whysoezzy.domain.usecase.ManageCommunitySubscriptionUseCase
import com.whysoezzy.domain.usecase.SearchCommunitiesUseCase
import com.whysoezzy.domain.usecase.SearchUseCase
import dev.whysoezzy.meetings.details.presentation.MeetingDetailsViewModel
import dev.whysoezzy.meetings.participants.presentation.MeetingParticipantsViewModel
import dev.whysoezzy.meetings.presentation.MainScreenViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val mainFeatureModule = module {

    factory {
        GetMainScreenDataUseCase(
            meetingsRepository = get(),
            getHeroMeetingsUseCase = get<GetHeroMeetingsUseCase>(),
            getPopularMeetingsUseCase = get<GetPopularMeetingsUseCase>(),
            getCommunities = {
                get<GetRecommendedCommunitiesUseCase>().invoke()
            }
        )
    }

    factory {
        SearchUseCase(
            meetingsRepository = get(),
            searchCommunities = { query ->
                get<SearchCommunitiesUseCase>().invoke(query)
            }
        )
    }
    factory { ManageCommunitySubscriptionUseCase(get()) }

    // ViewModels
    viewModel { MainScreenViewModel(get(), get()) }
    viewModel {
        MeetingDetailsViewModel(
            getMeetingByIdUseCase = get(),
            joinMeetingUseCase = get(),
            leaveMeetingUseCase = get(),
            isLoggedInUseCase = get()
        )
    }
    viewModel { MeetingParticipantsViewModel(get<GetMeetingParticipantsUseCase>()) }
}
