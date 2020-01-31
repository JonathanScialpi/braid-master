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
package io.bluebank.braid.server

import io.bluebank.braid.core.http.setupAllowAnyCORS
import io.bluebank.braid.core.http.setupOptionsMethod
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.StaticHandler

class JsonRPCVerticle(
  private val rootPath: String, val services: List<Any>, val port: Int,
  private val authProvider: AuthProvider?,
  private val httpServerOptions: HttpServerOptions
) : AbstractVerticle() {

  companion object {
    private val logger = loggerFor<JsonRPCVerticle>()
  }

  private lateinit var router: Router

  override fun start(startFuture: Future<Void>) {
    router = setupRouter()
    setupWebserver(router, startFuture)
  }

  private fun setupWebserver(router: Router, startFuture: Future<Void>) {
    vertx.createHttpServer(httpServerOptions.withCompatibleWebsockets())
      .requestHandler(router)
      .listen(port) {
        if (it.succeeded()) {
          logger.info("started on port $port")
          startFuture.complete()
        } else {
          logger.error("failed to start because", it.cause())
          startFuture.fail(it.cause())
        }
      }
  }

  private fun HttpServerOptions.withCompatibleWebsockets(): HttpServerOptions {
    this.websocketSubProtocols = "undefined"
    return this
  }

  private val servicesRouter: Router = Router.router(vertx)

  private fun setupRouter(): Router {
    val router = Router.router(vertx)
    router.setupAllowAnyCORS()
    router.setupOptionsMethod()
    servicesRouter.post().handler(BodyHandler.create())

    val config = BraidConfig(port, authProvider)
    val serviceMap = ServiceMap(services)
    router.setupSockJSHandler(vertx, serviceMap, config)
    return router
  }
}