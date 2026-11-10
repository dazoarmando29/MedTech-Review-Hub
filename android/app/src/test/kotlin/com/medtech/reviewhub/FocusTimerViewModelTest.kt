package com.medtech.reviewhub

import com.medtech.reviewhub.domain.model.PomodoroPreset
import com.medtech.reviewhub.ui.focus.FocusSessionRecorder
import com.medtech.reviewhub.ui.focus.FocusTimerViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compose-less ViewModel test: the focus Pomodoro state machine is pure
 * (no Android services involved — the blocking overlay lives in
 * AppBlockerService), so phase transitions are testable with plain JUnit.
 */
class FocusTimerViewModelTest {

    @Test
    fun `starts in work phase with configured minutes`() {
        val vm = FocusTimerViewModel()
        vm.configure(PomodoroPreset.POMODORO_25, 25, 5)
        vm.start(nowMs = 0L)
        val state = vm.state.value
        assertEquals(FocusTimerViewModel.Phase.WORK, state.phase)
        assertEquals(25 * 60_000L, state.remainingMs)
    }

    @Test
    fun `ticks count down one second`() = runBlocking {
        val vm = FocusTimerViewModel()
        vm.configure(PomodoroPreset.POMODORO_25, 25, 5)
        vm.start(nowMs = 0L)
        vm.tick(nowMs = 1000L)
        assertEquals(25 * 60_000L - FocusTimerViewModel.TICK_MS, vm.state.value.remainingMs)
    }

    @Test
    fun `work phase transitions to break and records the session`() = runBlocking {
        var recorded = false
        val recorder = FocusSessionRecorder { _, _, _, _, _, _ -> recorded = true }
        val vm = FocusTimerViewModel(recorder)
        vm.configure(PomodoroPreset.POMODORO_25, workMinutes = 1, breakMinutes = 1)
        vm.start(nowMs = 0L)
        var now = 0L
        while (vm.state.value.phase == FocusTimerViewModel.Phase.WORK) {
            now += FocusTimerViewModel.TICK_MS
            vm.tick(nowMs = now)
        }
        assertEquals(FocusTimerViewModel.Phase.BREAK, vm.state.value.phase)
        assertEquals(1, vm.state.value.sessionsCompleted)
        assertTrue(recorded)
    }

    @Test
    fun `break phase transitions to done`() = runBlocking {
        val vm = FocusTimerViewModel()
        vm.configure(PomodoroPreset.POMODORO_25, workMinutes = 1, breakMinutes = 1)
        vm.start(nowMs = 0L)
        var now = 0L
        repeat(130) {
            now += FocusTimerViewModel.TICK_MS
            vm.tick(nowMs = now)
        }
        assertEquals(FocusTimerViewModel.Phase.DONE, vm.state.value.phase)
    }

    @Test
    fun `pause and resume work`() = runBlocking {
        val vm = FocusTimerViewModel()
        vm.configure(PomodoroPreset.POMODORO_25, 25, 5)
        vm.start(nowMs = 0L)
        vm.pause()
        assertEquals(FocusTimerViewModel.Phase.PAUSED, vm.state.value.phase)
        val before = vm.state.value.remainingMs
        vm.tick(nowMs = 10_000L) // paused: no countdown
        assertEquals(before, vm.state.value.remainingMs)
        vm.resume(nowMs = 20_000L)
        assertEquals(FocusTimerViewModel.Phase.WORK, vm.state.value.phase)
    }

    @Test
    fun `snooze defers countdown and counts an interruption`() = runBlocking {
        val vm = FocusTimerViewModel()
        vm.configure(PomodoroPreset.POMODORO_25, 25, 5)
        vm.start(nowMs = 0L)
        vm.snoozeFiveMinutes(nowMs = 0L)
        val before = vm.state.value.remainingMs
        vm.tick(nowMs = 1000L) // within snooze window
        assertEquals(before, vm.state.value.remainingMs)
        assertEquals(1, vm.state.value.interruptions)
        vm.tick(nowMs = 6 * 60_000L) // snooze expired
        assertTrue(vm.state.value.remainingMs < before)
    }

    @Test
    fun `emergency pause counts an interruption`() {
        val vm = FocusTimerViewModel()
        vm.start(nowMs = 0L)
        vm.emergencyPause()
        assertEquals(FocusTimerViewModel.Phase.PAUSED, vm.state.value.phase)
        assertEquals(1, vm.state.value.interruptions)
    }

    @Test
    fun `formats remaining time as MM_SS`() = runBlocking {
        val vm = FocusTimerViewModel()
        vm.configure(PomodoroPreset.POMODORO_50, 50, 10)
        vm.start(nowMs = 0L)
        assertEquals("50:00", vm.formatRemaining())
        vm.tick(nowMs = 1000L)
        assertEquals("49:59", vm.formatRemaining())
    }
}
