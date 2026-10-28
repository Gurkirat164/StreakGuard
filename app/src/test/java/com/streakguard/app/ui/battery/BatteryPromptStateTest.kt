package com.streakguard.app.ui.battery

import org.junit.Assert.*
import org.junit.Test

class BatteryPromptStateTest {
    private val optimised = BatteryRestrictionStatus(true, false)
    private val backgroundLimited = BatteryRestrictionStatus(false, true)
    private val unrestricted = BatteryRestrictionStatus(false, false)

    @Test fun eitherRestrictionShowsFirstPrompt() {
        listOf(optimised, backgroundLimited, BatteryRestrictionStatus(true, true)).forEach {
            assertEquals(BatteryPrompt.FIRST, BatteryPromptState().foreground(it, false).prompt)
        }
    }

    @Test fun firstDismissalOpensConfirmationAndSecondClosesVisit() {
        val first = BatteryPromptState().foreground(optimised, false)
        assertEquals(BatteryPrompt.CONFIRMATION, first.dismiss().prompt)
        val closed = first.dismiss().dismiss()
        assertEquals(BatteryPrompt.NONE, closed.prompt)
        assertEquals(BatteryPrompt.NONE, closed.foreground(optimised, false).prompt)
    }

    @Test fun nextVisitRepeatsUnlessPermanentlySuppressed() {
        val closed = BatteryPromptState().foreground(optimised, false).dismiss().dismiss().background()
        assertEquals(BatteryPrompt.FIRST, closed.foreground(optimised, false).prompt)
        assertEquals(BatteryPrompt.NONE, closed.foreground(optimised, true).prompt)
    }

    @Test fun suppressedLaunchIsSilentForEitherRestriction() {
        listOf(optimised, backgroundLimited).forEach {
            assertEquals(BatteryPrompt.NONE, BatteryPromptState().foreground(it, true).prompt)
        }
    }

    @Test fun repeatedResumeOrRotationDoesNotResetConfirmation() {
        val confirmation = BatteryPromptState().foreground(optimised, false).dismiss()
        assertEquals(BatteryPrompt.CONFIRMATION, confirmation.foreground(optimised, false).prompt)
    }

    @Test fun settingsReturnRechecksBothRestrictions() {
        val waiting = BatteryPromptState().foreground(optimised, false).dismiss().openSettings().background()
        assertTrue(waiting.awaitingSettingsReturn)
        val stillRestricted = waiting.foreground(backgroundLimited, false)
        assertEquals(BatteryPrompt.CONFIRMATION, stillRestricted.prompt)
        assertFalse(stillRestricted.awaitingSettingsReturn)
        assertEquals(BatteryPrompt.NONE, waiting.foreground(unrestricted, false).prompt)
    }

    @Test fun manualSettingsRoundTripDoesNotReopenDismissedPrompts() {
        val manual = BatteryPromptState().foreground(optimised, false).dismiss().dismiss()
            .openSettings().background()
        assertEquals(BatteryPrompt.NONE, manual.foreground(optimised, false).prompt)
    }

    @Test fun clearingBothRestrictionsStopsAllAutomaticPrompts() {
        assertEquals(BatteryPrompt.NONE, BatteryPromptState().foreground(unrestricted, false).prompt)
        val showing = BatteryPromptState().foreground(optimised, false)
        assertEquals(BatteryPrompt.NONE, showing.foreground(unrestricted, false).prompt)
        assertEquals(BatteryPrompt.FIRST, showing.foreground(unrestricted, false).background()
            .foreground(optimised, false).prompt)
    }
}
