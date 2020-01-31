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
package io.bluebank.braid.client

import io.bluebank.braid.corda.serialisation.serializers.BraidCordaJacksonInit
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import org.slf4j.Logger

class BraidCordaClient(
  config: BraidClientConfig,
  vertx: Vertx,
  exceptionHandler: (Throwable) -> Unit = this::exceptionHandler,
  closeHandler: (() -> Unit) = this::closeHandler,
  clientOptions: HttpClientOptions = HttpClientOptions()
) : BraidClient(
  config = config,
  vertx = vertx,
  exceptionHandler = exceptionHandler,
  closeHandler = closeHandler,
  clientOptions = clientOptions
) {

  companion object {

    private val log: Logger = loggerFor<BraidCordaClient>()

    init {
      BraidCordaJacksonInit.init()
    }

    fun closeHandler() {
      log.info("closing...")
    }

    fun exceptionHandler(error: Throwable) {
      log.error("exception from socket", error)
    }
  }
}