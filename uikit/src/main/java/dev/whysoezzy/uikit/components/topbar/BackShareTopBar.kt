package dev.whysoezzy.uikit.components.topbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.uikit.tokens.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackShareTopBar(
    title: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(AppIcons.Back, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onShareClick) {
                Icon(AppIcons.Share, contentDescription = "Share")
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier,
    )
}

@Preview
@Composable
fun BackShareTopBarPreview() {
    BackShareTopBar(
        title = "Sample Title",
        onBackClick = { /* Handle back click */ },
        onShareClick = { /* Handle share click */ },
    )
}
