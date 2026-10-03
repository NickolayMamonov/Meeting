package dev.whysoezzy.uikit.components.maps

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapState
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.annotation.AnnotationPlugin
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.GesturesPlugin
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import com.whysoezzy.common.utils.isValidMapCoordinate
import dev.whysoezzy.uikit.BuildConfig
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import kotlinx.coroutines.delay
import kotlin.math.min

private const val MAPBOX_STREETS_V12 = "mapbox://styles/mapbox/streets-v12"
private const val MAP_PREVIEW_ZOOM = 15.0
private const val MAP_RENDER_DEADLINE_MILLIS = 15_000L
private const val MAP_PREVIEW_TEST_TAG = "uikit-map-preview"
internal const val MAP_PREVIEW_STATUS_TEST_TAG = "uikit-map-preview-status"
private const val MAP_PREVIEW_GLES3 = 0x00030000
private const val MAPBOX_PRIVACY_URL = "https://www.mapbox.com/legal/privacy/"
private const val OPENSTREETMAP_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
private const val MAPBOX_FEEDBACK_URL = "https://apps.mapbox.com/feedback/"

internal fun mapFeedbackUrl(
    longitude: Double,
    latitude: Double,
    zoom: Double,
): String = "$MAPBOX_FEEDBACK_URL#/$longitude/$latitude/$zoom"

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

private data class MapPreviewAttemptKey(
    val meetingId: Long?,
    val latitudeBits: Long,
    val longitudeBits: Long,
    val styleUri: String,
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

internal class MapPreviewReadiness {
    var status: MapPreviewStatus = MapPreviewStatus.LOADING
        private set
    private var styleLoaded = false
    private var pinInstalled = false
    private var mapLoaded = false
    private var frameFinishedAfterPin = false

    fun onStyleLoaded() {
        if (status == MapPreviewStatus.LOADING) styleLoaded = true
        updateReady()
    }

    fun onPinInstalled() {
        if (status == MapPreviewStatus.LOADING) pinInstalled = true
        updateReady()
    }

    fun onMapLoaded() {
        if (status == MapPreviewStatus.LOADING) mapLoaded = true
        updateReady()
    }

    fun onFrameFinished() {
        if (status == MapPreviewStatus.LOADING && pinInstalled && mapLoaded) {
            frameFinishedAfterPin = true
        }
        updateReady()
    }

    fun onFailure() {
        if (status == MapPreviewStatus.LOADING || status == MapPreviewStatus.READY) {
            status = MapPreviewStatus.UNAVAILABLE
        }
    }

    fun onDeadline() {
        if (status == MapPreviewStatus.LOADING) status = MapPreviewStatus.UNAVAILABLE
    }

    fun dispose() {
        status = MapPreviewStatus.UNAVAILABLE
    }

    private fun updateReady() {
        if (status == MapPreviewStatus.LOADING &&
            styleLoaded &&
            pinInstalled &&
            mapLoaded &&
            frameFinishedAfterPin
        ) {
            status = MapPreviewStatus.READY
        }
    }
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
    val lifeCycleOwner = LocalLifecycleOwner.current
    val token = BuildConfig.MAPBOX_PUBLIC_TOKEN.takeIf(::isSafePublicMapboxToken).orEmpty()
    val supportsGles3 = remember(context) { deviceSupportsGles3(context) }
    val eligibility = mapPreviewEligibility(latitude, longitude, token.isNotEmpty(), supportsGles3)
    val displayAddress = address.ifBlank { "Открыть в картах" }
    val attemptKey =
        remember(meetingId, latitude, longitude) {
            MapPreviewAttemptKey(
                meetingId = meetingId,
                latitudeBits = latitude.toBits(),
                longitudeBits = longitude.toBits(),
                styleUri = MAPBOX_STREETS_V12,
            )
        }
    val readiness = remember(attemptKey) { MapPreviewReadiness() }
    var status by remember(attemptKey) { mutableStateOf(MapPreviewStatus.LOADING) }
    val foregroundDeadline = remember(attemptKey) { ForegroundRenderDeadline() }

    DisposableEffect(attemptKey, readiness) {
        onDispose { readiness.dispose() }
    }

    LaunchedEffect(attemptKey, readiness, eligibility.showMap) {
        if (!eligibility.showMap) return@LaunchedEffect
        lifeCycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            foregroundDeadline.resume(SystemClock.elapsedRealtime())
            try {
                while (readiness.status == MapPreviewStatus.LOADING) {
                    val now = SystemClock.elapsedRealtime()
                    val remaining = foregroundDeadline.remainingMillis(now)
                    if (remaining == 0L) {
                        readiness.onDeadline()
                        status = readiness.status
                        break
                    }
                    delay(min(remaining, 100L))
                    status = readiness.status
                }
            } finally {
                foregroundDeadline.pause(SystemClock.elapsedRealtime())
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(BorderRadiusTokens.M))
                    .background(Color(0xFFE6ECF0))
                    .testTag(MAP_PREVIEW_TEST_TAG),
        ) {
            if (eligibility.showMap) {
                MapboxOptions.accessToken = token
                MapPreviewMap(
                    attemptKey = attemptKey,
                    latitude = latitude,
                    longitude = longitude,
                    status = status,
                    readiness = readiness,
                    onStatusChanged = { status = it },
                    onMapClick = onMapClick,
                    clickLabel =
                        if (eligibility.coordinateValid) {
                            "Открыть в картах: $displayAddress"
                        } else {
                            "Открыть в картах"
                        },
                )
            } else {
                NoMapFallback(
                    modifier = Modifier.fillMaxSize(),
                    coordinateValid = eligibility.coordinateValid,
                    previewLatitudeSupported = eligibility.previewLatitudeSupported,
                    onMapClick = onMapClick,
                    clickLabel =
                        if (eligibility.coordinateValid) {
                            "Открыть в картах: $displayAddress"
                        } else {
                            "Открыть в картах"
                        },
                )
            }
        }
    }
}

