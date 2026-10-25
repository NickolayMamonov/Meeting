package dev.whysoezzy.uikit.components.maps

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whysoezzy.uikit.BuildConfig
import dev.whysoezzy.uikit.components.blocks.LOCATIONIQ_CREDIT_TEST_TAG
import dev.whysoezzy.uikit.components.blocks.UIKitAddressMapBlock
import dev.whysoezzy.uikit.components.maps.MAP_PREVIEW_TEST_TAG
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.graphics.Color as AndroidColor

@RunWith(AndroidJUnit4::class)
class UIKitMapViewTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun noTokenFallbackPreservesBrandingTelemetryCreditsAndIndependentGeoAction() {
        assumeTrue(BuildConfig.MAPBOX_PUBLIC_TOKEN.isBlank())
        var mapClicks = 0
        val address = "ул. Тверская 15"
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                UIKitTheme {
                    UIKitAddressMapBlock(
                        address = address,
                        latitude = 55.7,
                        longitude = 37.6,
                        meetingId = 12345L,
                        nearestMetro = "Не указано",
                        onMapClick = { mapClicks++ },
                    )
                }
            }
        }

        assertPreviewHeight()
        val mapBounds =
            composeTestRule.onNodeWithTag(MAP_PREVIEW_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val locationIqNode =
            composeTestRule
                .onNodeWithTag(LOCATIONIQ_CREDIT_TEST_TAG)
                .assertIsDisplayed()
                .assertHasClickAction()
        val locationIqBounds = locationIqNode.fetchSemanticsNode().boundsInRoot
        val creditGap = locationIqBounds.top - mapBounds.bottom
        assertTrue(creditGap >= 0f)
        with(composeTestRule.density) {
            assertTrue(creditGap <= 8.dp.toPx())
        }
        locationIqNode.performClick()
        val locationIqIntent = requireNotNull(context.startedIntent)
        assertEquals(Intent.ACTION_VIEW, locationIqIntent.action)
        assertEquals("https://locationiq.com", locationIqIntent.data.toString())
        assertNull(locationIqIntent.data?.query)
        assertNull(locationIqIntent.data?.fragment)

        composeTestRule
            .onNodeWithText("При загрузке карты координаты встречи передаются Mapbox.")
            .assertIsDisplayed()
        assertBrandingAndCredits()
        composeTestRule.onNodeWithTag(MAP_PREVIEW_INFO_TEST_TAG).assertHasClickAction()

        composeTestRule.onNodeWithTag(MAP_PREVIEW_INFO_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Исправить карту").performClick()
        val feedbackIntent = requireNotNull(context.startedIntent)
        val feedbackUrl = requireNotNull(feedbackIntent.data).toString()
        assertEquals(Intent.ACTION_VIEW, feedbackIntent.action)
        assertEquals(mapFeedbackUrl(), feedbackUrl)
        assertFalse(feedbackUrl.contains(address))
        assertFalse(feedbackUrl.contains("55.7"))
        assertFalse(feedbackUrl.contains("37.6"))
        assertFalse(feedbackUrl.contains("12345"))
        assertNull(feedbackIntent.data?.query)
        assertNull(feedbackIntent.data?.fragment)
        assertEquals(0, mapClicks)

        composeTestRule.onNodeWithText("Закрыть").performClick()
        composeTestRule.onNodeWithContentDescription("Открыть в картах: $address").performClick()
        assertEquals(1, mapClicks)
    }

    @Test
    fun fakeSnapshotReadyShowsExactBitmapAndNoNativeMapRendererViews() {
        val client = ImmediateSnapshotClient(success = true)
        composeTestRule.setContent {
            UIKitTheme {
                UIKitMapViewContent(
                    address = "Ready meeting",
                    latitude = 55.7,
                    longitude = 37.6,
                    dependencies = dependencies(client),
                )
            }
        }

        composeTestRule.waitUntil(5_000L) {
            composeTestRule
                .onAllNodesWithText("Загрузка карты…")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeTestRule.onAllNodesWithText("Карта недоступна").assertCountEquals(0)
        val pixels = composeTestRule.onNodeWithTag(MAP_PREVIEW_IMAGE_TEST_TAG).captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[pixels.width / 2, pixels.height / 2])
        assertEquals(Color(0xFF4CAF50), pixels[pixels.width - 4, pixels.height / 2])
        assertEquals(Color(0xFF4CAF50), pixels[pixels.width / 2, pixels.height - 4])
        assertPreviewHeight(MAP_PREVIEW_IMAGE_TEST_TAG)
        assertEquals(1, client.startCount)
        assertBrandingAndCredits()
        assertEquals(1, client.startCount)

        val decor = composeTestRule.activity.window.decorView
        assertFalse(decor.descendants().any { it is com.mapbox.maps.MapView })
        assertFalse(decor.descendants().any { it is TextureView })
        assertFalse(decor.descendants().any { it is SurfaceView })
    }

    @Test
    fun belowMinimumViewportIsUnavailableWithoutConstructingSnapshotClient() {
        val client = ImmediateSnapshotClient(success = true)
        composeTestRule.setContent {
            UIKitTheme {
                Box(Modifier.width(180.dp)) {
                    UIKitMapViewContent(
                        address = "Narrow meeting",
                        latitude = 55.7,
                        longitude = 37.6,
                        dependencies = dependencies(client),
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Карта недоступна").assertIsDisplayed()
        assertEquals(0, client.startCount)
        assertBrandingAndCredits()
        composeTestRule.onNodeWithTag(MAP_PREVIEW_INFO_TEST_TAG).assertHasClickAction()
    }

    @Test
    fun throwingSnapshotClientShowsUnavailableInsteadOfLoadingForever() {
        composeTestRule.setContent {
            UIKitTheme {
                UIKitMapViewContent(
                    address = "Throwing snapshot",
                    latitude = 55.7,
                    longitude = 37.6,
                    dependencies = dependencies(ThrowingSnapshotClient()),
                )
            }
        }

        composeTestRule.onNodeWithText("Карта недоступна").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Загрузка карты…").assertCountEquals(0)
    }

    @Test
    fun telemetryDialogRoundTripsWithFakePreferenceWhileLoading() {
        assertTelemetryDialogRoundTrip(HoldingSnapshotClient())
    }

    @Test
    fun telemetryDialogRoundTripsWithFakePreferenceWhileReady() {
        val client = ImmediateSnapshotClient(success = true)
        assertTelemetryDialogRoundTrip(client)
        assertEquals(1, client.startCount)
    }

    @Test
    fun telemetryDialogRoundTripsWithFakePreferenceWhileUnavailable() {
        assertTelemetryDialogRoundTrip(ImmediateSnapshotClient(success = false))
    }

    @Test
    fun telemetryUnavailableDisablesToggleAndDoesNotRequestMutation() {
        val telemetry = FakeTelemetryPreference(initial = true).apply { failRead = true }
        composeTestRule.setContent {
            UIKitTheme {
                UIKitMapViewContent(
                    address = "Telemetry meeting",
                    latitude = 55.7,
                    longitude = 37.6,
                    dependencies = dependencies(ImmediateSnapshotClient(success = false), telemetry),
                )
            }
        }

        openTelemetry()
        composeTestRule.onNodeWithText("Настройка телеметрии недоступна.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Включить").assertIsNotEnabled()
        assertEquals(emptyList<Boolean>(), telemetry.requestedStates)
    }

    @Test
    fun compactLargeFontEligiblePreviewKeepsOneActionCreditsAndParentScroll() {
        val client = ImmediateSnapshotClient(success = true)
        var parentScrollState: ScrollState? = null
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(composeTestRule.density.density, fontScale = 1.3f),
            ) {
                UIKitTheme {
                    val scrollState = rememberScrollState()
                    parentScrollState = scrollState
                    Column(
                        modifier = Modifier.width(320.dp).height(250.dp).verticalScroll(scrollState),
                    ) {
                        UIKitMapViewContent(
                            address = "Compact meeting",
                            latitude = 55.7,
                            longitude = 37.6,
                            modifier = Modifier.testTag("compact-map"),
                            dependencies = dependencies(client),
                        )
                        Spacer(Modifier.height(500.dp))
                    }
                }
            }
        }

        assertCompactPhase("COMPACT_HEIGHT_FAILURE") { assertPreviewHeight("compact-map") }
        assertCompactPhase("COMPACT_BRANDING_FAILURE") { assertBrandingAndCredits() }
        assertCompactPhase("COMPACT_ACTION_FAILURE") {
            composeTestRule.onAllNodesWithContentDescription("Открыть в картах: Compact meeting").assertCountEquals(1)
        }
        assertCompactPhase("COMPACT_SCROLL_FAILURE") {
            composeTestRule.onNodeWithTag("compact-map").performTouchInput {
                swipe(
                    start = center.copy(y = center.y + 30f),
                    end = center.copy(y = center.y - 90f),
                    durationMillis = 500L,
                )
            }
            assertTrue(requireNotNull(parentScrollState).value > 0)
        }
    }

    private inline fun assertCompactPhase(
        label: String,
        action: () -> Unit,
    ) {
        try {
            action()
        } catch (_: AssertionError) {
            throw AssertionError(label)
        } catch (_: Exception) {
            throw AssertionError(label)
        }
    }

    @Test
    fun invalidCoordinateHasNoGeoActionButRetainsPrivacyAndTelemetryControls() {
        var mapClicks = 0
        composeTestRule.setContent {
            UIKitTheme {
                UIKitMapViewContent(
                    address = "address",
                    latitude = Double.NaN,
                    longitude = 37.6,
                    onMapClick = { mapClicks++ },
                    dependencies = dependencies(ImmediateSnapshotClient(success = true)),
                )
            }
        }

        composeTestRule.onNodeWithText("Местоположение недоступно").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Открыть в картах").assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MAP_PREVIEW_INFO_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Конфиденциальность Mapbox").assertIsDisplayed()
        composeTestRule.onNodeWithText("Настройки телеметрии Mapbox").assertIsDisplayed()
        assertEquals(0, mapClicks)
    }

    private fun dependencies(
        client: MapPreviewSnapshotClient,
        telemetry: MapTelemetryPreference = FakeTelemetryPreference(initial = false),
    ): MapPreviewDependencies =
        MapPreviewDependencies(
            hasPublicToken = true,
            snapshotClientFactory = { client },
            telemetryPreference = telemetry,
            supportsGles3 = true,
        )

    private fun assertTelemetryDialogRoundTrip(client: MapPreviewSnapshotClient) {
        val telemetry = FakeTelemetryPreference(initial = true)
        composeTestRule.setContent {
            UIKitTheme {
                UIKitMapViewContent(
                    address = "Telemetry meeting",
                    latitude = 55.7,
                    longitude = 37.6,
                    dependencies = dependencies(client, telemetry),
                )
            }
        }

        openTelemetry()
        composeTestRule.onNodeWithText("Сбор телеметрии включён.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Выключить").performClick()
        composeTestRule.onNodeWithText("Сбор телеметрии выключен.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Закрыть").performClick()
        composeTestRule.onNodeWithTag(MAP_PREVIEW_INFO_TEST_TAG).assertIsDisplayed()
        assertEquals(listOf(false), telemetry.requestedStates)
    }

    private fun openTelemetry() {
        composeTestRule.onAllNodesWithText("Настройки телеметрии Mapbox").assertCountEquals(0)
        composeTestRule.onNodeWithTag(MAP_PREVIEW_INFO_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Конфиденциальность Mapbox").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Настройки телеметрии Mapbox").performClick()
    }

    private fun assertPreviewHeight(testTag: String = "uikit-map-preview") {
        val bounds =
            composeTestRule
                .onNodeWithTag(testTag)
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(220.dp.value, bounds.height / composeTestRule.density.density, 0.5f)
    }

    private fun assertBrandingAndCredits() {
        composeTestRule.onNodeWithTag(MAP_PREVIEW_LOGO_TEST_TAG).assertIsDisplayed().assertHasClickAction()
        composeTestRule.onAllNodesWithText("Исправить карту").assertCountEquals(0)
        composeTestRule.onNodeWithTag(MAP_PREVIEW_INFO_TEST_TAG).assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag(MAP_PREVIEW_INFO_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("© Mapbox").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("© OpenStreetMap").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Исправить карту").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Конфиденциальность Mapbox").assertIsDisplayed()
        composeTestRule.onNodeWithText("Закрыть").performClick()
        composeTestRule.onAllNodesWithText("Конфиденциальность Mapbox").assertCountEquals(0)
    }

    private open class ImmediateSnapshotClient(
        private val success: Boolean,
    ) : MapPreviewSnapshotClient {
        var startCount = 0

        override fun start(
            request: MapPreviewSnapshotRequest,
            onComplete: (MapPreviewSnapshotCompletion) -> Unit,
        ): MapPreviewSnapshotHandle {
            startCount += 1
            if (success) {
                val bitmap = Bitmap.createBitmap(request.widthPx, request.heightPx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(AndroidColor.rgb(76, 175, 80))
                canvas.drawCircle(
                    request.widthPx / 2f,
                    request.heightPx / 2f,
                    10f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.RED },
                )
                onComplete(
                    MapPreviewSnapshotCompletion(
                        bitmap = AndroidMapPreviewBitmap(bitmap),
                        hasSdkError = false,
                        exactStyleLoaded = true,
                        pinOverlaySucceeded = true,
                    ),
                )
            } else {
                onComplete(
                    MapPreviewSnapshotCompletion(
                        bitmap = null,
                        hasSdkError = true,
                        exactStyleLoaded = false,
                        pinOverlaySucceeded = false,
                    ),
                )
            }
            return MapPreviewSnapshotHandle {}
        }
    }

    private class HoldingSnapshotClient : ImmediateSnapshotClient(success = true) {
        override fun start(
            request: MapPreviewSnapshotRequest,
            onComplete: (MapPreviewSnapshotCompletion) -> Unit,
        ): MapPreviewSnapshotHandle = MapPreviewSnapshotHandle {}
    }

    private class ThrowingSnapshotClient : MapPreviewSnapshotClient {
        override fun start(
            request: MapPreviewSnapshotRequest,
            onComplete: (MapPreviewSnapshotCompletion) -> Unit,
        ): MapPreviewSnapshotHandle = throw IllegalStateException()
    }

    private class FakeTelemetryPreference(
        initial: Boolean,
    ) : MapTelemetryPreference {
        private var state = initial
        var failRead = false
        val requestedStates = mutableListOf<Boolean>()

        override fun read(): Result<Boolean> =
            if (failRead) Result.failure(IllegalStateException()) else Result.success(state)

        override fun set(
            enabled: Boolean,
            onComplete: (Boolean) -> Unit,
        ) {
            requestedStates += enabled
            state = enabled
            onComplete(true)
        }
    }

    private fun View.descendants(): Sequence<View> =
        sequence {
            yield(this@descendants)
            if (this@descendants is ViewGroup) {
                repeat(childCount) {
                    yieldAll(getChildAt(it).descendants())
                }
            }
        }

    private class RecordingContext(
        base: Context,
    ) : ContextWrapper(base) {
        var startedIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            startedIntent = intent
        }
    }
}
