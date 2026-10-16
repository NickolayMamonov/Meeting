package dev.whysoezzy.uikit.components.maps

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.whysoezzy.common.utils.isValidMapCoordinate
import dev.whysoezzy.uikit.BuildConfig
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.AppIcons
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private const val MAP_RENDER_DEADLINE_MILLIS = 15_000L
private const val MAP_PREVIEW_TEST_TAG = "uikit-map-preview"
internal const val MAP_PREVIEW_IMAGE_TEST_TAG = "uikit-map-preview-image"
internal const val MAP_PREVIEW_STATUS_TEST_TAG = "uikit-map-preview-status"
internal const val MAP_PREVIEW_LOGO_TEST_TAG = "uikit-map-preview-logo"
internal const val MAP_PREVIEW_TOP_ACTION_TEST_TAG = "uikit-map-preview-top-action"
internal const val MAP_PREVIEW_TELEMETRY_TEST_TAG = "uikit-map-preview-telemetry"
internal const val MAP_PREVIEW_INFO_TEST_TAG = "uikit-map-preview-info"
private const val MAP_PREVIEW_GLES3 = 0x00030000
private const val MAPBOX_URL = "https://www.mapbox.com/"
private const val MAPBOX_PRIVACY_URL = "https://www.mapbox.com/legal/privacy/"
private const val OPENSTREETMAP_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
private const val MAPBOX_FEEDBACK_URL = "https://apps.mapbox.com/feedback/"
internal val MAP_PREVIEW_FALLBACK_COLOR = Color(0xFFF3F5F7)

internal fun mapFeedbackUrl(): String = MAPBOX_FEEDBACK_URL

internal fun mapActionContentDescription(
    address: String,
    coordinateValid: Boolean,
): String =
    if (coordinateValid && address.isNotBlank()) {
        "Открыть в картах: $address"
    } else {
        "Открыть в картах"
    }

internal enum class MapPreviewStatus {
    LOADING,
    READY,
    UNAVAILABLE,
}

internal data class MapPreviewEligibility(
    val showMap: Boolean,
    val coordinateValid: Boolean,
    val previewLatitudeSupported: Boolean,
)

internal fun mapPreviewEligibility(
    latitude: Double,
    longitude: Double,
    hasPublicToken: Boolean,
    supportsGles3: Boolean,
): MapPreviewEligibility {
    val coordinateValid = isValidMapCoordinate(latitude, longitude)
    val previewLatitudeSupported = latitude in -85.0511..85.0511
    return MapPreviewEligibility(
        showMap = coordinateValid && previewLatitudeSupported && hasPublicToken && supportsGles3,
        coordinateValid = coordinateValid,
        previewLatitudeSupported = previewLatitudeSupported,
    )
}

internal class ForegroundRenderDeadline(
    private val timeoutMillis: Long = MAP_RENDER_DEADLINE_MILLIS,
) {
    private var elapsedForegroundMillis = 0L
    private var foregroundStartedAt: Long? = null

    fun resume(nowMillis: Long) {
        if (foregroundStartedAt == null) foregroundStartedAt = nowMillis
    }

    fun pause(nowMillis: Long) {
        foregroundStartedAt?.let { startedAt ->
            elapsedForegroundMillis += nowMillis - startedAt
            foregroundStartedAt = null
        }
    }

    fun remainingMillis(nowMillis: Long): Long {
        val currentRun = foregroundStartedAt?.let { nowMillis - it } ?: 0L
        return (timeoutMillis - elapsedForegroundMillis - currentRun).coerceAtLeast(0L)
    }
}

internal data class MapPreviewDependencies(
    val hasPublicToken: Boolean,
    val snapshotClientFactory: () -> MapPreviewSnapshotClient,
    val telemetryPreference: MapTelemetryPreference,
    val supportsGles3: Boolean,
)

@Composable
fun UIKitMapView(
    address: String,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    meetingId: Long? = null,
    onMapClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val token = BuildConfig.MAPBOX_PUBLIC_TOKEN.takeIf(::isSafePublicMapboxToken).orEmpty()
    val dependencies =
        remember(context, token) {
            MapPreviewDependencies(
                hasPublicToken = token.isNotEmpty(),
                snapshotClientFactory = {
                    MapboxMapPreviewSnapshotClient(context, token)
                },
                telemetryPreference = MapboxTelemetryPreference,
                supportsGles3 = deviceSupportsGles3(context),
            )
        }
    UIKitMapViewContent(
        address = address,
        latitude = latitude,
        longitude = longitude,
        modifier = modifier,
        meetingId = meetingId,
        onMapClick = onMapClick,
        dependencies = dependencies,
    )
}

