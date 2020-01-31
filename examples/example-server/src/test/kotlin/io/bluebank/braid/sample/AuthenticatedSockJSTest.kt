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
package io.bluebank.braid.sample

import io.bluebank.braid.core.json.BraidJacksonInit
import io.bluebank.braid.core.jsonrpc.JsonRPCMounter
import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import io.bluebank.braid.core.jsonrpc.JsonRPCResponse
import io.bluebank.braid.core.logging.LogInitialiser
import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.security.AuthenticatedSocket
import io.bluebank.braid.core.service.ConcreteServiceExecutor
import io.bluebank.braid.core.socket.SockJSSocketWrapper
import io.bluebank.braid.core.socket.TypedSocket
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.ext.auth.shiro.ShiroAuth
import io.vertx.ext.auth.shiro.ShiroAuthOptions
import io.vertx.ext.auth.shiro.ShiroAuthRealmType
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.handler.sockjs.SockJSSocket
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

/**
 * Not an automated test.
 * Demonstrates the principles of a secure eventbus over sockjs
 */
class AuthenticatedSockJSTest : AbstractVerticle() {

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      LogInitialiser.init()
      BraidJacksonInit.init()
      Vertx.vertx().deployVerticle(AuthenticatedSockJSTest())
    }

    private val logger = loggerFor<AuthenticatedSockJSTest>()
  }

  override fun start(startFuture: Future<Void>) {
    val router = Router.router(vertx)

    router.route().handler(CookieHandler.create())
    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)))
    val timeService = setupTimeService()
    setupSockJS(router, timeService)
    setupStatic(router)

    val port = 8080
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(port) {
        if (it.succeeded()) {
          logger.info("started on http://localhost:$port")
        } else {
          logger.error("failed to startup", it.cause())
        }
        startFuture.handle(it.mapEmpty<Void>())
      }

  }

  private fun setupTimeService(): TimeService {
    return TimeService(vertx)
  }

  private fun setupStatic(router: Router) {
    router.get().handler(
      StaticHandler.create("streamingtest")
//        .setCachingEnabled(false)
//        .setCacheEntryTimeout(1).setMaxCacheSize(1)
      // enable the above lines to turn off caching - suitable for rapid coding of the UI
    )
  }

  private fun setupSockJS(router: Router, timeService: TimeService) {
    val sockJSHandler = SockJSHandler.create(vertx)
    sockJSHandler.socketHandler { socketHandler(it, timeService) }
    router.route("/api/*").handler(sockJSHandler)
  }

  private fun socketHandler(socket: SockJSSocket, timeService: TimeService) {
    val wrapper = SockJSSocketWrapper.create(socket, vertx)
    val auth = AuthenticatedSocket.create(getAuthProvider())
    val mount = JsonRPCMounter(ConcreteServiceExecutor(timeService), vertx)
    val transformer = TypedSocket.create<JsonRPCRequest, JsonRPCResponse>()
    wrapper.addListener(auth)
    auth.addListener(transformer)
    transformer.addListener(mount)
  }

  private fun getAuthProvider(): ShiroAuth {
    val config = json {
      obj("properties_path" to "classpath:auth/shiro.properties")
    }
    return ShiroAuth.create(
      vertx,
      ShiroAuthOptions().setConfig(config).setType(ShiroAuthRealmType.PROPERTIES)
    )
  }
}

