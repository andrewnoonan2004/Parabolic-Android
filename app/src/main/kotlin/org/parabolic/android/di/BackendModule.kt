package org.parabolic.android.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import org.parabolic.android.data.DownloadDatabase
import org.parabolic.android.engine.AndroidYtDlpRepository
import org.parabolic.core.YtDlpRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackendBindings {
    @Binds @Singleton abstract fun repository(implementation: AndroidYtDlpRepository): YtDlpRepository
}
@Module
@InstallIn(SingletonComponent::class)
object BackendModule {
    @Provides @Singleton fun json(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Provides @Singleton fun database(@ApplicationContext context: Context): DownloadDatabase =
        Room.databaseBuilder(context, DownloadDatabase::class.java, "downloads.db").build()
    @Provides fun downloads(database: DownloadDatabase) = database.downloads()
}
