package com.medtech.reviewhub.ui.focus

import com.medtech.reviewhub.domain.model.PomodoroPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pomodoro focus-timer state machine — Compose-free and unit-testable.
 *
 * The timer itself never touches Android; the screen drives it with [tick]
 * calls and the [FocusSessionRecorder] callback stores completed sessions.
 */
class FocusTimerViewModel(
    private val recorder: FocusSessionRecorder = FocusSessionRecorder.NONE
) {

    enum class Phase { IDLE, WORK, BREAK, DONE, PAUSED }

    data class TimerState(
        val phase: Phase = Phase.IDLE,
        val preset: PomodoroPreset = PomodoroPreset.POMODORO_25,
        val workMinutes: Int = 25,
        val breakMinutes: Int = 5,
        val remainingMs: Long = 0L,
        val totalMs: Long = 0L,
        val sessionsCompleted: Int = 0,
        val interruptions: Int = 0,
        val startedAtMs: Long = 0L,
        val phaseStartedAtMs: Long = 0L,
        val snoozedUntilMs: Long = 0L
    )

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    fun configure(preset: PomodoroPreset, workMinutes: Int = 25, breakMinutes: Int = 5) {
        _state.update {
            it.copy(preset = preset, workMinutes = workMinutes.coerceIn(1, 120), breakMinutes = breakMinutes.coerceIn(1, 60))
        }
    }

    fun start(nowMs: Long = System.currentTimeMillis()) {
        val s = _state.value
        val workMs = s.workMinutes * 60_000L
        _state.update {
            it.copy(
                phase = Phase.WORK,
                remainingMs = workMs,
                totalMs = workMs,
                startedAtMs = if (it.startedAtMs == 0L) nowMs else it.startedAtMs,
                phaseStartedAtMs = nowMs
            )
        }
    }

    /** Advances the timer by one tick. Returns the new phase (for reactions). */
    suspend fun tick(nowMs: Long = System.currentTimeMillis()): Phase {
        val s = _state.value
        if (s.phase != Phase.WORK && s.phase != Phase.BREAK) return s.phase
        if (s.snoozedUntilMs > nowMs) return s.phase
        val remaining = s.remainingMs - TICK_MS
        return if (remaining <= 0) {
            when (s.phase) {
                Phase.WORK -> {
                    val breakMs = s.breakMinutes * 60_000L
                    val completed = s.sessionsCompleted + 1
                    _state.update {
                        it.copy(
                            phase = Phase.BREAK,
                            remainingMs = breakMs,
                            totalMs = breakMs,
                            sessionsCompleted = completed,
                            phaseStartedAtMs = nowMs
                        )
                    }
                    recordWork(s, nowMs)
                    Phase.BREAK
                }
                Phase.BREAK -> {
                    _state.update { it.copy(phase = Phase.DONE, remainingMs = 0L, phaseStartedAtMs = nowMs) }
                    Phase.DONE
                }
                else -> s.phase
            }
        } else {
            _state.update { it.copy(remainingMs = remaining) }
            s.phase
        }
    }

    private suspend fun recordWork(s: TimerState, nowMs: Long) {
        recorder.onWorkPhaseCompleted(
            startedAtMs = s.phaseStartedAtMs,
            endedAtMs = nowMs,
            plannedMinutes = s.workMinutes,
            completedMinutes = s.workMinutes,
            presetLabel = s.preset.label,
            interruptions = s.interruptions
        )
    }

    fun pause() {
        if (_state.value.phase == Phase.WORK || _state.value.phase == Phase.BREAK) {
            _state.update { it.copy(phase = Phase.PAUSED) }
        }
    }

    fun resume(nowMs: Long = System.currentTimeMillis()) {
        if (_state.value.phase == Phase.PAUSED) {
            _state.update {
                it.copy(
                    phase = if (it.remainingMs == it.totalMs && it.sessionsCompleted == 0) Phase.WORK
                    else if (it.totalMs == it.breakMinutes * 60_000L) Phase.BREAK else Phase.WORK,
                    phaseStartedAtMs = nowMs
                )
            }
        }
    }

    /** "5 more minutes" snooze from the blocking overlay. */
    fun snoozeFiveMinutes(nowMs: Long = System.currentTimeMillis()) {
        _state.update {
            it.copy(snoozedUntilMs = nowMs + 5 * 60_000L, interruptions = it.interruptions + 1)
        }
    }

    /** Emergency pause from the blocking overlay (counts as an interruption). */
    fun emergencyPause() {
        _state.update { it.copy(phase = Phase.PAUSED, interruptions = it.interruptions + 1) }
    }

    fun recordDistraction() {
        _state.update { it.copy(interruptions = it.interruptions + 1) }
    }

    fun reset() {
        _state.value = TimerState()
    }

    /** "MM:SS" remaining time. */
    fun formatRemaining(state: TimerState = _state.value): String {
        val totalSec = (state.remainingMs / 1000L).coerceAtLeast(0)
        val m = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(m, sec)
    }

    companion object {
        const val TICK_MS = 1000L
    }
}

/** Completed-session storage hook. */
fun interface FocusSessionRecorder {
    suspend fun onWorkPhaseCompleted(
        startedAtMs: Long,
        endedAtMs: Long,
        plannedMinutes: Int,
        completedMinutes: Int,
        presetLabel: String,
        interruptions: Int
    )

    companion object {
        val NONE = FocusSessionRecorder { _, _, _, _, _, _ -> }
    }
}
