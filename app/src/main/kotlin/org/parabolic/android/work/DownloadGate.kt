package org.parabolic.android.work

import kotlinx.coroutines.flow.map
import org.parabolic.android.data.PreferencesRepository
import org.parabolic.core.ExecutionGate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadGate @Inject constructor(preferences: PreferencesRepository) {
    private val gate = ExecutionGate(preferences.preferences.map { it.maxConcurrent })
    suspend fun <T> withJob(id: String, block: suspend () -> T): T = gate.withJob(id, block)
    suspend fun <T> withSlot(block: suspend () -> T): T = gate.withSlot(block)
}
