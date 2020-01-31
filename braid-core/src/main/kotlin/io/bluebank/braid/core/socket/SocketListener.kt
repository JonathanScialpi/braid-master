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

/**
 * Implemented by a listener to a socket
 */
interface SocketListener<Receive, Send> {

  /**
   * Called by [AbstractSocket] to inform this listener that it will be receiving events from [socket]
   * Following this call, this listener is allowed to call [Socket] methods such as [Socket.write]
   */
  fun onRegister(socket: Socket<Receive, Send>)

  /**
   * Called when a new [item] is received on a given registered socket
   */
  fun onData(socket: Socket<Receive, Send>, item: Receive)

  /**
   * called when a registered socket has closed
   */
  fun onEnd(socket: Socket<Receive, Send>)
}