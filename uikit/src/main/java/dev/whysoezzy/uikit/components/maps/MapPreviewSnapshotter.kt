package dev.whysoezzy.uikit.components.maps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mapbox.common.MapboxOptions
import com.mapbox.common.TelemetryUtils
import com.mapbox.common.TelemetryUtilsResponseCallback
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapSnapshotOptions
import com.mapbox.maps.Size
import com.mapbox.maps.SnapshotOverlayOptions
import com.mapbox.maps.SnapshotStyleListener
import com.mapbox.maps.Snapshotter
import com.mapbox.maps.Style
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal const val MAPBOX_STREETS_V12 = "mapbox://styles/mapbox/streets-v12"
internal const val MAP_PREVIEW_ZOOM = 15.0
internal const val MAP_PREVIEW_BEARING = 0.0
internal const val MAP_PREVIEW_PITCH = 0.0
internal const val MAP_PREVIEW_MIN_WIDTH_DP = 200f
internal const val MAP_PREVIEW_MIN_HEIGHT_DP = 96f

internal data class MapPreviewSnapshotIdentity(
    val meetingId: Long?,
    val latitudeBits: Long,
    val longitudeBits: Long,
)

internal data class MapPreviewSnapshotRequest(
    val identity: MapPreviewSnapshotIdentity,
    val widthPx: Int,
    val heightPx: Int,
    val pixelRatio: Float,
    val latitude: Double,
    val longitude: Double,
    val styleUri: String = MAPBOX_STREETS_V12,
    val zoom: Double = MAP_PREVIEW_ZOOM,
    val bearing: Double = MAP_PREVIEW_BEARING,
    val pitch: Double = MAP_PREVIEW_PITCH,
)

internal fun mapPreviewSnapshotRequest(
    meetingId: Long?,
    latitude: Double,
    longitude: Double,
    widthPx: Int,
    heightPx: Int,
    pixelRatio: Float,
): MapPreviewSnapshotRequest =
    MapPreviewSnapshotRequest(
        identity =
            MapPreviewSnapshotIdentity(
                meetingId = meetingId,
                latitudeBits = latitude.toBits(),
                longitudeBits = longitude.toBits(),
            ),
        widthPx = widthPx,
        heightPx = heightPx,
        pixelRatio = pixelRatio,
        latitude = latitude,
        longitude = longitude,
    )

internal fun isSnapshotViewportEligible(
    widthPx: Int,
    heightPx: Int,
    pixelRatio: Float,
): Boolean =
    pixelRatio.isFinite() &&
        pixelRatio > 0f &&
        widthPx > 0 &&
        heightPx > 0 &&
        widthPx / pixelRatio >= MAP_PREVIEW_MIN_WIDTH_DP &&
        heightPx / pixelRatio >= MAP_PREVIEW_MIN_HEIGHT_DP

internal interface MapPreviewBitmap {
    val storageIdentity: Any
        get() = this
    val widthPx: Int
    val heightPx: Int
    val isRecycled: Boolean

    fun recycle()
}

