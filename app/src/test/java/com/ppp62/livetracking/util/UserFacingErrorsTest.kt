package com.ppp62.livetracking.util

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class UserFacingErrorsTest {
    @Test fun unknownDiagnosticsAndTokensAreNotDisplayed() {
        val diagnostic = "URL: https://private.example\nHeaders: Authorization=Bearer secret-token\nHttp Method: POST"
        assertEquals("Please try again.", UserFacingErrors.message(IllegalStateException(diagnostic), "Please try again."))
        assertNull(UserFacingErrors.knownMessage(diagnostic))
        assertNull(UserFacingErrors.knownMessage("email_address_invalid\n" + diagnostic))
    }
    @Test fun rejectedEmailHasAnActionableMessageWithoutEchoingTheAddress() {
        val text = UserFacingErrors.knownMessage("email_address_invalid")!!
        assertTrue(text.contains("another email"))
        assertFalse(text.contains("email_address_invalid"))
        assertFalse(text.contains("@"))
    }
    @Test(expected = CancellationException::class) fun cancellationIsNotReportedAsAnError() {
        UserFacingErrors.message(CancellationException(), "Failed")
    }
}
