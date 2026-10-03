package dev.whysoezzy.uikit.components.maps

import androidx.compose.ui.Modifier
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
import dev.whysoezzy.uikit.BuildConfig
import dev.whysoezzy.uikit.theme.UIKitTheme
import org.junit.Assert.assertEquals
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
        composeTestRule.setContent {
            UIKitTheme {
                UIKitMapView(
                    address = address,
                    latitude = 55.7,
                    longitude = 37.6,
                    modifier = Modifier.testTag("map-parent"),
                    onMapClick = { mapClicks++ },
                )
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
}
