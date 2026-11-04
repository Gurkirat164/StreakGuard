package com.streakguard.app.ui.battery

internal data class BatteryRestrictionStatus(
    val optimisationEnabled: Boolean,
    val backgroundRestricted: Boolean,
) {
    val restricted: Boolean get() = optimisationEnabled || backgroundRestricted
}

internal enum class BatteryPrompt { NONE, NATIVE_REQUEST, REMINDER }

/** A permission round trip belongs to this visit, even when Android stops the activity. */
internal data class BatteryPromptState(
    val prompt: BatteryPrompt = BatteryPrompt.NONE,
    val visitActive: Boolean = false,
    val awaitingRequestReturn: Boolean = false,
) {
    fun foreground(status: BatteryRestrictionStatus, suppressed: Boolean, requestAttempted: Boolean): BatteryPromptState {
        val nextPrompt = when {
            !status.optimisationEnabled || suppressed -> BatteryPrompt.NONE
            awaitingRequestReturn || visitActive -> prompt
            !requestAttempted -> BatteryPrompt.NATIVE_REQUEST
            else -> BatteryPrompt.REMINDER
        }
        return copy(prompt = nextPrompt, visitActive = true, awaitingRequestReturn = false)
    }

    fun background() = copy(visitActive = false)

    fun dismiss() = copy(prompt = BatteryPrompt.NONE)

    fun request() = copy(prompt = BatteryPrompt.NONE, visitActive = true, awaitingRequestReturn = true)
}
