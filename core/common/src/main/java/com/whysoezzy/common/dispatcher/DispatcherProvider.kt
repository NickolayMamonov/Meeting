package com.whysoezzy.common.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Абстракция над [Dispatchers] для возможности подмены в тестах.
 *
 * Прямое использование Dispatchers.Default/IO в ViewModel создаёт
 * неуправляемый из тестов код: TestDispatcher управляет только
 * Dispatchers.Main, а withContext(Dispatchers.Default) уходит на
 * реальный thread pool. Это приводит к race conditions в тестах,
 * читающих StateFlow.value сразу после advanceUntilIdle().
 *
 * В production используется [DefaultDispatcherProvider]. В тестах —
 * собственная реализация, делегирующая всё на TestDispatcher.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}