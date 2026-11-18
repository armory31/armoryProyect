/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

data class QuickPicksSelection(
    val batch: List<QuickPickEntry>,
    val remainingPool: List<QuickPickEntry>,
    val exhausted: Boolean,
)

object QuickPicksSelector {
    fun next(pool: List<QuickPickEntry>, previous: List<QuickPickEntry>): QuickPicksSelection {
        val prevIds = previous.map { it.id }.toSet()
        val kept = previous.shuffled().take(5)
        val keptIds = kept.map { it.id }.toSet()
        val fresh = pool.filter { it.id !in prevIds }

        val total = fresh.size + kept.size
        val size = when {
            total >= 40 -> 20
            total in 30..39 -> total / 2
            else -> total
        }

        val neededFresh = (size - kept.size).coerceAtLeast(0)
        val freshPicked = fresh.shuffled().take(neededFresh)
        val batch = (kept + freshPicked).shuffled()

        val discardedPrevIds = prevIds - keptIds
        val remainingPool = pool.filter { it.id !in discardedPrevIds }

        val exhausted = freshPicked.size >= fresh.size

        return QuickPicksSelection(
            batch = batch,
            remainingPool = remainingPool,
            exhausted = exhausted,
        )
    }
}