@Composable
private fun MapPreviewMap(
    attemptKey: MapPreviewAttemptKey,
    latitude: Double,
    longitude: Double,
    status: MapPreviewStatus,
    readiness: MapPreviewReadiness,
    onStatusChanged: (MapPreviewStatus) -> Unit,
    onMapClick: () -> Unit,
    clickLabel: String,
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        key(attemptKey) {
            val mapViewportState = rememberMapViewportState {
                setCameraOptions {
                    center(Point.fromLngLat(longitude, latitude))
                    zoom(MAP_PREVIEW_ZOOM)
                    bearing(0.0)
                    pitch(0.0)
                }
            }
            val mapState = rememberMapStateWithDisabledGestures()

            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapState = mapState,
                mapViewportState = mapViewportState,
                compass = {},
                scaleBar = {},
                logo = {
                    Logo(
                        modifier =
                            Modifier
                                .clickable(role = Role.Button) { launchSafeUrl(context, "https://www.mapbox.com/") }
                                .semantics { contentDescription = "Mapbox logo" },
                        alignment = Alignment.TopStart,
                    )
                },
                attribution = { Attribution(alignment = Alignment.TopEnd) },
            ) {
                MapEffect(attemptKey) { mapView ->
                    val subscriptions = mutableListOf<com.mapbox.common.Cancelable>()
                    var pointAnnotationManager: com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager? = null
                    try {
                        val map = mapView.mapboxMap
                        subscriptions += map.subscribeStyleLoaded {
                            if (readiness.status != MapPreviewStatus.LOADING) return@subscribeStyleLoaded
                            readiness.onStyleLoaded()
                            try {
                                val plugin =
                                    requireNotNull(mapView.getPlugin<AnnotationPlugin>(Plugin.MAPBOX_ANNOTATION_PLUGIN_ID))
                                val manager = plugin.createPointAnnotationManager()
                                pointAnnotationManager = manager
                                manager.create(
                                    PointAnnotationOptions()
                                        .withPoint(Point.fromLngLat(longitude, latitude))
                                        .withIconImage(createLocationPinBitmap(mapView.context))
                                        .withIconAnchor(IconAnchor.BOTTOM),
                                )
                                readiness.onPinInstalled()
                                onStatusChanged(readiness.status)
                            } catch (_: Exception) {
                                readiness.onFailure()
                                onStatusChanged(readiness.status)
                            }
                        }
                        subscriptions += map.subscribeMapLoaded {
                            readiness.onMapLoaded()
                            onStatusChanged(readiness.status)
                        }
                        subscriptions += map.subscribeRenderFrameFinished {
                            readiness.onFrameFinished()
                            onStatusChanged(readiness.status)
                        }
                        subscriptions += map.subscribeMapLoadingError {
                            readiness.onFailure()
                            onStatusChanged(readiness.status)
                        }
                        requireNotNull(mapView.getPlugin<GesturesPlugin>(Plugin.MAPBOX_GESTURES_PLUGIN_ID)).updateSettings {
                            rotateEnabled = false
                            pinchToZoomEnabled = false
                            scrollEnabled = false
                            simultaneousRotateAndPinchToZoomEnabled = false
                            pitchEnabled = false
                            doubleTapToZoomInEnabled = false
                            doubleTouchToZoomOutEnabled = false
                            quickZoomEnabled = false
                            pinchToZoomDecelerationEnabled = false
                            rotateDecelerationEnabled = false
                            scrollDecelerationEnabled = false
                            pinchScrollEnabled = false
                        }
                    } catch (_: Exception) {
                        readiness.onFailure()
                        onStatusChanged(readiness.status)
                    }

                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        subscriptions.forEach { it.cancel() }
                        pointAnnotationManager?.let {
                            requireNotNull(mapView.getPlugin<AnnotationPlugin>(Plugin.MAPBOX_ANNOTATION_PLUGIN_ID))
                                .removeAnnotationManager(it)
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.weight(1f))
                Box(Modifier.weight(1f))
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(if (status == MapPreviewStatus.READY) Color.Transparent else Color(0xFFF3F5F7))
                        .semantics {
                            role = Role.Button
                            contentDescription = clickLabel
                        }.clickable(onClick = onMapClick),
                contentAlignment = Alignment.Center,
            ) {
                if (status != MapPreviewStatus.READY) {
                    Text(
                        text = if (status == MapPreviewStatus.LOADING) "Загрузка карты…" else "Карта недоступна",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp).testTag(MAP_PREVIEW_STATUS_TEST_TAG),
                    )
                }
            }
            MapCredits(feedbackUrl = mapFeedbackUrl(longitude, latitude, MAP_PREVIEW_ZOOM))
        }
    }
}

