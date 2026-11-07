package org.parabolic.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExecutionGateTest {
    @Test fun `canceling running job releases permit`() = runTest {
        val gate = ExecutionGate(MutableStateFlow(1))
        val entered = CompletableDeferred<Unit>()
        val running = launch { gate.withSlot { entered.complete(Unit); awaitCancellation() } }
        entered.await()
        var secondEntered = false
        val waiting = launch { gate.withSlot { secondEntered = true } }
        runCurrent()
        assertFalse(secondEntered)
        running.cancelAndJoin()
        waiting.join()
        assertTrue(secondEntered)
    }
    @Test fun `canceling waiting job never consumes a permit`() = runTest {
        val gate = ExecutionGate(MutableStateFlow(1))
        val entered = CompletableDeferred<Unit>()
        val running = launch { gate.withSlot { entered.complete(Unit); awaitCancellation() } }
        entered.await()
        val waiting = launch { gate.withSlot { fail("Canceled waiter ran") } }
        runCurrent()
        waiting.cancelAndJoin()
        running.cancelAndJoin()
        gate.withSlot { assertTrue(true) }
    }
    @Test fun `raising limit wakes queued work`() = runTest {
        val limit = MutableStateFlow(1)
        val gate = ExecutionGate(limit)
        val first = launch { gate.withSlot { awaitCancellation() } }
        runCurrent()
        var started = false
        val second = launch { gate.withSlot { started = true } }
        runCurrent()
        assertFalse(started)
        limit.value = 2
        second.join()
        assertTrue(started)
        first.cancelAndJoin()
    }
    @Test fun `lowering limit drains existing jobs`() = runTest {
        val limit = MutableStateFlow(2)
        val gate = ExecutionGate(limit)
        val first = launch { gate.withSlot { awaitCancellation() } }
        val second = launch { gate.withSlot { awaitCancellation() } }
        runCurrent()
        limit.value = 1
        var started = false
        val third = launch { gate.withSlot { started = true } }
        first.cancelAndJoin()
        runCurrent()
        assertFalse(started)
        second.cancelAndJoin()
        third.join()
        assertTrue(started)
    }
    @Test fun `same job is serialized while other jobs proceed`() = runTest {
        val gate = ExecutionGate(MutableStateFlow(4))
        val first = launch { gate.withJob("a") { awaitCancellation() } }
        runCurrent()
        var replacementStarted = false
        val replacement = launch { gate.withJob("a") { replacementStarted = true } }
        runCurrent()
        assertFalse(replacementStarted)
        gate.withJob("b") { assertTrue(true) }
        first.cancelAndJoin()
        replacement.join()
        assertTrue(replacementStarted)
        gate.withJob("a") { assertTrue(true) }
    }
    @Test fun `throwing job does not retain a permit or job lock`() = runTest {
        val gate = ExecutionGate(MutableStateFlow(1))
        try { gate.withJob("a") { gate.withSlot { error("Failure") } } } catch (_: IllegalStateException) { }
        gate.withJob("a") { gate.withSlot { assertTrue(true) } }
    }
}