internal class AndroidMapPreviewBitmap(
    val bitmap: Bitmap,
) : MapPreviewBitmap {
    override val storageIdentity: Any
        get() = bitmap
    override val widthPx: Int
        get() = bitmap.width
    override val heightPx: Int
        get() = bitmap.height
    override val isRecycled: Boolean
        get() = bitmap.isRecycled

    override fun recycle() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

internal data class MapPreviewSnapshotCompletion(
    val bitmap: MapPreviewBitmap?,
    val hasSdkError: Boolean,
    val exactStyleLoaded: Boolean,
    val pinOverlaySucceeded: Boolean,
)

internal fun interface MapPreviewSnapshotHandle {
    fun cancel()
}

internal fun interface MapPreviewSnapshotClient {
    fun start(
        request: MapPreviewSnapshotRequest,
        onComplete: (MapPreviewSnapshotCompletion) -> Unit,
    ): MapPreviewSnapshotHandle
}

internal data class MapPreviewSnapshotUiState(
    val status: MapPreviewStatus,
    val bitmap: MapPreviewBitmap? = null,
    val request: MapPreviewSnapshotRequest? = null,
)

internal class MapPreviewSnapshotEngine(
    private val clientFactory: () -> MapPreviewSnapshotClient,
    private val onStateChanged: (MapPreviewSnapshotUiState) -> Unit,
) {
    private var disposed = false
    private var generation = 0L
    private var currentRequest: MapPreviewSnapshotRequest? = null
    private var currentHandle: CancelOnceHandle? = null
    private var retainedBitmap: MapPreviewBitmap? = null
    private var terminal = true

    @Volatile
    var state: MapPreviewSnapshotUiState =
        MapPreviewSnapshotUiState(MapPreviewStatus.UNAVAILABLE)
        private set

    @Synchronized
    fun update(request: MapPreviewSnapshotRequest?) {
        if (disposed || request == currentRequest) return

        generation += 1
        currentRequest = request
        terminal = request == null
        currentHandle?.cancel()
        currentHandle = null
        retainedBitmap?.recycle()
        retainedBitmap = null

        if (request == null) {
            publish(MapPreviewSnapshotUiState(MapPreviewStatus.UNAVAILABLE))
            return
        }

        publish(MapPreviewSnapshotUiState(MapPreviewStatus.LOADING, request = request))
        val callbackGeneration = generation
        val handle =
            try {
                CancelOnceHandle(
                    clientFactory().start(request) { completion ->
                        onCompletion(callbackGeneration, request, completion)
                    },
                )
            } catch (_: Exception) {
                terminal = true
                publish(MapPreviewSnapshotUiState(MapPreviewStatus.UNAVAILABLE, request = request))
                return
            }

        if (disposed || callbackGeneration != generation || terminal) {
            handle.cancel()
        } else {
            currentHandle = handle
        }
    }

    @Synchronized
    fun onDeadline(request: MapPreviewSnapshotRequest) {
        if (disposed || terminal || request != currentRequest) return
        terminal = true
        currentHandle?.cancel()
        currentHandle = null
        publish(MapPreviewSnapshotUiState(MapPreviewStatus.UNAVAILABLE, request = request))
    }

    @Synchronized
    fun dispose() {
        if (disposed) return
        disposed = true
        terminal = true
        generation += 1
        currentRequest = null
        currentHandle?.cancel()
        currentHandle = null
        retainedBitmap?.recycle()
        retainedBitmap = null
        state = MapPreviewSnapshotUiState(MapPreviewStatus.UNAVAILABLE)
    }

    @Synchronized
    private fun onCompletion(
        callbackGeneration: Long,
        request: MapPreviewSnapshotRequest,
        completion: MapPreviewSnapshotCompletion,
    ) {
        val bitmap = completion.bitmap
        if (disposed || terminal || callbackGeneration != generation || request != currentRequest) {
            if (bitmap != null && bitmap.storageIdentity !== retainedBitmap?.storageIdentity) bitmap.recycle()
            return
        }

        terminal = true
        currentHandle?.cancel()
        currentHandle = null
        val accepted =
            bitmap != null &&
                !completion.hasSdkError &&
                completion.exactStyleLoaded &&
                completion.pinOverlaySucceeded &&
                bitmap.widthPx == request.widthPx &&
                bitmap.heightPx == request.heightPx &&
                !bitmap.isRecycled

        if (accepted) {
            retainedBitmap = bitmap
            publish(MapPreviewSnapshotUiState(MapPreviewStatus.READY, bitmap, request))
        } else {
            bitmap?.recycle()
            publish(MapPreviewSnapshotUiState(MapPreviewStatus.UNAVAILABLE, request = request))
        }
    }

    private fun publish(newState: MapPreviewSnapshotUiState) {
        state = newState
        onStateChanged(newState)
    }

    private class CancelOnceHandle(
        private val delegate: MapPreviewSnapshotHandle,
    ) : MapPreviewSnapshotHandle {
        private val cancelled = AtomicBoolean(false)

        override fun cancel() {
            if (cancelled.compareAndSet(false, true)) delegate.cancel()
        }
    }
}

internal class MapboxMapPreviewSnapshotClient(
    context: Context,
    private val publicToken: String,
) : MapPreviewSnapshotClient {
    private val applicationContext = context.applicationContext

    override fun start(
        request: MapPreviewSnapshotRequest,
        onComplete: (MapPreviewSnapshotCompletion) -> Unit,
    ): MapPreviewSnapshotHandle {
        MapboxOptions.accessToken = publicToken
        val terminal = AtomicBoolean(false)
        val exactStyleLoaded = AtomicBoolean(false)
        val pinOverlaySucceeded = AtomicBoolean(false)
        val snapshotter = AtomicReference<Snapshotter?>()
        val completionLock = Any()
        var deliveredBitmap: MapPreviewBitmap? = null

        fun destroy() {
            snapshotter.getAndSet(null)?.let {
                it.cancel()
                it.destroy()
            }
        }

        fun finish(completion: MapPreviewSnapshotCompletion) {
            synchronized(completionLock) {
                if (terminal.compareAndSet(false, true)) {
                    deliveredBitmap = completion.bitmap
                    try {
                        onComplete(completion)
                    } finally {
                        destroy()
                    }
                } else {
                    val bitmap = completion.bitmap
                    if (bitmap != null && bitmap.storageIdentity !== deliveredBitmap?.storageIdentity) bitmap.recycle()
                }
            }
        }

        try {
            val options =
                MapSnapshotOptions
                    .Builder()
                    .size(Size(request.widthPx.toFloat(), request.heightPx.toFloat()))
                    .pixelRatio(request.pixelRatio)
                    .build()
            val createdSnapshotter =
                Snapshotter(
                    applicationContext,
                    options,
                    SnapshotOverlayOptions(showLogo = false, showAttributes = false),
                )
            snapshotter.set(createdSnapshotter)
            createdSnapshotter.setStyleListener(
                object : SnapshotStyleListener {
                    override fun onDidFinishLoadingStyle(style: Style) = Unit

                    override fun onDidFullyLoadStyle(style: Style) {
                        exactStyleLoaded.set(
                            runCatching { style.styleURI == request.styleUri }.getOrDefault(false),
                        )
                    }

                    override fun onDidFailLoadingStyle(message: String) {
                        finish(
                            MapPreviewSnapshotCompletion(
                                bitmap = null,
                                hasSdkError = true,
                                exactStyleLoaded = false,
                                pinOverlaySucceeded = false,
                            ),
                        )
                    }

                    override fun onStyleImageMissing(imageId: String) = Unit
                },
            )
            createdSnapshotter.setCamera(
                CameraOptions
                    .Builder()
                    .center(Point.fromLngLat(request.longitude, request.latitude))
                    .zoom(request.zoom)
                    .bearing(request.bearing)
                    .pitch(request.pitch)
                    .build(),
            )
            createdSnapshotter.setStyleUri(request.styleUri)
            createdSnapshotter.start(
                { overlay ->
                    try {
                        val pinBitmap = createLocationPinBitmap(applicationContext)
                        val coordinate =
                            overlay.screenCoordinate(
                                Point.fromLngLat(request.longitude, request.latitude),
                            )
                        val inBounds =
                            coordinate.x.isFinite() &&
                                coordinate.y.isFinite() &&
                                coordinate.x >= 0.0 &&
                                coordinate.x < request.widthPx &&
                                coordinate.y >= 0.0 &&
                                coordinate.y < request.heightPx
                        try {
                            if (inBounds) {
                                overlay.canvas.drawBitmap(
                                    pinBitmap,
                                    coordinate.x.toFloat() - pinBitmap.width / 2f,
                                    coordinate.y.toFloat() - pinBitmap.height,
                                    null,
                                )
                            }
                            pinOverlaySucceeded.set(inBounds)
                        } finally {
                            pinBitmap.recycle()
                        }
                    } catch (_: Exception) {
                        pinOverlaySucceeded.set(false)
                    }
                },
            ) { bitmap, error ->
                finish(
                    MapPreviewSnapshotCompletion(
                        bitmap = bitmap?.let(::AndroidMapPreviewBitmap),
                        hasSdkError = error != null,
                        exactStyleLoaded = exactStyleLoaded.get(),
                        pinOverlaySucceeded = pinOverlaySucceeded.get(),
                    ),
                )
            }
        } catch (_: Exception) {
            finish(
                MapPreviewSnapshotCompletion(
                    bitmap = null,
                    hasSdkError = true,
                    exactStyleLoaded = false,
                    pinOverlaySucceeded = false,
                ),
            )
        }

        return MapPreviewSnapshotHandle {
            if (terminal.compareAndSet(false, true)) destroy()
        }
    }
}

internal interface MapTelemetryPreference {
    fun read(): Result<Boolean>

    fun set(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
    )
}

@SuppressLint("RestrictedApi")
internal object MapboxTelemetryPreference : MapTelemetryPreference {
    override fun read(): Result<Boolean> =
        runCatching { TelemetryUtils.getEventsCollectionState() }

    override fun set(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        val terminal = AtomicBoolean(false)
        val finish: (Boolean) -> Unit = { succeeded ->
            if (terminal.compareAndSet(false, true)) onComplete(succeeded)
        }
        runCatching {
            TelemetryUtils.setEventsCollectionState(
                enabled,
                TelemetryUtilsResponseCallback { result ->
                    val matchesReadback =
                        result.isValue &&
                            runCatching { TelemetryUtils.getEventsCollectionState() }
                                .getOrNull() == enabled
                    finish(matchesReadback)
                },
            )
        }.onFailure {
            finish(false)
        }
    }
}

internal enum class MapTelemetryUiState {
    ENABLED,
    DISABLED,
    SAVING,
    UNAVAILABLE,
}

internal class MapTelemetryController(
    private val preference: MapTelemetryPreference,
    private val onStateChanged: (MapTelemetryUiState) -> Unit,
) {
    @Volatile
    var state: MapTelemetryUiState = MapTelemetryUiState.UNAVAILABLE
        private set

    @Synchronized
    fun open() {
        if (state != MapTelemetryUiState.SAVING) refresh()
    }

    @Synchronized
    private fun refresh() {
        publish(
            preference
                .read()
                .fold(
                    onSuccess = { if (it) MapTelemetryUiState.ENABLED else MapTelemetryUiState.DISABLED },
                    onFailure = { MapTelemetryUiState.UNAVAILABLE },
                ),
        )
    }

    @Synchronized
    fun set(enabled: Boolean) {
        if (state != MapTelemetryUiState.ENABLED && state != MapTelemetryUiState.DISABLED) return
        publish(MapTelemetryUiState.SAVING)
        preference.set(enabled) { succeeded ->
            if (!succeeded) {
                publish(MapTelemetryUiState.UNAVAILABLE)
                return@set
            }
            refresh()
        }
    }

    @Synchronized
    private fun publish(newState: MapTelemetryUiState) {
        state = newState
        onStateChanged(newState)
    }
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
            cubicTo(
                centerX - radius * 0.55f,
                height * 0.54f,
                centerX - radius,
                height * 0.45f,
                centerX - radius,
                radius,
            )
            cubicTo(
                centerX - radius,
                -radius * 0.3f,
                centerX + radius,
                -radius * 0.3f,
                centerX + radius,
                radius,
            )
            cubicTo(
                centerX + radius,
                height * 0.45f,
                centerX + radius * 0.55f,
                height * 0.54f,
                centerX,
                height.toFloat(),
            )
        }
    canvas.drawPath(path, paint)
    paint.color = Color.White.toArgb()
    canvas.drawCircle(centerX, radius, radius * 0.38f, paint)
    return bitmap
}
