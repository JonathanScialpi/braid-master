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
package io.bluebank.braid.corda.rest

import io.bluebank.braid.corda.rest.docs.DocsHandler
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.LocalSessionStore
import javax.ws.rs.core.Response.Status.TEMPORARY_REDIRECT
import kotlin.reflect.KCallable

/**
 * This class encapsulates a simpler way of setting up a Rest Service (
 * for those moments that we need one
 *
 * An instance of this class provides convenient methods for setting up HTTP methods
 * bound to method references. It automatically serves a swagger json and UI.
 *
 * This class works with Kotlin - work is needed to make it work well with other JVM languages.
 */
class RestMounter(
  private val config: RestConfig = RestConfig(),
  private val router: Router,
  private val vertx: Vertx
) {

  companion object {
    private val log = loggerFor<RestMounter>()

    fun mount(config: RestConfig, router: Router, vertx: Vertx) {
      RestMounter(config, router, vertx)
    }
  }

  private val path =
    config.apiPath.trim().dropWhile { it == '/' }.dropLastWhile { it == '/' }
  private val swaggerPath =
    config.swaggerPath.trim().dropWhile { it == '/' }.dropLastWhile { it == '/' }
  private val swaggerJsonPath = if (swaggerPath.isBlank()) {
    "swagger.json"
  } else {
    "$swaggerPath/swagger.json"
  }
  private val swaggerStaticPath = if (swaggerPath.isBlank()) {
    "*"
  } else {
    "$swaggerPath/*"
  }

  val docsHandler: DocsHandler
  private val cookieHandler by lazy { CookieHandler.create() }
  private val sessionHandler by lazy {
    SessionHandler.create(
      LocalSessionStore.create(
        vertx
      )
    )
  }
  private val basicAuthHandler by lazy { BasicAuthHandler.create(config.authProvider) }
  private val unprotectedRouter = Router.router(vertx)
  private val protectedRouter: Router = Router.router(vertx)
  var currentRouter = unprotectedRouter
  private var groupName: String = ""
  private val protected: Boolean
    get() {
      return currentRouter == protectedRouter
    }

  init {
    if (!config.apiPath.startsWith("/")) throw RuntimeException("path must begin with a /")
    docsHandler = DocsHandlerFactory(config).createDocsHandler()
    mount(config.pathsInit)
  }

  private fun mount(fn: RestMounter.(Router) -> Unit) {
    configureSwaggerAndStatic()
    mountUnprotectedRouter()
    mountProtectedRouter()
    // pass control to caller to setup rest bindings
    this.fn(router)
    log.info("REST end point bound to ${config.hostAndPortUri}/$path")
  }

  private fun mountUnprotectedRouter() {
    router.mountSubRouter("/$path", unprotectedRouter)
  }

  private fun configureSwaggerAndStatic() {
    // configure the swagger json
    router.get("/$swaggerJsonPath").handler(docsHandler)
    log.info("swagger json bound to ${config.hostAndPortUri}/$swaggerJsonPath")

    // and now for the swagger static
    val swaggerStaticResource = when (config.openApiVersion) {
      3 -> "swagger-2"
      else -> error("unrecognised open api version")
    }
    val sh = StaticHandler.create(swaggerStaticResource)
    router.getWithRegex("/$swaggerPath").handler {
      if (it.request().path().endsWith("/")) {
        sh.handle(it)
      } else {
        it.response().putHeader("Location", "/$swaggerPath/")
          .setStatusCode(TEMPORARY_REDIRECT.statusCode).end()
      }
    }
    router.get("/$swaggerStaticPath").handler(sh)
    log.info("Swagger UI bound to ${config.hostAndPortUri}/$swaggerPath")
  }

  private fun mountProtectedRouter() {
    validateAuthSchemaAndProvider()
    if (config.authSchema == AuthSchema.None) return
    currentRouter = protectedRouter

    router.mountSubRouter("/$path", protectedRouter)
    when (config.authSchema) {
      AuthSchema.Basic -> {
        protectedRouter.route().handler(cookieHandler)
        protectedRouter.route().handler(sessionHandler)
        protectedRouter.route().handler(basicAuthHandler)
      }
      AuthSchema.Token -> {
        protectedRouter.route()
          .handler(JWTAuthHandler.create(config.authProvider as JWTAuth))
      }
      else -> {
        // don't add any auth provider
      }
    }
  }

  private fun validateAuthSchemaAndProvider() {
    if (config.authSchema != AuthSchema.None && config.authProvider == null) throw RuntimeException(
      "authprovider cannot be null for ${config.authSchema}"
    )
  }

  /**
   * Define a grouping of method bindings
   */
  fun group(groupName: String, fn: () -> Unit) {
    this.groupName.let { old ->
      this.groupName = groupName
      try {
        fn()
      } finally {
        this.groupName = old
      }
    }
  }

  /**
   * bind the enclosed bindings declared in [fn] to the unprotected, publicly accessible, router
   */
  fun unprotected(fn: () -> Unit) {
    this.currentRouter.let { old ->
      currentRouter = unprotectedRouter
      try {
        fn()
      } finally {
        currentRouter = old
      }
    }
  }

  /**
   * bind the enclosed bindings declared in [fn] to the protected router
   */
  fun protected(fn: () -> Unit) {
    this.currentRouter.let { old ->
      if (config.authSchema == AuthSchema.None) {
        log.warn("protected scope in REST bindings is ineffective because authSchema is ${config.authSchema}")
      } else {
        currentRouter = protectedRouter
      }
      try {
        fn()
      } finally {
        currentRouter = old
      }
    }
  }

  /**
   * Get access to the raw Vertx router
   * This method can be used to bind additional behaviours (e.g. serving a static website)
   */
  @Suppress("unused")
  fun router(fn: Router.() -> Unit) {
    currentRouter.fn()
  }

  @JvmName("getFuture")
  fun <Response> get(path: String, fn: KCallable<Future<Response>>) {
    bind(HttpMethod.GET, path, fn)
  }

  fun <Response> get(path: String, fn: KCallable<Response>) {
    bind(HttpMethod.GET, path, fn)
  }

  @JvmName("getRaw")
  fun get(path: String, fn: RoutingContext.() -> Unit) {
    bind(HttpMethod.GET, path, fn)
  }

  @JvmName("putFuture")
  fun <Response> put(path: String, fn: KCallable<Future<Response>>) {
    bind(HttpMethod.PUT, path, fn)
  }

  fun <Response> put(path: String, fn: KCallable<Response>) {
    bind(HttpMethod.PUT, path, fn)
  }

  @JvmName("putRaw")
  fun put(path: String, fn: RoutingContext.() -> Unit) {
    bind(HttpMethod.PUT, path, fn)
  }

  fun <Response> post(path: String, fn: KCallable<Response>) {
    bind(HttpMethod.POST, path, fn)
  }

  @JvmName("postFuture")
  fun <Response> post(path: String, fn: KCallable<Future<Response>>) {
    bind(HttpMethod.POST, path, fn)
  }

  @JvmName("postRaw")
  fun post(path: String, fn: RoutingContext.() -> Unit) {
    bind(HttpMethod.POST, path, fn)
  }

  fun <Response> delete(path: String, fn: KCallable<Response>) {
    bind(HttpMethod.DELETE, path, fn)
  }

  @JvmName("deleteFuture")
  fun <Response> delete(path: String, fn: KCallable<Future<Response>>) {
    bind(HttpMethod.DELETE, path, fn)
  }

  @JvmName("deleteRaw")
  fun delete(path: String, fn: RoutingContext.() -> Unit) {
    bind(HttpMethod.DELETE, path, fn)
  }

  private fun bind(method: HttpMethod, path: String, fn: RoutingContext.() -> Unit) {
    currentRouter.route(method, path).handler { it.fn() }
    docsHandler.add(groupName, protected, method, path, fn)
  }

  private fun <Response> bind(
    method: HttpMethod,
    path: String,
    fn: KCallable<Future<Response>>
  ) {
    currentRouter.route(method, path).bind(fn)
    docsHandler.add(groupName, protected, method, path, fn)
  }

  @JvmName("bindMethod0")
  public fun <Response> bind(method: HttpMethod, path: String, fn: KCallable<Response>) {
    currentRouter.route(method, path).bind(fn)
    docsHandler.add(groupName, protected, method, path, fn)
  }
}
