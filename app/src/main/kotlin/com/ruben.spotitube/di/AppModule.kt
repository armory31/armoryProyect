/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.ruben.spotitube.constants.MaxSongCacheSizeKey
import com.ruben.spotitube.db.MusicDatabase
import com.ruben.spotitube.utils.dataStore
import com.ruben.spotitube.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.NavigableSet
import java.util.TreeSet
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

private class LazyCache(
    private val create: () -> SimpleCache,
) : Cache {
    private val lock = Any()
    @Volatile private var cache: SimpleCache? = null

    private fun delegate(): SimpleCache =
        cache ?: synchronized(lock) { cache ?: create().also { cache = it } }

    override fun addListener(key: String, listener: Cache.Listener) =
        delegate().addListener(key, listener)

    override fun removeListener(key: String, listener: Cache.Listener) =
        delegate().removeListener(key, listener)

    override fun getCachedSpans(key: String): NavigableSet<CacheSpan> =
        delegate().getCachedSpans(key)

    override fun getKeys(): NavigableSet<String> =
        TreeSet(delegate().keys)

    override fun getCacheSpace(): Long =
        delegate().cacheSpace

    override fun getUid(): Long =
        delegate().uid

    override fun getCachedLength(key: String, position: Long, length: Long): Long =
        delegate().getCachedLength(key, position, length)

    override fun getCachedBytes(key: String, position: Long, length: Long): Long =
        delegate().getCachedBytes(key, position, length)

    override fun applyContentMetadataMutations(key: String, mutations: ContentMetadataMutations) =
        delegate().applyContentMetadataMutations(key, mutations)

    override fun getContentMetadata(key: String): ContentMetadata =
        delegate().getContentMetadata(key)

    override fun startReadWrite(key: String, position: Long, length: Long): CacheSpan =
        delegate().startReadWrite(key, position, length)

    override fun startReadWriteNonBlocking(key: String, position: Long, length: Long): CacheSpan? =
        delegate().startReadWriteNonBlocking(key, position, length)

    override fun startFile(key: String, position: Long, maxLength: Long): File =
        delegate().startFile(key, position, maxLength)

    override fun commitFile(file: File, length: Long) =
        delegate().commitFile(file, length)

    override fun releaseHoleSpan(holeSpan: CacheSpan) =
        delegate().releaseHoleSpan(holeSpan)

    override fun removeSpan(span: CacheSpan) =
        delegate().removeSpan(span)

    override fun removeResource(key: String) =
        delegate().removeResource(key)

    override fun isCached(key: String, position: Long, length: Long): Boolean =
        delegate().isCached(key, position, length)

    override fun release() =
        delegate().release()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MusicDatabase = MusicDatabase.newInstance(context)

    @Singleton
    @Provides
    fun provideDatabaseDao(database: MusicDatabase): com.ruben.spotitube.db.DatabaseDao = database

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
    ): Cache {
        val evictor = when (val cacheSize = context.dataStore.get(MaxSongCacheSizeKey, 1024)) {
            -1 -> NoOpCacheEvictor()
            else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
        }
        return LazyCache {
            SimpleCache(
                context.filesDir.resolve("exoplayer"),
                evictor,
                databaseProvider,
            )
        }
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache =
        LazyCache {
            SimpleCache(context.filesDir.resolve("download"), NoOpCacheEvictor(), databaseProvider)
        }
}
