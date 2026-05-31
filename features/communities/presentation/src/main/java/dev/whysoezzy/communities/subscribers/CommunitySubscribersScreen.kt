package dev.whysoezzy.communities.subscribers

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.whysoezzy.communities.R
import dev.whysoezzy.uikit.components.layouts.PersonsGridContent
import dev.whysoezzy.uikit.components.layouts.PersonsGridError
import dev.whysoezzy.uikit.components.layouts.PersonsGridLoading
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitySubscribersScreen(
    communityId: Long,
    onBackPressed: () -> Unit,
    onPersonClick: (Long) -> Unit = {},
    viewModel: CommunitySubscribersViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(communityId) {
        viewModel.onEvent(CommunitySubscribersEvent.LoadSubscribers(communityId))
    }

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navEvent.collect { event ->
                when (event) {
                    is CommunitySubscribersNavEvent.NavigateToProfile -> onPersonClick(event.userId)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState) {
                            is CommunitySubscribersUiState.Success ->
                                (uiState as CommunitySubscribersUiState.Success).communityName
                            else -> stringResource(R.string.community_subscribers_default_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = stringResource(
                                dev.whysoezzy.uikit.R.string.action_back,
                            ),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is CommunitySubscribersUiState.Loading -> {
                PersonsGridLoading(modifier = Modifier.padding(paddingValues))
            }

            is CommunitySubscribersUiState.Success -> {
                PersonsGridContent(
                    persons = state.subscribers,
                    onPersonClick = { subscriberId ->
                        viewModel.onEvent(CommunitySubscribersEvent.NavigateToProfile(subscriberId))
                    },
                    emptyStateText = stringResource(R.string.community_subscribers_empty),
                    modifier = Modifier.padding(paddingValues),
                )
            }

            is CommunitySubscribersUiState.Error -> {
                PersonsGridError(
                    message = state.message,
                    onRetry = {
                        viewModel.onEvent(CommunitySubscribersEvent.LoadSubscribers(communityId))
                    },
                    onBackPressed = onBackPressed,
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}
