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

import io.bluebank.braid.core.socket.impl.SockJsSocketImpl
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.handler.sockjs.SockJSSocket

interface SockJSSocketWrapper : Socket<Buffer, Buffer> {
  companion object {
    /**
     * wrap a vertx [SockJSSocket] with a non-blocking [Socket] wrapper
     * @param socket - vertx SockJSSocket
     * @param vertx - vertx instance to be used for scheduling blocking calls
     * @param maxExecutionTime - maximum execution time before the request is killed
     */
    fun create(
      socket: SockJSSocket,
      vertx: Vertx,
      threads: Int,
      maxExecutionTime: Long
    ): Socket<Buffer, Buffer> {
      val nbs = NonBlockingSocket<Buffer, Buffer>(vertx, threads, maxExecutionTime)
      val sjs = SockJsSocketImpl(socket)
      sjs.addListener(nbs)
      return nbs
    }

    /**
     * Create a non-blocking [Socket] wrapper with the maximum execution time set to [NonBlockingSocket.DEFAULT_MAX_EXECUTION_TIME_NANOS].
     * Requests are processed in parallel
     *
     * @param socket - vertx SockJSSocket
     * @param vertx - vertx instance to be used for scheduling blocking calls
     */
    fun create(socket: SockJSSocket, vertx: Vertx, threads: Int) =
      create(socket, vertx, threads, NonBlockingSocket.DEFAULT_MAX_EXECUTION_TIME_NANOS)

    /**
     * Create a non-blocking [Socket] wrapper with the maximum execution time set to [NonBlockingSocket.DEFAULT_MAX_EXECUTION_TIME_NANOS]
     * Requests are processed in parallel. Total number of threads is set to [NonBlockingSocket.DEFAULT_MAX_THREADS]
     *
     * @param socket - vertx SockJSSocket
     * @param vertx - vertx instance to be used for scheduling blocking calls
     */
    fun create(socket: SockJSSocket, vertx: Vertx) = create(
      socket,
      vertx,
      NonBlockingSocket.DEFAULT_MAX_THREADS,
      NonBlockingSocket.DEFAULT_MAX_EXECUTION_TIME_NANOS
    )

    /**
     * Factory for creating a blocking socket wrapper.
     * WARNING: Beware that handlers on this socket should not be long running. Handler will block the event loop!
     */
    fun create(socket: SockJSSocket): Socket<Buffer, Buffer> {
      return SockJsSocketImpl(socket)
    }
  }
}
