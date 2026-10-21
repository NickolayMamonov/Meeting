package dev.whysoezzy.uikit.components.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MapPreviewSnapshotterTest {
    @Test
    fun `request maps platform pixels density Streets and fixed camera exactly`() {
        val request =
            mapPreviewSnapshotRequest(
                meetingId = 42L,
                latitude = 55.7558,
                longitude = 37.6176,
                widthPx = 640,
                heightPx = 256,
                pixelRatio = 2f,
            )

        assertEquals(42L, request.identity.meetingId)
        assertEquals(55.7558.toBits(), request.identity.latitudeBits)
        assertEquals(37.6176.toBits(), request.identity.longitudeBits)
        assertEquals(640, request.widthPx)
        assertEquals(256, request.heightPx)
        assertEquals(2f, request.pixelRatio)
        assertEquals(MAPBOX_STREETS_V12, request.styleUri)
        assertEquals(15.0, request.zoom, 0.0)
        assertEquals(0.0, request.bearing, 0.0)
        assertEquals(0.0, request.pitch, 0.0)
    }

    @Test
    fun `successful current result becomes ready and remains owned until replacement`() {
        val client = FakeSnapshotClient()
        val states = mutableListOf<MapPreviewSnapshotUiState>()
        val engine = MapPreviewSnapshotEngine({ client }, states::add)
        val request = request()
        val bitmap = FakeBitmap(request.widthPx, request.heightPx)

        engine.update(request)
        client.complete(success(bitmap))

        assertEquals(MapPreviewStatus.READY, engine.state.status)
        assertSame(bitmap, engine.state.bitmap)
        assertFalse(bitmap.isRecycled)
        assertEquals(1, client.cancelCount)

        engine.update(request.copy(widthPx = request.widthPx + 1))

        assertTrue(bitmap.isRecycled)
        assertEquals(MapPreviewStatus.LOADING, engine.state.status)
        assertEquals(2, client.startCount)
    }

    @Test
    fun `null error dimensions style and pin failures are unavailable and recycle results`() {
        val completions =
            listOf(
                MapPreviewSnapshotCompletion(null, false, true, true),
                MapPreviewSnapshotCompletion(FakeBitmap(640, 256), true, true, true),
                MapPreviewSnapshotCompletion(FakeBitmap(637, 256), false, true, true),
                MapPreviewSnapshotCompletion(FakeBitmap(640, 256), false, false, true),
                MapPreviewSnapshotCompletion(FakeBitmap(640, 256), false, true, false),
            )

        completions.forEach { completion ->
            val client = FakeSnapshotClient()
            val engine = MapPreviewSnapshotEngine({ client }) {}
            engine.update(request())
            client.complete(completion)

            assertEquals(MapPreviewStatus.UNAVAILABLE, engine.state.status)
            assertTrue(completion.bitmap == null || completion.bitmap.isRecycled)
        }
    }

    @Test
    fun `factory and start exceptions retain failed request as unavailable`() {
        val request = request()
        val factories =
            listOf<() -> MapPreviewSnapshotClient>(
                { throw IllegalStateException() },
                {
                    object : MapPreviewSnapshotClient {
                        override fun start(
                            request: MapPreviewSnapshotRequest,
                            onComplete: (MapPreviewSnapshotCompletion) -> Unit,
                        ): MapPreviewSnapshotHandle = throw IllegalStateException()
                    }
                },
            )

        factories.forEach { factory ->
            val engine = MapPreviewSnapshotEngine(factory) {}

            engine.update(request)

            assertEquals(MapPreviewStatus.UNAVAILABLE, engine.state.status)
            assertEquals(request, engine.state.request)
        }
    }

    @Test
    fun `same request and status observations do not restart while genuine replacements cancel once`() {
        val client = FakeSnapshotClient()
        val engine = MapPreviewSnapshotEngine({ client }) {}
        val first = request()

        engine.update(first)
        val firstCallback = client.callbacks.single()
        engine.update(first)
        engine.update(first.copy())
        assertEquals(1, client.startCount)
        assertEquals(0, client.cancelCount)

        engine.update(first.copy(pixelRatio = 3f))
        assertEquals(2, client.startCount)
        assertEquals(1, client.cancelCount)
        val replacedLate = FakeBitmap(first.widthPx, first.heightPx)
        firstCallback(success(replacedLate))
        assertTrue(replacedLate.isRecycled)
        assertEquals(MapPreviewStatus.LOADING, engine.state.status)

        engine.update(first.copy(latitude = 56.0))
        assertEquals(3, client.startCount)
        assertEquals(2, client.cancelCount)

        engine.update(first.copy(identity = first.identity.copy(meetingId = 2L)))
        assertEquals(4, client.startCount)
        assertEquals(3, client.cancelCount)
    }

    @Test
    fun `silent client expires at fifteen foreground seconds without a completion callback`() {
        val client = FakeSnapshotClient()
        val engine = MapPreviewSnapshotEngine({ client }) {}
        val request = request()
        val deadline = ForegroundRenderDeadline()
        engine.update(request)
        deadline.resume(0L)
        assertEquals(1L, deadline.remainingMillis(14_999L))
        assertEquals(MapPreviewStatus.LOADING, engine.state.status)
        assertEquals(0L, deadline.remainingMillis(15_000L))
        engine.onDeadline(request)
        assertEquals(MapPreviewStatus.UNAVAILABLE, engine.state.status)
        assertEquals(1, client.cancelCount)
        assertEquals(1, client.callbacks.size)
        engine.onDeadline(request)
        assertEquals(1, client.cancelCount)
    }

    @Test
    fun `deadline disposal duplicates and old generation callbacks are terminal and recycled`() {
        val client = FakeSnapshotClient()
        val engine = MapPreviewSnapshotEngine({ client }) {}
        val first = request()
        engine.update(first)
        val firstCallback = client.callbacks.single()

        engine.onDeadline(first)
        assertEquals(MapPreviewStatus.UNAVAILABLE, engine.state.status)
        assertEquals(1, client.cancelCount)
        val late = FakeBitmap(first.widthPx, first.heightPx)
        firstCallback(success(late))
        assertTrue(late.isRecycled)

        val second = first.copy(identity = first.identity.copy(meetingId = 2L))
        engine.update(second)
        val secondCallback = client.callbacks.last()
        engine.dispose()
        val disposed = FakeBitmap(second.widthPx, second.heightPx)
        secondCallback(success(disposed))
        assertTrue(disposed.isRecycled)
        assertEquals(2, client.cancelCount)
    }

    @Test
    fun `duplicate completion recycles only the duplicate bitmap`() {
        val client = FakeSnapshotClient()
        val engine = MapPreviewSnapshotEngine({ client }) {}
        val request = request()
        val retained = FakeBitmap(request.widthPx, request.heightPx)
        val duplicate = FakeBitmap(request.widthPx, request.heightPx)

        engine.update(request)
        client.complete(success(retained))
        client.complete(success(duplicate))

        assertSame(retained, engine.state.bitmap)
        assertFalse(retained.isRecycled)
        assertTrue(duplicate.isRecycled)
        assertEquals(1, client.cancelCount)
    }

    @Test
    fun `duplicate callback cannot recycle the currently displayed bitmap`() {
        val client = FakeSnapshotClient()
        val engine = MapPreviewSnapshotEngine({ client }) {}
        val request = request()
        val bitmap = FakeBitmap(request.widthPx, request.heightPx)
        val completion = success(bitmap)

        engine.update(request)
        client.complete(completion)
        client.complete(completion)

        assertEquals(MapPreviewStatus.READY, engine.state.status)
        assertSame(bitmap, engine.state.bitmap)
        assertFalse(bitmap.isRecycled)
        assertEquals(1, client.cancelCount)
    }

    @Test
    fun `distinct wrappers sharing retained storage cannot recycle displayed bitmap`() {
        val client = FakeSnapshotClient()
        val engine = MapPreviewSnapshotEngine({ client }) {}
        val request = request()
        val bitmap = FakeBitmap(request.widthPx, request.heightPx)
        val wrapper = object : MapPreviewBitmap by bitmap {}

        engine.update(request)
        client.complete(success(bitmap))
        client.complete(success(wrapper))

        assertEquals(MapPreviewStatus.READY, engine.state.status)
        assertSame(bitmap, engine.state.bitmap)
        assertFalse(bitmap.isRecycled)
    }

    @Test
    fun `completion racing the deadline has one terminal owner`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val client = FakeSnapshotClient()
                val states = mutableListOf<MapPreviewSnapshotUiState>()
                val engine = MapPreviewSnapshotEngine({ client }, states::add)
                val request = request()
                val bitmap = FakeBitmap(request.widthPx, request.heightPx)
                val start = CountDownLatch(1)
                engine.update(request)
                val completion =
                    executor.submit {
                        check(start.await(5, TimeUnit.SECONDS))
                        client.complete(success(bitmap))
                    }
                val deadline =
                    executor.submit {
                        check(start.await(5, TimeUnit.SECONDS))
                        engine.onDeadline(request)
                    }
                start.countDown()
                completion.get(5, TimeUnit.SECONDS)
                deadline.get(5, TimeUnit.SECONDS)

                assertEquals(2, states.size)
                assertEquals(1, client.cancelCount)
                if (engine.state.status == MapPreviewStatus.READY) {
                    assertSame(bitmap, engine.state.bitmap)
                    assertFalse(bitmap.isRecycled)
                } else {
                    assertEquals(MapPreviewStatus.UNAVAILABLE, engine.state.status)
                    assertTrue(bitmap.isRecycled)
                }
                engine.dispose()
                assertTrue(bitmap.isRecycled)
                assertEquals(1, client.cancelCount)
            }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `telemetry reads toggles verifies readback and exposes fixed failure state`() {
        val preference = FakeTelemetryPreference(initial = true)
        val states = mutableListOf<MapTelemetryUiState>()
        val controller = MapTelemetryController(preference, states::add)

        controller.open()
        assertEquals(MapTelemetryUiState.ENABLED, controller.state)

        controller.set(false)
        assertEquals(MapTelemetryUiState.DISABLED, controller.state)
        assertEquals(listOf(false), preference.requestedStates)

        controller.set(true)
        assertEquals(MapTelemetryUiState.ENABLED, controller.state)
        assertEquals(listOf(false, true), preference.requestedStates)

        preference.failSet = true
        controller.set(false)
        assertEquals(MapTelemetryUiState.UNAVAILABLE, controller.state)

        preference.failRead = true
        controller.open()
        assertEquals(MapTelemetryUiState.UNAVAILABLE, controller.state)
    }

    @Test
    fun `telemetry set from unavailable state does not invoke preference`() {
        val preference = FakeTelemetryPreference(initial = true).apply { failRead = true }
        val controller = MapTelemetryController(preference) {}

        controller.open()
        assertEquals(MapTelemetryUiState.UNAVAILABLE, controller.state)

        controller.set(false)

        assertEquals(emptyList<Boolean>(), preference.requestedStates)
        assertEquals(MapTelemetryUiState.UNAVAILABLE, controller.state)
    }

    @Test
    fun `reopening telemetry while saving cannot allow a second write`() {
        var readCount = 0
        val writes = mutableListOf<Boolean>()
        val preference =
            object : MapTelemetryPreference {
                override fun read(): Result<Boolean> {
                    readCount += 1
                    return Result.success(true)
                }

                override fun set(
                    enabled: Boolean,
                    onComplete: (Boolean) -> Unit,
                ) {
                    writes += enabled
                }
            }
        val controller = MapTelemetryController(preference) {}
        controller.open()
        controller.set(false)

        controller.open()
        controller.set(true)

        assertEquals(MapTelemetryUiState.SAVING, controller.state)
        assertEquals(1, readCount)
        assertEquals(listOf(false), writes)
    }

    @Test
    fun `bounded undershoot retains original bitmap until disposed once`() {
        val fractional = request().copy(widthPx = 954, heightPx = 252, pixelRatio = 2.625f)
        val cases =
            listOf(
                Triple(request(), 639, 256),
                Triple(request(), 638, 254),
                Triple(request().copy(widthPx = 986, heightPx = 192), 986, 192),
                Triple(fractional, 952, 252),
                Triple(fractional, 954, 250),
                Triple(fractional, 952, 250),
            )
        cases.forEach { (request, width, height) ->
            val client = FakeSnapshotClient()
            val engine = MapPreviewSnapshotEngine({ client }) {}
            val bitmap = FakeBitmap(width, height)
            engine.update(request)
            client.complete(success(bitmap))

            assertEquals(MapPreviewStatus.READY, engine.state.status)
            assertSame(bitmap, engine.state.bitmap)
            assertEquals(0, bitmap.recycleCount)
            engine.dispose()
            engine.dispose()
            assertEquals(1, bitmap.recycleCount)
        }
    }

    @Test
    fun `invalid dimensions ratios oversize and excessive per axis gaps are rejected once`() {
        val fractional = request().copy(widthPx = 954, heightPx = 252, pixelRatio = 2.625f)
        val cases =
            listOf(
                Triple(fractional, 951, 252),
                Triple(fractional, 954, 249),
                Triple(fractional, 955, 252),
                Triple(fractional, 954, 253),
                Triple(fractional, 640, 200),
                Triple(fractional, 0, 252),
                Triple(fractional, 954, 0),
                Triple(fractional, -1, 252),
                Triple(fractional, 954, -1),
                Triple(fractional.copy(widthPx = 0), 1, 252),
                Triple(fractional.copy(widthPx = -1), 1, 252),
                Triple(fractional.copy(heightPx = 0), 954, 1),
                Triple(fractional.copy(heightPx = -1), 954, 1),
            ) +
                listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).map {
                    Triple(fractional.copy(pixelRatio = it), 954, 252)
                }
        cases.forEach { (request, width, height) ->
            val client = FakeSnapshotClient()
            val engine = MapPreviewSnapshotEngine({ client }) {}
            val bitmap = FakeBitmap(width, height)
            engine.update(request)
            client.complete(success(bitmap))

            assertEquals(MapPreviewStatus.UNAVAILABLE, engine.state.status)
            assertEquals(1, bitmap.recycleCount)
            engine.dispose()
            assertEquals(1, bitmap.recycleCount)
        }
    }

    private fun request(): MapPreviewSnapshotRequest =
        mapPreviewSnapshotRequest(
            meetingId = 1L,
            latitude = 55.7558,
            longitude = 37.6176,
            widthPx = 640,
            heightPx = 256,
            pixelRatio = 2f,
        )

    private fun success(bitmap: MapPreviewBitmap): MapPreviewSnapshotCompletion =
        MapPreviewSnapshotCompletion(
            bitmap = bitmap,
            hasSdkError = false,
            exactStyleLoaded = true,
            pinOverlaySucceeded = true,
        )

    private class FakeSnapshotClient : MapPreviewSnapshotClient {
        var startCount = 0
        var cancelCount = 0
        val callbacks = mutableListOf<(MapPreviewSnapshotCompletion) -> Unit>()

        override fun start(
            request: MapPreviewSnapshotRequest,
            onComplete: (MapPreviewSnapshotCompletion) -> Unit,
        ): MapPreviewSnapshotHandle {
            startCount += 1
            callbacks += onComplete
            return MapPreviewSnapshotHandle { cancelCount += 1 }
        }

        fun complete(completion: MapPreviewSnapshotCompletion) {
            callbacks.last()(completion)
        }
    }

    private class FakeBitmap(
        override val widthPx: Int,
        override val heightPx: Int,
    ) : MapPreviewBitmap {
        var recycleCount: Int = 0
            private set
        override var isRecycled: Boolean = false
            private set

        override fun recycle() {
            recycleCount += 1
            isRecycled = true
        }
    }

    private class FakeTelemetryPreference(
        initial: Boolean,
    ) : MapTelemetryPreference {
        var current = initial
        var failRead = false
        var failSet = false
        val requestedStates = mutableListOf<Boolean>()

        override fun read(): Result<Boolean> =
            if (failRead) Result.failure(IllegalStateException()) else Result.success(current)

        override fun set(
            enabled: Boolean,
            onComplete: (Boolean) -> Unit,
        ) {
            requestedStates += enabled
            if (!failSet) current = enabled
            onComplete(!failSet && current == enabled)
        }
    }
}
