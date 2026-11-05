/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ruben.spotitube.extensions

import com.ruben.spotitube.db.entities.Song




fun <T> List<T>.reversed(reversed: Boolean) = if (reversed) asReversed() else this

fun <T> MutableList<T>.move(
    fromIndex: Int,
    toIndex: Int,
): MutableList<T> {
    add(toIndex, removeAt(fromIndex))
    return this
}

fun <T : Any> List<T>.mergeNearbyElements(
    key: (T) -> Any = { it },
    merge: (first: T, second: T) -> T = { first, _ -> first },
): List<T> {
    if (isEmpty()) return emptyList()

    val mergedList = mutableListOf<T>()
    var currentItem = this[0]

    for (i in 1 until size) {
        val nextItem = this[i]
        if (key(currentItem) == key(nextItem)) {
            currentItem = merge(currentItem, nextItem)
        } else {
            mergedList.add(currentItem)
            currentItem = nextItem
        }
    }
    mergedList.add(currentItem)

    return mergedList
}

// Extension function to filter explicit content for local Song entities


// Extension function to filter explicit content for local Album entities


// No-op for local songs: local Song entities do not contain video metadata to filter reliably
fun List<Song>.filterVideo(enabled: Boolean = true) = this
