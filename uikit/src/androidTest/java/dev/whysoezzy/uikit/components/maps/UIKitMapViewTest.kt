package dev.whysoezzy.uikit.components.maps

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whysoezzy.uikit.BuildConfig
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UIKitMapViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun noTokenFallbackPreservesPhysicalSizeCreditsAndIndependentGeoAction() {
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
        composeTestRule.setContent {
            UIKitTheme {
                UIKitMapView(
                    address = "Render readiness test",
                    latitude = 37.7749,
                    longitude = -122.4194,
                )
            }
        }

        composeTestRule.waitUntil(20_000L) {
            composeTestRule
                .onAllNodesWithTag(MAP_PREVIEW_STATUS_TEST_TAG)
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeTestRule.onNodeWithText("© Mapbox").assertIsDisplayed()
        composeTestRule.onNodeWithText("© OpenStreetMap").assertIsDisplayed()
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
