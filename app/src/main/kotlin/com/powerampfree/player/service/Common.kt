package com.powerampfree.player.service

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Shared logging tag for the playback service family (was PlaybackService.TAG). */
internal const val TAG = "PafService"

/** Poll cadence of the seam watcher that launches the overlap start. */
internal const val WATCHER_PERIOD_MS = 15L

/** An already-completed Future: immediate answers to Media3 session commands. */
internal fun <T> completed(value: T): ListenableFuture<T> =
    object : ListenableFuture<T> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true
        override fun get(): T = value
        override fun get(timeout: Long, unit: TimeUnit): T = value
        override fun addListener(listener: Runnable, executor: Executor) {
            executor.execute(listener)
        }
    }
