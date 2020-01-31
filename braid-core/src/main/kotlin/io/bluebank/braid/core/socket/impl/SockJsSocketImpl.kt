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
package io.bluebank.braid.core.socket.impl

import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.socket.AbstractSocket
import io.bluebank.braid.core.socket.SockJSSocketWrapper
import io.vertx.core.buffer.Buffer
import io.vertx.ext.auth.User
import io.vertx.ext.web.handler.sockjs.SockJSSocket

class SockJsSocketImpl(private val sockJS: SockJSSocket) :
  AbstractSocket<Buffer, Buffer>(), SockJSSocketWrapper {

  companion object {
    private val log = loggerFor<SockJsSocketImpl>()
  }

  init {
    sockJS.handler { buffer ->
      log.trace("received buffer {}", buffer)
      onData(buffer)
    }
    sockJS.endHandler {
      log.trace("socket closed")
      onEnd()
    }
  }

  override fun user(): User? {
    return null // the socket itself doesn't know the user
  }

  override fun write(obj: Buffer): SockJSSocketWrapper {
    try {
      log.trace("writing {}", obj)
      sockJS.write(obj)
    } catch (err: Throwable) {
      log.error("failure to write onto sockjs socket", err)
    }
    return this
  }
}