@Composable
private fun rememberMapStateWithDisabledGestures(): MapState =
    rememberMapState(MAPBOX_STREETS_V12) {
        gesturesState.gesturesSettings =
            GesturesSettings
                .Builder()
                .apply {
                    rotateEnabled = false
                    pinchToZoomEnabled = false
                    scrollEnabled = false
                    simultaneousRotateAndPinchToZoomEnabled = false
                    pitchEnabled = false
                    doubleTapToZoomInEnabled = false
                    doubleTouchToZoomOutEnabled = false
                    quickZoomEnabled = false
                    pinchToZoomDecelerationEnabled = false
                    rotateDecelerationEnabled = false
                    scrollDecelerationEnabled = false
                    pinchScrollEnabled = false
                }.build()
    }

@Composable
private fun NoMapFallback(
    modifier: Modifier,
    coordinateValid: Boolean,
    previewLatitudeSupported: Boolean,
    onMapClick: () -> Unit,
    clickLabel: String,
) {
    val context = LocalContext.current
    Box(modifier.background(Color(0xFFF3F5F7))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .semantics {
                            role = if (coordinateValid) Role.Button else Role.Image
                            contentDescription = clickLabel
                        }.clickable(enabled = coordinateValid, onClick = onMapClick),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(com.mapbox.maps.plugin.logo.R.drawable.mapbox_logo_icon),
                    contentDescription = "Mapbox logo",
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .height(24.dp)
                            .clickable(role = Role.Button) {
                                launchSafeUrl(context, "https://www.mapbox.com/")
                            },
                )
                Text(
                    text =
                        when {
                            !coordinateValid -> "Местоположение недоступно"
                            !previewLatitudeSupported -> "Карта недоступна"
                            else -> "Карта недоступна"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            MapCredits(feedbackUrl = MAPBOX_FEEDBACK_URL)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapCredits(feedbackUrl: String) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.94f)),
    ) {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.Center,
            maxItemsInEachRow = 2,
        ) {
            MapLink("© Mapbox", "https://www.mapbox.com/")
            MapLink("© OpenStreetMap", OPENSTREETMAP_COPYRIGHT_URL)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapLink(text = "Mapbox privacy information", url = MAPBOX_PRIVACY_URL)
            MapLink("Improve this map", feedbackUrl)
        }
    }
}

@Composable
private fun MapLink(
    text: String,
    url: String,
) {
    val context = LocalContext.current
    Text(
        text = text,
        color = Color(0xFF172B3A),
        style = MaterialTheme.typography.labelSmall,
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(role = Role.Button) { launchSafeUrl(context, url) }
                .heightIn(min = 48.dp)
                .padding(horizontal = 6.dp)
                .semantics { contentDescription = text },
    )
}

private fun isSafePublicMapboxToken(token: String): Boolean =
    token.startsWith("pk.") && token.matches(Regex("pk\\.[A-Za-z0-9._-]+"))

private fun deviceSupportsGles3(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return activityManager.deviceConfigurationInfo.reqGlEsVersion >= MAP_PREVIEW_GLES3
}

private fun createLocationPinBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = (32 * density).toInt().coerceAtLeast(32)
    val height = (42 * density).toInt().coerceAtLeast(42)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color(0xFFB3261E).toArgb() }
    val centerX = width / 2f
    val radius = width * 0.42f
    val path =
        Path().apply {
            moveTo(centerX, height.toFloat())
            cubicTo(centerX - radius * 0.55f, height * 0.54f, centerX - radius, height * 0.45f, centerX - radius, radius)
            cubicTo(centerX - radius, -radius * 0.3f, centerX + radius, -radius * 0.3f, centerX + radius, radius)
            cubicTo(centerX + radius, height * 0.45f, centerX + radius * 0.55f, height * 0.54f, centerX, height.toFloat())
        }
    canvas.drawPath(path, paint)
    paint.color = Color.White.toArgb()
    canvas.drawCircle(centerX, radius, radius * 0.38f, paint)
    return bitmap
}

private fun launchSafeUrl(
    context: Context,
    url: String,
) {
    try {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    } catch (_: android.content.ActivityNotFoundException) {
        // Opening an external link is optional when the device has no handler.
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
