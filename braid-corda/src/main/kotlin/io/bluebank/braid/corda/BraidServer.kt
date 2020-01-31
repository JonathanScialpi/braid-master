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
package io.bluebank.braid.corda

import io.bluebank.braid.corda.serialisation.serializers.BraidCordaJacksonInit
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import net.corda.core.node.AppServiceHub
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.addShutdownHook
import java.util.concurrent.ConcurrentHashMap

class BraidServer(private val services: AppServiceHub?, private val config: BraidConfig) {
  companion object {
    private val log = loggerFor<BraidServer>()
    private val servers = ConcurrentHashMap<Int, BraidServer>()

    init {
      BraidCordaJacksonInit.init()
    }

    fun bootstrapBraid(
      serviceHub: AppServiceHub?,
      config: BraidConfig = BraidConfig(),
      fn: Handler<AsyncResult<String>>? = null
    ): BraidServer {
      return servers.computeIfAbsent(config.port) {
        serviceHub?.let {
          log.info("starting up braid server for ${serviceHub.myInfo.legalIdentities.first().name.organisation} on port ${config.port}")
        }
        BraidServer(serviceHub, config)
          .start(fn)
          .apply {
            serviceHub?.registerUnloadHandler {
              shutdown()
            }
          }
      }
    }
  }

  lateinit var vertx: Vertx
    private set

  private val fDeployId = Future.future<String>()
  private val deployId: String?
    get() = fDeployId.result()

  private fun start(fn: Handler<AsyncResult<String>>?): BraidServer {
    vertx = config.vertx ?: Vertx.vertx()
    vertx.deployVerticle(BraidVerticle(services, config)) {
      if (it.failed()) {
        val msg = "failed to start braid server on ${config.port}"
        log.error(msg, it.cause())
        fDeployId.fail(RuntimeException(msg, it.cause()))
      } else {
        log.info("Braid server started successfully on ${config.protocol}://localhost:${config.port}")
        fDeployId.complete(it.result())
      }
      fn?.handle(it)
    }
    addShutdownHook(this::shutdown)
    return this
  }

  fun whenReady(): Future<String> = fDeployId

  fun shutdown() {
    if (deployId != null) {
      log.info("shutting down braid server on port: ${config.port}")
      vertx.undeploy(deployId) {
        vertx.close()
      }
    }
  }
}

