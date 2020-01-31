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

/**
 * This class implements a thread-safe lock-free implementation of [Socket] listener notifications
 */
abstract class AbstractSocket<Receive, Send> : Socket<Receive, Send> {

  companion object {
    private val logger = loggerFor<AbstractSocket<*, *>>()
  }

  private var listeners = emptyList<SocketListener<Receive, Send>>()
  override fun addListener(listener: SocketListener<Receive, Send>): Socket<Receive, Send> {
    listeners += listener // deep copy - slow but safe
    listener.onRegister(this)
    return this
  }

  protected fun Socket<Receive, Send>.onData(item: Receive): Socket<Receive, Send> {
    listeners.forEach {
      try {
        it.onData(this, item)
      } catch (err: Throwable) {
        logger.error("failed to dispatch onData", err)
      }
    }
    return this
  }

  protected fun onEnd() {
    listeners.forEach {
      try {
        it.onEnd(this)
      } catch (err: Throwable) {
        logger.error("failed to dispatch onEnd", err)
      }
    }
    // we will only notify the listeners once and release all resources
    listeners = emptyList()
  }
}