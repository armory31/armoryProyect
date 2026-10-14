package com.ruben.spotitube.innertube.utils

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCatchingCancellableTest {

    @Test
    fun runCatchingCancellable_normalException_returnsFailureResult() {
        val expectedException = RuntimeException("Normal error")

        val result = runCatchingCancellable {
            throw expectedException
        }

        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    @Test(expected = CancellationException::class)
    fun runCatchingCancellable_cancellationException_rethrowsException() {
        runCatchingCancellable {
            throw CancellationException("Cancelled")
        }
    }
}
