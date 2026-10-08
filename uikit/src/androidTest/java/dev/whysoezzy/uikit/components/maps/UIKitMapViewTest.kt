package dev.whysoezzy.uikit.components.maps

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
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
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UIKitMapViewTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun noTokenFallbackPreservesPhysicalSizeCreditsAndIndependentGeoAction() {
        assumeTrue(BuildConfig.MAPBOX_PUBLIC_TOKEN.isBlank())
        var mapClicks = 0
        val address = "ул. Тверская 15"
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                UIKitTheme {
                    UIKitMapView(
                        address = address,
                        latitude = 55.7,
                        longitude = 37.6,
                        meetingId = 12345L,
                        modifier = Modifier.testTag("map-parent"),
                        onMapClick = { mapClicks++ },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("uikit-map-preview").assertIsDisplayed()
        val bounds =
            composeTestRule
                .onNodeWithTag("uikit-map-preview")
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(240.dp.value, bounds.height / composeTestRule.density.density, 0.5f)
        composeTestRule.onNodeWithText("© Mapbox").assertIsDisplayed()
        composeTestRule.onNodeWithText("© OpenStreetMap").assertIsDisplayed()
        composeTestRule.onNodeWithText("Improve this map").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mapbox privacy information").assertIsDisplayed()

        composeTestRule.onNodeWithText("© Mapbox").assertHasClickAction()
        composeTestRule.onNodeWithText("© OpenStreetMap").assertHasClickAction()
        composeTestRule.onNodeWithText("Mapbox privacy information").assertHasClickAction()
        composeTestRule.onNodeWithText("Improve this map").assertHasClickAction()

        composeTestRule.onNodeWithText("Improve this map").performClick()
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

        composeTestRule.onNodeWithContentDescription("Открыть в картах: $address").performClick()
        assertEquals(1, mapClicks)
    }

    @Test
    fun topCenterTapOpensGeoWithoutAddingDuplicateAccessibilityControl() {
        var mapClicks = 0
        composeTestRule.setContent {
            UIKitTheme {
                MapPreviewTopAction(onMapClick = { mapClicks++ })
            }
        }

        composeTestRule.onNodeWithTag(MAP_PREVIEW_TOP_ACTION_TEST_TAG).performTouchInput { click() }

        assertEquals(1, mapClicks)
        composeTestRule.onAllNodesWithContentDescription("Открыть в картах").assertCountEquals(0)
    }

    @Test
    fun blankAddressUsesExactlyTheGenericActionDescriptionInFallback() {
        assumeTrue(BuildConfig.MAPBOX_PUBLIC_TOKEN.isBlank())
        composeTestRule.setContent {
            UIKitTheme {
                UIKitMapView(
                    address = "",
                    latitude = 55.7,
                    longitude = 37.6,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Открыть в картах").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onAllNodesWithContentDescription("Открыть в картах: Открыть в картах").assertCountEquals(0)
    }

    @Test
    fun loadingAndUnavailableMaskCoversTheFullImageIncludingTopStripAndReadyRemovesIt() {
        val status = mutableStateOf(MapPreviewStatus.LOADING)
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier
                        .size(width = 320.dp, height = 240.dp)
                        .background(Color.Magenta)
                        .testTag("mask-container"),
            ) { MapPreviewImageMask(status) }
        }

        for (maskStatus in listOf(MapPreviewStatus.LOADING, MapPreviewStatus.UNAVAILABLE)) {
            status.value = maskStatus
            composeTestRule.waitForIdle()

            val containerBounds = composeTestRule.onNodeWithTag("mask-container").fetchSemanticsNode().boundsInRoot
            val maskNode = composeTestRule.onNodeWithTag(MAP_PREVIEW_MASK_TEST_TAG)
            maskNode.assertIsDisplayed()
            val maskBounds = maskNode.fetchSemanticsNode().boundsInRoot
            assertEquals(containerBounds.left, maskBounds.left, 0.5f)
            assertEquals(containerBounds.top, maskBounds.top, 0.5f)
            assertEquals(containerBounds.width, maskBounds.width, 0.5f)
            assertEquals(containerBounds.height, maskBounds.height, 0.5f)

            val image = composeTestRule.onNodeWithTag("mask-container").captureToImage().toPixelMap()
            val topStripSampleY = (24.dp.value * composeTestRule.density.density).toInt()
            assertEquals(MAP_PREVIEW_FALLBACK_COLOR, image[image.width / 2, topStripSampleY])
        }

        status.value = MapPreviewStatus.READY
        composeTestRule.waitForIdle()
        val readyImage = composeTestRule.onNodeWithTag("mask-container").captureToImage().toPixelMap()
        val topStripSampleY = (24.dp.value * composeTestRule.density.density).toInt()
        assertEquals(Color.Magenta, readyImage[readyImage.width / 2, topStripSampleY])
    }

    @Test
    fun compactLargeFontFallbackKeepsCreditsClickableAndAllowsParentScroll() {
        assumeTrue(BuildConfig.MAPBOX_PUBLIC_TOKEN.isBlank())
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
                        UIKitMapView(
                            address = "Compact meeting",
                            latitude = 55.7,
                            longitude = 37.6,
                            modifier = Modifier.testTag("compact-map"),
                        )
                        Spacer(Modifier.height(500.dp))
                    }
                }
            }
        }

        val mapBounds = composeTestRule.onNodeWithTag("uikit-map-preview").fetchSemanticsNode().boundsInRoot
        assertEquals(320.dp.value, mapBounds.width / composeTestRule.density.density, 0.5f)
        composeTestRule.onNodeWithText("© Mapbox").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("© OpenStreetMap").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Improve this map").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Mapbox privacy information").assertIsDisplayed().assertHasClickAction()

        composeTestRule.onNodeWithTag("compact-map").performTouchInput {
            swipe(
                start = center.copy(y = center.y + 30f),
                end = center.copy(y = center.y - 90f),
                durationMillis = 500L,
            )
        }
        assertTrue(
            "Vertical drag on the map preview must still scroll the parent",
            requireNotNull(parentScrollState).value > 0,
        )
    }

    @Test
    fun invalidCoordinateHasNoGeoAction() {
        var mapClicks = 0
        composeTestRule.setContent {
            UIKitTheme {
                UIKitMapView(
                    address = "address",
                    latitude = Double.NaN,
                    longitude = 37.6,
                    onMapClick = { mapClicks++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Местоположение недоступно").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mapbox privacy information").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Открыть в картах").assertIsNotEnabled()
        assertEquals(0, mapClicks)
    }

    @Test
    fun publicTokenAllowsTheProductionMapToReachACompleteLoadedFrame() {
        assumeTrue(BuildConfig.MAPBOX_PUBLIC_TOKEN.isNotBlank())
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val meetingAddress = "Test address must not leave the app"
        val meetingId = 987654321L
        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                UIKitTheme {
                    UIKitMapView(
                        address = meetingAddress,
                        latitude = 37.7749,
                        longitude = -122.4194,
                        meetingId = meetingId,
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(MAP_PREVIEW_MASK_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MAP_PREVIEW_LOGO_TEST_TAG).assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag(MAP_PREVIEW_ATTRIBUTION_TEST_TAG).assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag(MAP_PREVIEW_STATUS_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
        assertEquals(MAP_PREVIEW_FALLBACK_COLOR, capturePreviewScreenPixel(0.5f, 0.1f))

        composeTestRule.onNodeWithTag(MAP_PREVIEW_LOGO_TEST_TAG).performClick()
        val logoIntent = requireNotNull(context.startedIntent)
        assertEquals(Intent.ACTION_VIEW, logoIntent.action)
        assertEquals("www.mapbox.com", logoIntent.data?.host)
        context.startedIntent = null

        try {
            composeTestRule.waitUntil(20_000L) {
                composeTestRule
                    .onAllNodesWithTag(MAP_PREVIEW_STATUS_TEST_TAG, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isEmpty()
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            val failureKind =
                when {
                    composeTestRule
                        .onAllNodesWithText("Карта недоступна", useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty() -> "SDK_UNAVAILABLE"
                    composeTestRule
                        .onAllNodesWithText("Загрузка карты…", useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty() -> "STILL_LOADING"
                    else -> "NO_STATUS_NODE"
                }
            if (failureKind == "SDK_UNAVAILABLE") {
                composeTestRule.onNodeWithTag(MAP_PREVIEW_LOGO_TEST_TAG).assertIsDisplayed().assertHasClickAction()
                composeTestRule
                    .onNodeWithTag(MAP_PREVIEW_ATTRIBUTION_TEST_TAG)
                    .assertIsDisplayed()
                    .assertHasClickAction()
                assertEquals(MAP_PREVIEW_FALLBACK_COLOR, capturePreviewScreenPixel(0.5f, 0.1f))
            }
            throw AssertionError("Sanitized Mapbox render classification: $failureKind", timeout)
        }
        composeTestRule.onNodeWithText("© Mapbox").assertIsDisplayed()
        composeTestRule.onNodeWithText("© OpenStreetMap").assertIsDisplayed()
        val readyCenterPixel = capturePreviewScreenPixel(0.5f, 0.5f)
        assertTrue("READY map imagery is still covered by the fallback mask", readyCenterPixel != MAP_PREVIEW_FALLBACK_COLOR)
        assertTrue("READY preview did not expose map imagery", readyCenterPixel != Color(0xFFE6ECF0))

        composeTestRule.onNodeWithTag(MAP_PREVIEW_ATTRIBUTION_TEST_TAG).performClick()
        composeTestRule.waitUntil(5_000L) {
            composeTestRule
                .onAllNodesWithText("Improve this map", ignoreCase = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Improve this map", ignoreCase = true).performClick()
        composeTestRule.waitUntil(5_000L) { context.startedIntent != null }

        val sdkFeedback = requireNotNull(context.startedIntent)
        val initialCameraFragment = assertApprovedSdkFeedbackPayload(sdkFeedback, context)
        context.startedIntent = null

        composeTestRule.onNodeWithTag("uikit-map-preview").performTouchInput {
            swipe(
                start = Offset(center.x * 0.3f, center.y),
                end = Offset(center.x * 0.7f, center.y),
                durationMillis = 600L,
            )
        }
        composeTestRule.onNodeWithTag(MAP_PREVIEW_ATTRIBUTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Improve this map", ignoreCase = true).performClick()
        composeTestRule.waitUntil(5_000L) { context.startedIntent != null }
        val afterGestureIntent = requireNotNull(context.startedIntent)
        assertEquals(initialCameraFragment, assertApprovedSdkFeedbackPayload(afterGestureIntent, context))
        context.startedIntent = null

        composeTestRule.onNodeWithText("Improve this map").performClick()
        val genericFeedback = requireNotNull(context.startedIntent)
        assertEquals(Intent.ACTION_VIEW, genericFeedback.action)
        assertEquals("https://apps.mapbox.com/feedback/", genericFeedback.data.toString())
        assertNull(genericFeedback.data?.query)
        assertNull(genericFeedback.data?.fragment)
    }

    private fun assertApprovedSdkFeedbackPayload(
        intent: Intent,
        context: Context,
    ): String {
        val uri = requireNotNull(intent.data)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https", uri.scheme)
        assertEquals("apps.mapbox.com", uri.host)
        assertEquals("/feedback", uri.path)
        assertEquals(setOf("referrer", "access_token", "owner", "id"), uri.queryParameterNames)
        assertEquals(context.packageName, uri.getQueryParameter("referrer"))
        assertTrue(
            "SDK attribution must use the configured public token",
            uri.getQueryParameter("access_token") == BuildConfig.MAPBOX_PUBLIC_TOKEN,
        )
        assertEquals("mapbox", uri.getQueryParameter("owner"))
        assertEquals("streets-v12", uri.getQueryParameter("id"))
        val camera = requireNotNull(uri.fragment).split('/')
        assertEquals(6, camera.size)
        assertEquals(-122.4194, camera[1].toDouble(), 0.000001)
        assertEquals(37.7749, camera[2].toDouble(), 0.000001)
        assertEquals(15.0, camera[3].toDouble(), 0.000001)
        assertEquals(0.0, camera[4].toDouble(), 0.000001)
        assertEquals(0.0, camera[5].toDouble(), 0.000001)
        // The exact query-key allowlist excludes address, meeting identity, and backend metadata.
        return requireNotNull(uri.fragment)
    }

    private fun capturePreviewScreenPixel(
        xFraction: Float,
        yFraction: Float,
    ): Color {
        val previewBounds = composeTestRule.onNodeWithTag("uikit-map-preview").fetchSemanticsNode().boundsInRoot
        val contentView = composeTestRule.activity.findViewById<View>(android.R.id.content)
        val contentOrigin = IntArray(2)
        contentView.getLocationOnScreen(contentOrigin)
        val screenshot =
            requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val x = contentOrigin[0] + (previewBounds.left + previewBounds.width * xFraction).toInt()
        val y = contentOrigin[1] + (previewBounds.top + previewBounds.height * yFraction).toInt()

        return Color(screenshot.getPixel(x, y))
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
