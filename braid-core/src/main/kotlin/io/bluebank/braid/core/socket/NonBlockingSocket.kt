/**
 * Copyright 2018 Royal Bank of Scotland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.bluebank.braid.core.socket

import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.Vertx
import io.vertx.core.WorkerExecutor
import io.vertx.ext.auth.User
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ensures that all callbacks are not on the main event loop thread
 * Requests are queued in sequence on a separate thread.
 */
class NonBlockingSocket<R, S>(
  private val vertx: Vertx,
  private val threads: Int = Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
  private val maxExecutionTime: Long = DEFAULT_MAX_EXECUTION_TIME_NANOS
) : AbstractSocket<R, S>(), SocketProcessor<R, S, R, S> {

  companion object {
    private val log = loggerFor<NonBlockingSocket<*, *>>()
    const val DEFAULT_MAX_EXECUTION_TIME_NANOS =
      60L * 60 * 1_000 * 1_000_000 // 1 hour max execution time. Too long?
    private val fountain by lazy {
      val atomic = AtomicInteger(0)
      fun() = "nonblocking-socket-${atomic.getAndIncrement()}"
    }
    /**
     * Default for the maximum number of threads for processing a socket
     * This is set to the max(1, core_count - 1)
     */
    val DEFAULT_MAX_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 1)
    const val THREAD_POOL_NAME = "braid-nonblocking-threadpool"
  }

  private val id = fountain()
  private var socket: Socket<R, S>? = null
  private val pool: WorkerExecutor
    get() = vertx.createSharedWorkerExecutor(
      THREAD_POOL_NAME,
      threads,
      maxExecutionTime
    )

  init {
    log.trace("initialising NonBlockingSocket $id")
  }

  override fun onRegister(socket: Socket<R, S>) {
    log.trace("registered socket for NonBlockingSocket $id")
    if (this.socket != null) {
      log.warn("a socket is already assigned to this object")
    }
    this.socket = socket
  }

  override fun user(): User? = socket?.user()

  override fun onData(socket: Socket<R, S>, item: R) {
    try {
      pool.executeBlocking<R>({ onData(item) }, true, { })
    } catch (err: IllegalStateException) {
      log.info("data item processed during vertx shutdown $item")
    } catch (err: Throwable) {
      log.error("failed to process data item", err)
    }
  }

  override fun onEnd(socket: Socket<R, S>) {
    log.trace("end handler for $id")
    try {
      pool.executeBlocking<Unit>({
        onEnd() // notify all listeners
        this.socket = null // we no longer require this socket
      }, true, {})
    } catch (err: Throwable) {
      when (err) {
        is RejectedExecutionException,
        is IllegalStateException -> log.info("socket closed during vertx shutdown")
        else -> log.error("failed to process end handler", err)
      }
    }
  }

  /**
   * Please note: we expect the calling thread to be on a vertx context
   */
  override fun write(obj: S): Socket<R, S> {
    log.trace("writing {}", obj)
    try {
      if (socket != null) {
        socket?.write(obj)
      } else {
        log.warn("socket was null when writing {}", obj)
      }
    } catch (err: Throwable) {
      log.warn("failed to write $obj to socket", err)
    }
    return this
  }
}