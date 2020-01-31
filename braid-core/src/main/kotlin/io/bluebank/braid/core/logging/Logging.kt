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
package io.bluebank.braid.core.logging

import io.vertx.core.logging.SLF4JLogDelegateFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T : Any> loggerFor(): Logger {
  LogInitialiser.init()
  return LoggerFactory.getLogger(T::class.java)
}

object LogInitialiser {
  init {
    System.setProperty(
      "vertx.logger-delegate-factory-class-name",
      SLF4JLogDelegateFactory::class.qualifiedName
    )
  }

  fun init() {
    // will cause init to be called once and once only
  }
}

