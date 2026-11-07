package org.parabolic.android.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import org.parabolic.core.NetworkOptions
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore("settings")
data class UserPreferences(
    val maxConcurrent: Int = 2,
    val network: NetworkOptions = NetworkOptions(),
    val pendingUrl: String? = null,
    val defaultFolderUri: String? = null,
)

@Singleton
class PreferencesRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsStore
    val preferences: Flow<UserPreferences> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map {
        UserPreferences(
            maxConcurrent = (it[MAX_CONCURRENT] ?: 2).coerceIn(1, 4),
            network = NetworkOptions(it[RATE], it[PROXY], it[ARIA] ?: false),
            pendingUrl = it[PENDING_URL],
            defaultFolderUri = it[DEFAULT_FOLDER_URI],
        )
    }
    suspend fun setConcurrency(count: Int) { require(count in 1..4); store.edit { it[MAX_CONCURRENT] = count } }
    suspend fun setNetwork(options: NetworkOptions) {
        store.edit {
            options.bytesPerSecond?.let { rate -> it[RATE] = rate } ?: it.remove(RATE)
            options.proxy?.let { proxy -> it[PROXY] = proxy } ?: it.remove(PROXY)
            it[ARIA] = options.useAria2
        }
    }
    suspend fun setDefaultFolderUri(uri: String?) {
        store.edit {
            if (uri.isNullOrBlank()) it.remove(DEFAULT_FOLDER_URI)
            else it[DEFAULT_FOLDER_URI] = uri
        }
    }
    suspend fun saveSharedUrl(url: String) { store.edit { it[PENDING_URL] = url } }
    suspend fun clearSharedUrl() { store.edit { it.remove(PENDING_URL) } }
    private companion object {
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val RATE = longPreferencesKey("bytes_per_second")
        val PROXY = stringPreferencesKey("proxy")
        val ARIA = booleanPreferencesKey("aria2")
        val PENDING_URL = stringPreferencesKey("pending_url")
        val DEFAULT_FOLDER_URI = stringPreferencesKey("default_folder_uri")
    }
}
