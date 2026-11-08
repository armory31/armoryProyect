/*
 * ARMORY Project - Performance Timing Helper (2026)
 * Licensed Under GPL-3.0
 */

package com.ruben.spotitube.utils

import android.os.SystemClock
import android.os.Trace
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

object PerfTiming {
    const val TAG = "PerfTiming"

    @Volatile
    var appStartTimeMs: Long = 0L

    private val tabClickTimestamps = ConcurrentHashMap<String, Long>()
    private val sectionTimestamps = ConcurrentHashMap<String, Long>()

    fun beginSection(name: String): Long {
        val startMs = SystemClock.elapsedRealtime()
        sectionTimestamps[name] = startMs
        try {
            Trace.beginSection(name.take(127))
        } catch (_: Throwable) {}
        val threadName = Thread.currentThread().name
        Timber.tag(TAG).i("[BEGIN] $name (Thread: $threadName)")
        return startMs
    }

    fun endSection(name: String, explicitStartMs: Long? = null) {
        val nowMs = SystemClock.elapsedRealtime()
        try {
            Trace.endSection()
        } catch (_: Throwable) {}
        val startMs = explicitStartMs ?: sectionTimestamps.remove(name)
        val threadName = Thread.currentThread().name
        val durationStr = if (startMs != null && startMs > 0) " +${nowMs - startMs}ms" else ""
        val appElapsedStr = if (appStartTimeMs > 0) " (appElapsed: +${nowMs - appStartTimeMs}ms)" else ""
        Timber.tag(TAG).i("[END] $name$durationStr$appElapsedStr (Thread: $threadName)")
    }

    fun log(event: String, startMs: Long? = null) {
        val nowMs = SystemClock.elapsedRealtime()
        val threadName = Thread.currentThread().name
        val durationStr = if (startMs != null && startMs > 0) " +${nowMs - startMs}ms" else if (appStartTimeMs > 0) " +${nowMs - appStartTimeMs}ms" else ""
        Timber.tag(TAG).i("$event$durationStr (Thread: $threadName)")
    }

    fun logHomeTiming(event: String) {
        val nowMs = SystemClock.elapsedRealtime()
        val elapsed = if (appStartTimeMs > 0) nowMs - appStartTimeMs else 0L
        val threadName = Thread.currentThread().name
        Timber.tag(TAG).i("[HOME LOG] $event +${elapsed}ms (Thread: $threadName)")
    }

    fun onTabClicked(route: String) {
        val startMs = beginSection("TabNav:$route")
        tabClickTimestamps[route] = startMs
    }

    fun onTabComposedWithData(route: String, hasData: Boolean = true) {
        val startMs = tabClickTimestamps.remove(route)
        val nowMs = SystemClock.elapsedRealtime()
        val threadName = Thread.currentThread().name
        if (startMs != null) {
            endSection("TabNav:$route", startMs)
            Timber.tag(TAG).i("[TAB COMPOSITION] Tab '$route' first composition with data (hasData=$hasData) +${nowMs - startMs}ms (Thread: $threadName)")
        } else {
            Timber.tag(TAG).i("[TAB COMPOSITION] Tab '$route' composed with data (hasData=$hasData) (Thread: $threadName)")
        }
    }

    inline fun <T> measureRoomQuery(queryName: String, block: () -> T): T {
        val threadName = Thread.currentThread().name
        val startMs = beginSection("RoomQuery:$queryName")
        try {
            return block()
        } finally {
            val duration = SystemClock.elapsedRealtime() - startMs
            endSection("RoomQuery:$queryName", startMs)
            Timber.tag(TAG).i("[ROOM QUERY] $queryName +${duration}ms (Thread: $threadName)")
        }
    }

    inline fun <T> measureNetworkCall(callName: String, block: () -> T): T {
        val threadName = Thread.currentThread().name
        val startMs = beginSection("NetworkCall:$callName")
        try {
            return block()
        } finally {
            val duration = SystemClock.elapsedRealtime() - startMs
            endSection("NetworkCall:$callName", startMs)
            Timber.tag(TAG).i("[NETWORK CALL] $callName +${duration}ms (Thread: $threadName)")
        }
    }

    suspend inline fun <T> measureNetworkCallSuspend(callName: String, crossinline block: suspend () -> T): T {
        val threadName = Thread.currentThread().name
        val startMs = beginSection("NetworkCall:$callName")
        try {
            return block()
        } finally {
            val duration = SystemClock.elapsedRealtime() - startMs
            endSection("NetworkCall:$callName", startMs)
            Timber.tag(TAG).i("[NETWORK CALL] $callName +${duration}ms (Thread: $threadName)")
        }
    }
}
