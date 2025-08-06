package dev.whysoezzy.communities.di

import dev.whysoezzy.communities.details.di.communityDetailsModule
import dev.whysoezzy.communities.subscribers.di.communitySubscribersModule
import org.koin.dsl.module

val communitiesFeatureModule = module {
    includes(
        communityDetailsModule,
        communitySubscribersModule
    )
}