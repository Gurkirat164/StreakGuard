package com.streakguard.app.ui.battery

internal data class BatteryRestrictionStatus(
    val optimisationEnabled: Boolean,
    val backgroundRestricted: Boolean,
) {
    val restricted: Boolean get() = optimisationEnabled || backgroundRestricted
}

internal enum class BatteryPrompt { NONE, FIRST, CONFIRMATION }

/** A settings round trip belongs to the current visit, rather than starting another launch prompt. */
internal data class BatteryPromptState(
    val prompt: BatteryPrompt = BatteryPrompt.NONE,
    val visitActive: Boolean = false,
    val awaitingSettingsReturn: Boolean = false,
) {
    fun foreground(status: BatteryRestrictionStatus, suppressed: Boolean): BatteryPromptState {
        val nextPrompt = when {
            !status.restricted || suppressed -> BatteryPrompt.NONE
            awaitingSettingsReturn || visitActive -> prompt
            else -> BatteryPrompt.FIRST
        }
        return copy(prompt = nextPrompt, visitActive = true, awaitingSettingsReturn = false)
    }

    fun background() = copy(visitActive = false)

    fun dismiss() = copy(prompt = when (prompt) {
        BatteryPrompt.FIRST -> BatteryPrompt.CONFIRMATION
        else -> BatteryPrompt.NONE
    })

    fun openSettings() = copy(awaitingSettingsReturn = true)
}
