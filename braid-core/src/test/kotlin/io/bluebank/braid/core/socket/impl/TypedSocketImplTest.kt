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

import io.bluebank.braid.core.json.BraidJacksonInit
import io.bluebank.braid.core.jsonrpc.MockSocket
import io.bluebank.braid.core.jsonrpc.MockUser
import io.bluebank.braid.core.logging.LogInitialiser
import io.bluebank.braid.core.socket.Socket
import io.bluebank.braid.core.socket.SocketListener
import io.bluebank.braid.core.socket.TypedSocket
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.Json
import org.junit.Test
import kotlin.test.assertEquals

class TypedSocketImplTest {
  companion object {
    init {
      LogInitialiser.init()
      BraidJacksonInit.init()
    }
  }

  @Test
  fun `that we can pass message through a typed socket`() {
    val payload = Payload(1)
    val userId = "fuzz"
    val socket = MockSocket<Buffer, Buffer>(MockUser(userId))
    val typedSocket = TypedSocket.create<Payload, Payload>()
    socket.addListener(typedSocket)

    var listenerCallbacks = 0
    typedSocket.addListener(object : SocketListener<Payload, Payload> {
      override fun onRegister(socket: Socket<Payload, Payload>) {
        ++listenerCallbacks
      }

      override fun onEnd(socket: Socket<Payload, Payload>) {
        ++listenerCallbacks
      }

      override fun onData(socket: Socket<Payload, Payload>, item: Payload) {
        ++listenerCallbacks
        socket.write(item)
      }
    })

    socket.addResponseListener {
      val decoded = Json.decodeValue(it, Payload::class.java)
      assertEquals(payload, decoded, "that payload matches")
    }
    assertEquals(userId, typedSocket.user()!!.principal().getString("id"))
    socket.process(Json.encodeToBuffer(payload))
    socket.end()
    assertEquals(
      1,
      socket.writeCount,
      "that we received and wrote back one typed message"
    )
    assertEquals(3, listenerCallbacks, "that listener was called")
  }
}

data class Payload(val value: Int)