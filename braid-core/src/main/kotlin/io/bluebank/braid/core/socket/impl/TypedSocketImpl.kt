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
import io.bluebank.braid.core.socket.Socket
import io.bluebank.braid.core.socket.SocketProcessor
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.Json
import io.vertx.ext.auth.User

class TypedSocketImpl<Receive, Send>(private val receiveClass: Class<Receive>) :
  AbstractSocket<Receive, Send>(),
  SocketProcessor<Receive, Send, Buffer, Buffer> {

  companion object {
    private val log = loggerFor<TypedSocketImpl<*, *>>()
  }

  private lateinit var socket: Socket<Buffer, Buffer>

  override fun onRegister(socket: Socket<Buffer, Buffer>) {
    this.socket = socket
  }

  override fun user(): User? = socket.user()

  override fun onData(socket: Socket<Buffer, Buffer>, item: Buffer) {
    log.trace("decoding item {}", item)
    val decoded = Json.decodeValue(item, receiveClass)
    log.trace("decode to {}", decoded)
    onData(decoded)
  }

  override fun onEnd(socket: Socket<Buffer, Buffer>) {
    log.trace("socket closed")
    onEnd()
  }

  override fun write(obj: Send): Socket<Receive, Send> {
    val s = Json.encodeToBuffer(obj)
    log.trace("writing {} as {}", obj, s)
    socket.write(s)
    return this
  }
}