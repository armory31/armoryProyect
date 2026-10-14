package com.ruben.spotitube.innertube.utils

import kotlinx.coroutines.CancellationException

/**
 * Executes the given [block] and returns a [Result] encapsulating the result of execution.
 * Unlike standard [runCatching], any [CancellationException] thrown inside [block]
 * is rethrown immediately to maintain structured concurrency in Kotlin coroutines.
 */
inline fun <T, R> T.runCatchingCancellable(block: T.() -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/**
 * Executes the given [block] and returns a [Result] encapsulating the result of execution.
 * Unlike standard [runCatching], any [CancellationException] thrown inside [block]
 * is rethrown immediately to maintain structured concurrency in Kotlin coroutines.
 */
inline fun <R> runCatchingCancellable(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
