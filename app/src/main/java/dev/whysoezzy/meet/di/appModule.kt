package dev.whysoezzy.meet.di

import com.whysoezzy.common.dispatcher.DefaultDispatcherProvider
import com.whysoezzy.common.dispatcher.DispatcherProvider
import dev.whysoezzy.meet.navigation.AuthCheckViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule =
    module {
        single<DispatcherProvider> { DefaultDispatcherProvider() }
        viewModel { AuthCheckViewModel(get(), get(), get(), get()) }
    }
