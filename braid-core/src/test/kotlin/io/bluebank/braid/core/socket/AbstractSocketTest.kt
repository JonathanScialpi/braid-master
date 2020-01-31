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

import io.bluebank.braid.core.jsonrpc.MockSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AbstractSocketTest {
  @Test
  fun `that an exception from a listener is handled`() {
    val socket = MockSocket<String, String>()
    var counter = 0

    val listener = object : SocketListener<String, String> {
      override fun onRegister(socket: Socket<String, String>) {
        ++counter
      }

      override fun onData(socket: Socket<String, String>, item: String) {
        ++counter
        error("data handler failure")
      }

      override fun onEnd(socket: Socket<String, String>) {
        ++counter
        error("end handler failure")
      }
    }

    socket.addListener(listener)
    socket.addResponseListener {
      fail("should never receive anything")
    }

    socket.process("hello")
    socket.end()
    assertEquals("all callbacks were hit", 3, counter)
  }
}