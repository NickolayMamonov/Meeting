package com.whysoezzy.data.di

import com.whysoezzy.data.api.MeetingsApi
import com.whysoezzy.data.api.MeetingsApiKtor
import com.whysoezzy.data.repository.MeetingsRepositoryImpl
import com.whysoezzy.domain.repository.MeetingsRepository
import com.whysoezzy.domain.usecase.GetAllMeetingsUseCase
import com.whysoezzy.domain.usecase.GetHeroMeetingsUseCase
import com.whysoezzy.domain.usecase.GetMeetingByIdUseCase
import com.whysoezzy.domain.usecase.GetMeetingParticipantsUseCase
import com.whysoezzy.domain.usecase.GetPagedMeetingsUseCase
import com.whysoezzy.domain.usecase.GetPopularMeetingsUseCase
import com.whysoezzy.domain.usecase.JoinMeetingUseCase
import com.whysoezzy.domain.usecase.LeaveMeetingUseCase
import com.whysoezzy.domain.usecase.SearchMeetingsUseCase
import org.koin.core.qualifier.named
import org.koin.dsl.module

val meetingsModule = module {

    single<MeetingsApi> { MeetingsApiKtor(get(named("authorizedClient"))) }

    single<MeetingsRepository> { MeetingsRepositoryImpl(get()) }

    factory { GetHeroMeetingsUseCase(get()) }
    factory { GetPopularMeetingsUseCase(get()) }
    factory { GetAllMeetingsUseCase(get()) }
    factory { SearchMeetingsUseCase(get()) }
    factory { GetMeetingByIdUseCase(get()) }
    factory { GetMeetingParticipantsUseCase(get()) }
    factory { JoinMeetingUseCase(get()) }
    factory { LeaveMeetingUseCase(get()) }
    factory { GetPagedMeetingsUseCase(get()) }
}
