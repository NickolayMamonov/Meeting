package dev.whysoezzy.communities.subscribers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whysoezzy.common.dispatcher.DispatcherProvider
import com.whysoezzy.domain.usecase.GetCommunityByIdUseCase
import com.whysoezzy.domain.usecase.GetCommunitySubscribersUseCase
import com.whysoezzy.network.toUserMessage
import dev.whysoezzy.communities.mappers.toPersonItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

sealed interface CommunitySubscribersNavEvent {
    data class NavigateToProfile(
        val userId: Long,
    ) : CommunitySubscribersNavEvent
}

class CommunitySubscribersViewModel(
    private val getCommunityByIdUseCase: GetCommunityByIdUseCase,
    private val getCommunitySubscribersUseCase: GetCommunitySubscribersUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow<CommunitySubscribersUiState>(CommunitySubscribersUiState.Loading)
    val uiState: StateFlow<CommunitySubscribersUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<CommunitySubscribersNavEvent>(extraBufferCapacity = 1)
    val navEvent: SharedFlow<CommunitySubscribersNavEvent> = _navEvent.asSharedFlow()

    fun onEvent(event: CommunitySubscribersEvent) {
        when (event) {
            is CommunitySubscribersEvent.LoadSubscribers -> loadSubscribers(event.communityId)
            is CommunitySubscribersEvent.NavigateToProfile -> viewModelScope.launch {
                _navEvent.emit(CommunitySubscribersNavEvent.NavigateToProfile(event.userId))
            }
        }
    }

    private fun loadSubscribers(communityId: Long) {
        viewModelScope.launch {
            _uiState.value = CommunitySubscribersUiState.Loading
            try {
                val community = getCommunityByIdUseCase(communityId).getOrThrow()
                val subscribers = getCommunitySubscribersUseCase(communityId).getOrThrow()

                val items = withContext(dispatchers.default) {
                    subscribers.map { it.toPersonItem() }
                }
                _uiState.value = CommunitySubscribersUiState.Success(
                    communityName = community.name,
                    subscribers = items,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load community subscribers")
                _uiState.value = CommunitySubscribersUiState.Error(message = e.toUserMessage())
            }
        }
    }
}