@Composable
internal fun UIKitMapViewContent(
    address: String,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    meetingId: Long? = null,
    onMapClick: () -> Unit = {},
    dependencies: MapPreviewDependencies,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()
    val eligibility =
        mapPreviewEligibility(
            latitude = latitude,
            longitude = longitude,
            hasPublicToken = dependencies.hasPublicToken,
            supportsGles3 = dependencies.supportsGles3,
        )
    val clickLabel = mapActionContentDescription(address, eligibility.coordinateValid)
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var snapshotState by remember {
        mutableStateOf(
            MapPreviewSnapshotUiState(
                if (eligibility.showMap) MapPreviewStatus.LOADING else MapPreviewStatus.UNAVAILABLE,
            ),
        )
    }
    val engine =
        remember {
            MapPreviewSnapshotEngine(dependencies.snapshotClientFactory) { newState ->
                coroutineScope.launch { snapshotState = newState }
            }
        }
    val viewportEligible =
        isSnapshotViewportEligible(
            widthPx = viewportWidthPx,
            heightPx = viewportHeightPx,
            pixelRatio = density,
        )
    val request =
        remember(
            meetingId,
            latitude,
            longitude,
            viewportWidthPx,
            viewportHeightPx,
            density,
            eligibility.showMap,
            viewportEligible,
        ) {
            if (eligibility.showMap && viewportEligible) {
                mapPreviewSnapshotRequest(
                    meetingId = meetingId,
                    latitude = latitude,
                    longitude = longitude,
                    widthPx = viewportWidthPx,
                    heightPx = viewportHeightPx,
                    pixelRatio = density,
                )
            } else {
                null
            }
        }
    val displayStatus =
        when {
            !eligibility.showMap -> MapPreviewStatus.UNAVAILABLE
            viewportWidthPx == 0 || viewportHeightPx == 0 -> MapPreviewStatus.LOADING
            !viewportEligible -> MapPreviewStatus.UNAVAILABLE
            snapshotState.request != request -> MapPreviewStatus.LOADING
            else -> snapshotState.status
        }

    DisposableEffect(engine) {
        onDispose { engine.dispose() }
    }
    val foregroundDeadline = remember(request) { ForegroundRenderDeadline() }
    LaunchedEffect(engine, request, lifecycleOwner) {
        engine.update(request)
        if (request != null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                foregroundDeadline.resume(SystemClock.elapsedRealtime())
                try {
                    while (engine.state.status == MapPreviewStatus.LOADING) {
                        val now = SystemClock.elapsedRealtime()
                        val remaining = foregroundDeadline.remainingMillis(now)
                        if (remaining == 0L) {
                            engine.onDeadline(request)
                            break
                        }
                        delay(min(remaining, 100L))
                    }
                } finally {
                    foregroundDeadline.pause(SystemClock.elapsedRealtime())
                }
            }
        }
    }

    var informationDialogVisible by remember { mutableStateOf(false) }
    var telemetryDialogVisible by remember { mutableStateOf(false) }
    var telemetryState by remember { mutableStateOf(MapTelemetryUiState.UNAVAILABLE) }
    val telemetryController =
        remember {
            MapTelemetryController(dependencies.telemetryPreference) { newState ->
                coroutineScope.launch { telemetryState = newState }
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(BorderRadiusTokens.M))
                .background(MAP_PREVIEW_FALLBACK_COLOR)
                .testTag(MAP_PREVIEW_TEST_TAG),
    ) {
        MapImageViewport(
            modifier = Modifier.fillMaxSize(),
            status = displayStatus,
            bitmap = snapshotState.bitmap.takeIf { snapshotState.request == request },
            eligibility = eligibility,
            clickLabel = clickLabel,
            onMapClick = onMapClick,
            onSizeChanged = { width, height ->
                viewportWidthPx = width
                viewportHeightPx = height
            },
        )
        IconButton(
            onClick = { informationDialogVisible = true },
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .testTag(MAP_PREVIEW_INFO_TEST_TAG),
        ) {
            Box(
                modifier = Modifier.size(28.dp).background(Color.White.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Info,
                    contentDescription = "Информация о карте",
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF172B3A).copy(alpha = 0.8f),
                )
            }
        }
    }

    if (informationDialogVisible) {
        MapInformationDialog(
            onDismiss = { informationDialogVisible = false },
            onOpenTelemetry = {
                informationDialogVisible = false
                telemetryDialogVisible = true
                telemetryController.open()
            },
        )
    }

    if (telemetryDialogVisible) {
        MapTelemetryDialog(
            state = telemetryState,
            onSet = telemetryController::set,
            onDismiss = { telemetryDialogVisible = false },
        )
    }
}

