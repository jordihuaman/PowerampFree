package com.powerampfree.audio.focus

/** Focus events we react to, decoupled from Android's int constants. */
enum class FocusChange {
    /** Regained focus (other audio ended). */
    GAIN,
    /** Permanently lost (another app took audio). */
    LOSS,
    /** Temporarily lost (call, prompt); will be returned. */
    LOSS_TRANSIENT,
    /** Temporarily lost but duckable (nav prompt over music). */
    LOSS_TRANSIENT_CAN_DUCK,
}

/** What the service should do in response to a focus event. */
enum class FocusAction {
    NONE,
    PAUSE,
    RESUME,
    /** Lower our DSP volume instead of pausing. */
    DUCK,
    /** Restore DSP volume after a duck. */
    RESTORE,
}

/** User-configurable audio-focus behaviour (block 8 of the feature catalog). */
data class FocusToggles(
    val pause: Boolean = true,
    val resume: Boolean = true,
    val duck: Boolean = true,
)

/**
 * Pure decision table mapping a focus event to an action, honouring the user's
 * [FocusToggles]. Kept free of Android types so the branching (pause vs duck vs
 * resume vs restore) is unit-testable.
 *
 * State inputs ([pausedByFocus], [ducked]) are what the caller last did: a
 * [FocusAction.PAUSE] sets pausedByFocus, a [FocusAction.DUCK] sets ducked, and
 * [FocusAction.GAIN] then restores whichever applies first (duck before resume,
 * since duck and pause are mutually exclusive moments).
 */
object FocusPolicy {
    fun onEvent(
        change: FocusChange,
        toggles: FocusToggles,
        pausedByFocus: Boolean,
        ducked: Boolean,
    ): FocusAction = when (change) {
        FocusChange.GAIN -> when {
            ducked -> FocusAction.RESTORE
            pausedByFocus && toggles.resume -> FocusAction.RESUME
            else -> FocusAction.NONE
        }
        FocusChange.LOSS ->
            if (toggles.pause) FocusAction.PAUSE else FocusAction.NONE
        FocusChange.LOSS_TRANSIENT -> when {
            toggles.pause -> FocusAction.PAUSE
            toggles.duck -> FocusAction.DUCK
            else -> FocusAction.NONE
        }
        FocusChange.LOSS_TRANSIENT_CAN_DUCK -> when {
            toggles.duck -> FocusAction.DUCK
            toggles.pause -> FocusAction.PAUSE
            else -> FocusAction.NONE
        }
    }
}
