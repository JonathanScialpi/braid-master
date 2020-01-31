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

import io.bluebank.braid.corda.rest.RestMounter
import io.bluebank.braid.corda.router.Routers
import io.bluebank.braid.core.http.setupAllowAnyCORS
import io.bluebank.braid.core.http.setupOptionsMethod
import io.bluebank.braid.core.http.withCompatibleWebsockets
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.WorkerExecutor
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.LoggerFormat
import io.vertx.ext.web.handler.LoggerHandler
import net.corda.core.node.AppServiceHub
import java.net.URL
import java.util.concurrent.TimeUnit

class BraidVerticle(
  private val services: AppServiceHub?,
  private val config: BraidConfig
) : AbstractVerticle() {

  companion object {
    private val log = loggerFor<BraidVerticle>()
  }

  override fun start(startFuture: Future<Void>) {
    // setupRouter takes 3500 msec to run on my machine, even for a simple test
    // (though it's only slow when running Braid in a separate server and using the class path scanner,
    // because when using braid within a cordapp it's reasonably fast).

    // The vertx blocked thread checker would warn, if we used vertx.executeBlocking and that took more than 10 seconds,
    // so instead let's use a WorkerExecutor here, which *can* run for longer than 10 seconds.
    // CordappScanner supports an unbounded number of cordapps -- so what's a safe/optimum timeout value?
    // Let's arbitrarily choose "2 minutes" for now and maybe make it infinite or configurable later.

    val poolSize = 1
    val maxExecuteTime: Long = 60
    val executor = super.vertx.createSharedWorkerExecutor(
      "braid-startup-threadpool",
      poolSize,
      maxExecuteTime,
      TimeUnit.SECONDS
    )

    fun log(start: Long, method: String) {
      log.info("BraidVerticle.$method complete -- ${System.currentTimeMillis() - start} msec")
    }

    executor.executeBlocking<Router>(
      {
        val start = System.currentTimeMillis()
        it.complete(setupRouter())
        log(start, "setupRouter")
      },
      {
        if (it.failed()) {
          throw it.cause();
        }
        val start = System.currentTimeMillis()
        setupWebserver(it.result(), startFuture)
        log(start, "setupWebserver")

        executor.close()

        log.info(
          "Braid server started on",
          "${config.protocol}://localhost:${config.port}${config.rootPath}"
        )
      }
    )
  }

  private fun setupWebserver(router: Router, startFuture: Future<Void>) {
    vertx.createHttpServer(config.httpServerOptions.withCompatibleWebsockets())
      .requestHandler(router)
      .listen(config.port) {
        if (it.succeeded()) {
          log.info("Braid service mounted on ${config.protocol}://localhost:${config.port}${config.rootPath}")
        } else {
          log.error("failed to start server: ${it.cause().message}")
        }
        startFuture.handle(it.mapEmpty())
      }
  }

  private fun setupRouter(): Router {
    val router = Routers.create(vertx, config.port)
    log.info("BraidVerticle.setupRouter starting...")
    router.route().handler(LoggerHandler.create(LoggerFormat.SHORT))
    router.route().handler(BodyHandler.create())
    router.setupAllowAnyCORS()
    router.setupOptionsMethod()
    services?.let {
      router.setupSockJSHandler(vertx, services, config)
    }
    config.restConfig?.let { restConfig ->
      val host = URL(restConfig.hostAndPortUri).host
      val updatedHostAndPort = "${config.protocol}://$host:${config.port}"
      val moddedConfig = restConfig.withHostAndPortUri(updatedHostAndPort)
        .withAuth(config.authConstructor?.invoke(vertx))

      RestMounter.mount(moddedConfig, router, vertx)
    }
    return router
  }
}