package com.clayboicardi.game2048.data

import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The D-024 writer (ARCHITECTURE.md §4.5, QUALITY.md §1.6): conflation without cancellation, the
 * swallowed `IOException`, and the D-044 durable reports and retry. The writer runs in
 * `backgroundScope` on the test scheduler.
 */
class ConflatedWriterTest {
    @get:Rule val main = MainDispatcherRule()

    /** F07-AC5 */
    @Test
    fun conflatesWithoutCancellingInFlightWrite() = runTest {
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        val completed = mutableListOf<Int>()
        val writer =
            ConflatedWriter<Int>(
                backgroundScope,
                write = { v ->
                    started += v
                    if (v == 1) gate.await()
                    completed += v
                },
            )

        // The AC: v1 starts and suspends on the gate; v2…v100 arrive while it is in flight.
        writer.submit(1)
        testScheduler.runCurrent()
        assertThat(started).containsExactly(1)
        for (v in 2..100) writer.submit(v)
        testScheduler.runCurrent()
        assertThat(started).containsExactly(1)
        gate.complete(Unit)
        writer.awaitIdle()
        // v1 completed (never cancelled), then exactly one more write: the latest.
        assertThat(completed).containsExactly(1, 100).inOrder()
        assertThat(started).containsExactly(1, 100).inOrder()
        // D-221: every submission is counted, the 98 conflated ones included.
        assertThat(writer.submissions).isEqualTo(100L)

        // QUALITY.md §1.6 (a) and (b): 1,000 submissions while one write is suspended produce
        // exactly 2 writes, the in-flight one and the latest.
        val gate2 = CompletableDeferred<Unit>()
        val writer2 =
            ConflatedWriter<Int>(
                backgroundScope,
                write = { v ->
                    started += v
                    if (v == 101) gate2.await()
                    completed += v
                },
            )
        started.clear()
        completed.clear()
        writer2.submit(101)
        testScheduler.runCurrent()
        for (v in 102..1101) writer2.submit(v)
        testScheduler.advanceUntilIdle()
        assertThat(started).containsExactly(101)
        assertThat(completed).isEmpty()
        gate2.complete(Unit)
        writer2.awaitIdle()
        assertThat(completed).containsExactly(101, 1101).inOrder()
        assertThat(writer2.submissions).isEqualTo(1001L)
        assertThat(writer.failures.get()).isEqualTo(0)
        assertThat(writer2.failures.get()).isEqualTo(0)
    }

    /** F07-AC6 */
    @Test
    fun ioFailureIsSwallowedAndNextWriteSucceeds() = runTest {
        val attempted = mutableListOf<String>()
        val written = mutableListOf<String>()
        val writer =
            ConflatedWriter<String>(
                backgroundScope,
                write = { v ->
                    attempted += v
                    if (attempted.size == 1) throw IOException("disk full")
                    written += v
                },
            )

        writer.submit("v1")
        writer.awaitIdle()
        writer.submit("v2")
        writer.awaitIdle()

        // No exception escaped: runTest fails on an uncaught exception in backgroundScope.
        assertThat(writer.failures.get()).isEqualTo(1)
        assertThat(attempted).containsExactly("v1", "v2").inOrder()
        assertThat(written).containsExactly("v2")
    }

    /** F07-AC31 */
    @Test
    fun reportsWrittenValuesAndRetriesFailedOne() = runTest {
        // (a) The throwing attempt reports nothing; one retry rewrites v1; a second retry is a
        // no-op.
        var failing = true
        val attempts = mutableListOf<String>()
        val reported = mutableListOf<String>()
        val a =
            ConflatedWriter<String>(
                backgroundScope,
                write = { v ->
                    attempts += v
                    if (failing) throw IOException("I/O error")
                },
                onWritten = { reported += it },
            )
        a.submit("v1")
        a.awaitIdle()
        assertThat(attempts).containsExactly("v1")
        assertThat(reported).isEmpty()
        failing = false
        a.retryFailed()
        a.awaitIdle()
        assertThat(attempts).containsExactly("v1", "v1").inOrder()
        assertThat(reported).containsExactly("v1")
        a.retryFailed()
        a.awaitIdle()
        testScheduler.advanceUntilIdle()
        assertThat(attempts).containsExactly("v1", "v1").inOrder()
        assertThat(reported).containsExactly("v1")
        assertThat(a.failures.get()).isEqualTo(1)
        assertThat(a.submissions).isEqualTo(2L)

        // (b) A newer submission makes the retry a no-op: one more write, v2.
        failing = true
        attempts.clear()
        reported.clear()
        val b =
            ConflatedWriter<String>(
                backgroundScope,
                write = { v ->
                    attempts += v
                    if (failing) throw IOException("I/O error")
                },
                onWritten = { reported += it },
            )
        b.submit("v1")
        b.awaitIdle()
        failing = false
        b.submit("v2")
        b.retryFailed()
        b.awaitIdle()
        testScheduler.advanceUntilIdle()
        assertThat(attempts).containsExactly("v1", "v2").inOrder()
        assertThat(reported).containsExactly("v2")
        assertThat(b.failures.get()).isEqualTo(1)
    }
}
