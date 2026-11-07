package org.parabolic.core

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Dynamic concurrency with cancellation-safe permits and serialized execution for each retained job directory. */
class ExecutionGate(private val limits: Flow<Int>) {
    private val mutex = Mutex()
    private val active = MutableStateFlow(0)
    private data class Entry(val lock: Mutex = Mutex(), var references: Int = 0)
    private val jobs = mutableMapOf<String, Entry>()

    suspend fun <T> withJob(id: String, block: suspend () -> T): T {
        val entry = mutex.withLock { jobs.getOrPut(id) { Entry() }.also { it.references++ } }
        try { return entry.lock.withLock { block() } }
        finally {
            withContext(NonCancellable) {
                mutex.withLock { if (--entry.references == 0) jobs.remove(id) }
            }
        }
    }

    suspend fun <T> withSlot(block: suspend () -> T): T {
        while (true) {
            combine(active, limits) { count, limit -> count < limit.coerceIn(1, 4) }.first { it }
            val acquired = mutex.withLock {
                if (active.value < limits.first().coerceIn(1, 4)) { active.value++; true } else false
            }
            if (acquired) break
        }
        try { return block() }
        finally { withContext(NonCancellable) { mutex.withLock { active.value-- } } }
    }
}
