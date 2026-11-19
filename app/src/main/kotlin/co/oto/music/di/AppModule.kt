package co.oto.music.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import co.oto.music.constants.MaxSongCacheSizeKey
import co.oto.music.db.InternalDatabase
import co.oto.music.db.MusicDatabase

import co.oto.music.utils.dataStore
import co.oto.music.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Singleton
    @Provides
    fun provideDao(
        database: InternalDatabase,
    ) = database.dao

    @Singleton
    @Provides
    fun provideInternalDatabase(
        @ApplicationContext context: Context,
    ): InternalDatabase {
        val dbName = co.oto.music.link.tv.TvProfiles.databaseName(context)
        co.oto.music.db.InternalDatabase.migrateLegacyDatabase(context, dbName)
        return Room
            // on a tv each profile has its own library; everywhere else this is the one database
            .databaseBuilder(context, InternalDatabase::class.java, dbName)
        // The one list in MusicDatabase.kt. This copy used to be its own, stopped at 35 -> 36, and
        // every later version reached the phone as "no migration path" -> all tables dropped.
        .addMigrations(*co.oto.music.db.AllMigrations)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setTransactionExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
        .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
        .addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    db.execSQL("PRAGMA busy_timeout = 60000")
                    db.execSQL("PRAGMA cache_size = -16000")
                    db.execSQL("PRAGMA wal_autocheckpoint = 1000")
                    db.execSQL("PRAGMA synchronous = NORMAL")
                } catch (e: Exception) {
                    timber.log.Timber.tag("MusicDatabase").e(e, "Failed to set PRAGMA settings")
                }
            }
        })
        .build()
    }

    @Singleton
    @Provides
    fun provideDatabase(
        internalDatabase: InternalDatabase,
    ): MusicDatabase = MusicDatabase(internalDatabase)

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        val cacheSize = context.dataStore[MaxSongCacheSizeKey] ?: 1024
        return SimpleCache(
            co.oto.music.playback.StoragePlaces.dir(context, co.oto.music.playback.StoredKind.SongCache),
            when (cacheSize) {
                -1 -> NoOpCacheEvictor()
                else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
            },
            databaseProvider,
        )
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        return SimpleCache(
            co.oto.music.playback.StoragePlaces.dir(context, co.oto.music.playback.StoredKind.Downloads),
            NoOpCacheEvictor(),
            databaseProvider
        )
    }

}
