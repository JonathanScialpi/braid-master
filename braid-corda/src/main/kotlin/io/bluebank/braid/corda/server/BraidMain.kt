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
package io.bluebank.braid.corda.server

import io.bluebank.braid.core.async.catch
import io.bluebank.braid.core.async.onSuccess
import io.bluebank.braid.core.http.HttpServerConfig
import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.utils.toJarsClassLoader
import io.bluebank.braid.core.utils.tryWithClassLoader
import io.vertx.core.Future
import io.vertx.core.Vertx

private val log = loggerFor<BraidMain>()

/**
 * The top level entry point for running braid as an executable
 */
class BraidMain(
  externalVertx: Vertx? = null
) {

  val vertx: Vertx
  private val internallyCreated: Boolean
  private val deploymentIds = mutableListOf<String>()

  init {
    if (externalVertx == null) {
      this.vertx = Vertx.vertx()
      internallyCreated = true
    } else {
      this.vertx = externalVertx
      internallyCreated = false
    }
  }

  fun start(config: BraidServerConfig): Future<String> {
    return tryWithClassLoader(config.cordapps.toJarsClassLoader()) {
      BraidCordaStandaloneServer(
        port = config.port,
        userName = config.user,
        password = config.password,
        nodeAddress = config.networkHostAndPort,
        openApiVersion = config.openApiVersion,
        vertx = vertx,
        httpServerOptions = HttpServerConfig.buildFromPropertiesAndVars()
      )
        .startServer()
        .onSuccess {
          deploymentIds.add(it)
        }
    }
  }

  /**
   * Shutdown this BraidMain together with all the instances started
   */
  fun shutdown(): Future<Void> {
    return shutdownDeployments()
      .compose { shutdownVertx() }
  }

  private fun shutdownVertx(): Future<Void>? {
    return when {
      internallyCreated -> notifyVertxShutdownSkip()
      else -> actualVertxShutdown()
    }
  }

  private fun actualVertxShutdown(): Future<Void> {
    log.info("shutting down vertx")
    return Future.future<Void>().apply { vertx.close(this::handle) }
      .onSuccess {
        log.info("vertx shutdown")
      }
      .catch {
        log.error("failed to stop vertx", it)
      }
  }

  private fun notifyVertxShutdownSkip(): Future<Void>? {
    log.info("vertx was externally created - skipping vertx shutdown")
    return Future.succeededFuture<Void>()
  }

  private fun shutdownDeployments(): Future<Void> {
    log.info("shutting down all braid servers ...")
    return deploymentIds.fold(Future.succeededFuture()) { future, id ->
      future.compose {
        Future.future<Void>().apply { vertx.undeploy(id, this::handle) }
      }.onSuccess {
        log.info("all braid servers shutdown")
      }.catch {
        log.error("failure in shutting down braid servers", it.cause)
      }
    }
  }
}