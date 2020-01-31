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

import com.google.common.io.Resources
import io.bluebank.braid.corda.rest.RestConfig
import io.bluebank.braid.core.http.HttpServerConfig.Companion.defaultServerOptions
import io.bluebank.braid.core.logging.LogInitialiser
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AuthProvider
import net.corda.core.flows.FlowLogic
import net.corda.core.node.AppServiceHub
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Configuration class for Braid
 * @param port - port that the service is bound on
 * @param rootPath - the HTTP path that is at the root of the API
 * @param registeredFlows - a map from flow name to [FlowLogic] class
 * @param services - map from service name to service instance
 * @param authConstructor - a lambda that is creates a [Vertx] [AuthProvider]
 * @param httpServerOptions - these options control all HTTP transport concerns e.g. TLS
 * @param threadPoolSize - the number of executor threads available for each connected client
 */
data class BraidConfig(
  val port: Int = 8080,
  val rootPath: String = "/api/",
  val registeredFlows: Map<String, Class<out FlowLogic<*>>> = emptyMap(),
  val services: Map<String, Any> = emptyMap(),
  val authConstructor: ((Vertx) -> AuthProvider)? = null,
  val httpServerOptions: HttpServerOptions = defaultServerOptions(),
  val threadPoolSize: Int = 1,
  val vertx: Vertx? = null,
  val restConfig: RestConfig? = null
) {

  companion object {
    private val log = loggerFor<BraidConfig>()

    init {
      LogInitialiser.init()
    }

    @Suppress("unused")
    @JvmStatic
    fun fromResource(resourcePath: String): BraidConfig? {
      val fullConfig = try {
        val file = Resources.toString(Resources.getResource(resourcePath), Charsets.UTF_8)
        JsonObject(file)
      } catch (err: Throwable) {
        val msg = "could not find config resource $resourcePath"
        log.warn(msg, err)
        null
      }
      return if (fullConfig != null) {
        Json.decodeValue(fullConfig.encode(), BraidConfig::class.java)
      } else {
        null
      }
    }
  }

  fun withPort(port: Int) = this.copy(port = port)
  @Suppress("unused")
  fun withRootPath(rootPath: String): BraidConfig {
    val canonical = if (!rootPath.endsWith('/'))
      "$rootPath/"
    else
      rootPath
    return this.copy(rootPath = canonical)
  }

  fun withAuthConstructor(authConstructor: ((Vertx) -> AuthProvider)) =
    this.copy(authConstructor = memoize(authConstructor))

  fun withHttpServerOptions(httpServerOptions: HttpServerOptions) =
    this.copy(httpServerOptions = httpServerOptions)

  fun withService(service: Any) =
    withService(service.javaClass.simpleName.decapitalize(), service)

  @Suppress("MemberVisibilityCanBePrivate")
  fun withService(name: String, service: Any): BraidConfig {
    val map = services.toMutableMap()
    map[name] = service
    return this.copy(services = map)
  }

  @Suppress("unused")
  fun withThreadPoolSize(threadCount: Int): BraidConfig {
    return this.copy(threadPoolSize = threadCount)
  }

  fun withRestConfig(restConfig: RestConfig): BraidConfig {
    return this.copy(restConfig = restConfig)
  }

  inline fun <reified T : FlowLogic<*>> withFlow(name: String, flowClass: KClass<T>) =
    withFlow(name, flowClass.java)

  @Suppress("unused")
  inline fun <reified T : FlowLogic<*>> withFlow(flowClass: KClass<T>) =
    withFlow(flowClass.java)

  fun <T : FlowLogic<*>> withFlow(flowClass: Class<T>) =
    withFlow(flowClass.simpleName.decapitalize(), flowClass)

  fun <T : FlowLogic<*>> withFlow(name: String, flowClass: Class<T>): BraidConfig {
    val map = registeredFlows.toMutableMap()
    map[name] = flowClass
    return this.copy(registeredFlows = map)
  }

  fun withVertx(vertx: Vertx) = this.copy(vertx = vertx)

  internal val protocol: String get() = if (httpServerOptions.isSsl) "https" else "http"

  fun bootstrapBraid(
    serviceHub: AppServiceHub? = null,
    fn: Handler<AsyncResult<String>>? = null
  ) = BraidServer.bootstrapBraid(serviceHub, this, fn)
}

private inline fun <reified T : Any, reified R : Any> memoize(crossinline fn: (T) -> R): (T) -> R {
  val concurrentMap = ConcurrentHashMap<T, R>()
  return { t ->
    concurrentMap.computeIfAbsent(t) {
      fn(t)
    }
  }
}

