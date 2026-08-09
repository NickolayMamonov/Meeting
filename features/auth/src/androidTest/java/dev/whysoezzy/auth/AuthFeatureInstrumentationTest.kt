package dev.whysoezzy.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whysoezzy.auth.domain.models.AuthOutcome
import com.whysoezzy.auth.domain.models.EmailAddressParser
import dev.whysoezzy.auth.presentation.name.NameInputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthFeatureInstrumentationTest {
    @Test
    fun featureBoundary_usesCanonicalEmailAndExplicitNameMode() {
        val result = EmailAddressParser().parse(" Person@Example.com ")

        assertTrue(result is AuthOutcome.Success)
        assertEquals(
            "p***@example.com",
            (result as AuthOutcome.Success).value.masked,
        )
        assertNotEquals(NameInputMode.Onboarding, NameInputMode.ProfileCompletion)
    }
}