@Composable
private fun MapImageViewport(
    modifier: Modifier,
    status: MapPreviewStatus,
    bitmap: MapPreviewBitmap?,
    eligibility: MapPreviewEligibility,
    clickLabel: String,
    onMapClick: () -> Unit,
    onSizeChanged: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MAP_PREVIEW_FALLBACK_COLOR)
                .onSizeChanged { onSizeChanged(it.width, it.height) }
                .semantics {
                    role = if (eligibility.coordinateValid) Role.Button else Role.Image
                    contentDescription = clickLabel
                }.clickable(enabled = eligibility.coordinateValid, onClick = onMapClick)
                .testTag(MAP_PREVIEW_IMAGE_TEST_TAG),
    ) {
        val readyBitmap = (bitmap as? AndroidMapPreviewBitmap)?.bitmap
        if (status == MapPreviewStatus.READY && readyBitmap != null && !readyBitmap.isRecycled) {
            Image(
                bitmap = readyBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        } else {
            Text(
                text =
                    when {
                        !eligibility.coordinateValid -> "Местоположение недоступно"
                        status == MapPreviewStatus.LOADING -> "Загрузка карты…"
                        else -> "Карта недоступна"
                    },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                        .testTag(MAP_PREVIEW_STATUS_TEST_TAG),
            )
        }

        Image(
            painter = painterResource(com.mapbox.maps.plugin.logo.R.drawable.mapbox_logo_icon),
            contentDescription = "Mapbox logo",
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .height(24.dp)
                    .clickable(role = Role.Button) { launchSafeUrl(context, MAPBOX_URL) }
                    .testTag(MAP_PREVIEW_LOGO_TEST_TAG),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            MapPreviewTopAction(
                enabled = eligibility.coordinateValid,
                onMapClick = onMapClick,
            )
        }
    }
}

@Composable
internal fun MapPreviewTopAction(
    enabled: Boolean = true,
    onMapClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .width(48.dp)
                .height(48.dp)
                .clickable(enabled = enabled, onClick = onMapClick)
                .clearAndSetSemantics {
                    testTag = MAP_PREVIEW_TOP_ACTION_TEST_TAG
                },
    )
}

@Composable
private fun MapInformationDialog(
    onDismiss: () -> Unit,
    onOpenTelemetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Информация о карте") },
        text = {
            Column {
                // Snapshotter renders attribution into the bitmap; these links remain accessible.
                MapLink(Modifier.fillMaxWidth(), "© Mapbox", MAPBOX_URL)
                MapLink(Modifier.fillMaxWidth(), "© OpenStreetMap", OPENSTREETMAP_COPYRIGHT_URL)
                MapLink(Modifier.fillMaxWidth(), "Исправить карту", mapFeedbackUrl())
                MapLink(Modifier.fillMaxWidth(), "Конфиденциальность Mapbox", MAPBOX_PRIVACY_URL)
                TextButton(
                    onClick = onOpenTelemetry,
                    modifier = Modifier.testTag(MAP_PREVIEW_TELEMETRY_TEST_TAG),
                ) {
                    Text("Настройки телеметрии Mapbox")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

@Composable
private fun MapTelemetryDialog(
    state: MapTelemetryUiState,
    onSet: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки телеметрии Mapbox") },
        text = {
            Text(
                when (state) {
                    MapTelemetryUiState.ENABLED -> "Сбор телеметрии включён."
                    MapTelemetryUiState.DISABLED -> "Сбор телеметрии выключен."
                    MapTelemetryUiState.SAVING -> "Сохранение настройки…"
                    MapTelemetryUiState.UNAVAILABLE -> "Настройка телеметрии недоступна."
                },
            )
        },
        confirmButton = {
            TextButton(
                enabled = state == MapTelemetryUiState.ENABLED || state == MapTelemetryUiState.DISABLED,
                onClick = {
                    onSet(state != MapTelemetryUiState.ENABLED)
                },
            ) {
                Text(if (state == MapTelemetryUiState.ENABLED) "Выключить" else "Включить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

@Composable
private fun MapLink(
    modifier: Modifier,
    text: String,
    url: String,
) {
    val context = LocalContext.current
    Text(
        text = text,
        color = Color(0xFF172B3A),
        style = MaterialTheme.typography.labelSmall,
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(role = Role.Button) { launchSafeUrl(context, url) }
                .heightIn(min = 48.dp)
                .padding(horizontal = 6.dp, vertical = 12.dp)
                .semantics { contentDescription = text },
        textAlign = TextAlign.Center,
    )
}

private fun isSafePublicMapboxToken(token: String): Boolean =
    token.startsWith("pk.") && token.matches(Regex("pk\\.[A-Za-z0-9._-]+"))

private fun deviceSupportsGles3(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return activityManager.deviceConfigurationInfo.reqGlEsVersion >= MAP_PREVIEW_GLES3
}

private fun launchSafeUrl(
    context: Context,
    url: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: android.content.ActivityNotFoundException) {
        // External links are optional when the device has no handler.
    }
}

@Preview
@Composable
private fun UIKitMapViewPreview() {
    UIKitTheme {
        UIKitMapView(
            address = "Кожевенная линия, 40",
            latitude = 59.9279,
            longitude = 30.2584,
        )
    }
}
