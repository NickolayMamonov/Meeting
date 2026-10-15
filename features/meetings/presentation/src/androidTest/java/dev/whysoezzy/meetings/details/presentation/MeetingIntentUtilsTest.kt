package dev.whysoezzy.meetings.details.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeetingIntentUtilsTest {
    @Test
    fun openMapIntentEncodesAddressAndUsesExistingExternalGeoContract() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val address = "ул. Тверская 15, подъезд A & зал"

        openMapIntent(context, 55.7, 37.6, address)

        val intent = requireNotNull(context.startedIntent)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo", intent.data?.scheme)
        assertEquals("geo:55.7,37.6?q=${Uri.encode(address)}", intent.data.toString())
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun openMapIntentSilentlyHandlesMissingGeoHandler() {
        val context =
            RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                shouldThrow = true
            }

        openMapIntent(context, 55.7, 37.6, "address")

        assertNotNull(context.attemptedIntent)
        assertNull(context.startedIntent)
    }

    private class RecordingContext(
        base: Context,
    ) : ContextWrapper(base) {
        var attemptedIntent: Intent? = null
        var startedIntent: Intent? = null
        var shouldThrow: Boolean = false

        override fun startActivity(intent: Intent) {
            attemptedIntent = intent
            if (shouldThrow) throw ActivityNotFoundException()
            startedIntent = intent
        }
    }
}
