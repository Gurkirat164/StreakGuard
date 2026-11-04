package com.streakguard.app.ui.battery

import org.junit.Assert.*
import org.junit.Test

class BatteryPromptStateTest {
    private val optimised = BatteryRestrictionStatus(true, false)
    private val unrestricted = BatteryRestrictionStatus(false, false)

    @Test fun firstVisitRequestsAndroidDirectly() {
        assertEquals(BatteryPrompt.NATIVE_REQUEST,
            BatteryPromptState().foreground(optimised, suppressed = false, requestAttempted = false).prompt)
    }

    @Test fun declinedNativeRequestDoesNotImmediatelyShowAnotherDialog() {
        val waiting = BatteryPromptState().foreground(optimised, false, false).request().background()
        assertTrue(waiting.awaitingRequestReturn)
        val returned = waiting.foreground(optimised, false, true)
        assertEquals(BatteryPrompt.NONE, returned.prompt)
        assertFalse(returned.awaitingRequestReturn)
        assertEquals(BatteryPrompt.REMINDER, returned.background().foreground(optimised, false, true).prompt)
    }

    @Test fun oneDismissalEndsTheVisitWithoutASecondPopup() {
        val closed = BatteryPromptState().foreground(optimised, false, true).dismiss()
        assertEquals(BatteryPrompt.NONE, closed.prompt)
        assertEquals(BatteryPrompt.NONE, closed.foreground(optimised, false, true).prompt)
        assertEquals(BatteryPrompt.REMINDER, closed.background().foreground(optimised, false, true).prompt)
    }

    @Test fun suppressionPreventsBothInitialRequestAndLaterReminders() {
        listOf(false, true).forEach { attempted ->
            assertEquals(BatteryPrompt.NONE, BatteryPromptState().foreground(optimised, true, attempted).prompt)
        }
    }

    @Test fun resumeAndRotationDoNotRestartDismissedReminder() {
        val closed = BatteryPromptState().foreground(optimised, false, true).dismiss()
        repeat(3) { assertEquals(BatteryPrompt.NONE, closed.foreground(optimised, false, true).prompt) }
    }

    @Test fun manualRequestWorksWhenAutomaticPromptsAreSuppressed() {
        val manual = BatteryPromptState().foreground(optimised, true, true).request().background()
        assertTrue(manual.awaitingRequestReturn)
        assertEquals(BatteryPrompt.NONE, manual.foreground(optimised, true, true).prompt)
    }

    @Test fun successfulRequestClearsReminder() {
        val waiting = BatteryPromptState().foreground(optimised, false, true).request().background()
        assertEquals(BatteryPrompt.NONE, waiting.foreground(unrestricted, false, true).prompt)
    }

    @Test fun existingExemptionSkipsNativeAndCustomRequests() {
        listOf(false, true).forEach { attempted ->
            assertEquals(BatteryPrompt.NONE, BatteryPromptState().foreground(unrestricted, false, attempted).prompt)
        }
    }

    @Test fun revokingExemptionShowsReminderOnNextVisit() {
        val granted = BatteryPromptState().foreground(unrestricted, false, true)
        assertEquals(BatteryPrompt.REMINDER, granted.background().foreground(optimised, false, true).prompt)
    }

    @Test fun separateBackgroundRestrictionDoesNotRepeatAnAlreadyGrantedExemption() {
        assertEquals(BatteryPrompt.NONE,
            BatteryPromptState().foreground(BatteryRestrictionStatus(false, true), false, true).prompt)
    }
}
