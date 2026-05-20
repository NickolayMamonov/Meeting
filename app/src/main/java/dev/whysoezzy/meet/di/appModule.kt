package dev.whysoezzy.meet.di

import dev.whysoezzy.meet.navigation.AuthCheckViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module{
    viewModel { AuthCheckViewModel(get()) }
}