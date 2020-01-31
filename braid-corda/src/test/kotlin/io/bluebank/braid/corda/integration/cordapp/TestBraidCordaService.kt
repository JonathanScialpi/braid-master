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
package io.bluebank.braid.corda.integration.cordapp

import io.bluebank.braid.corda.BraidConfig
import io.bluebank.braid.corda.BraidServer
import io.bluebank.braid.corda.rest.RestConfig
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.AsyncResult
import io.vertx.core.Future.failedFuture
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AbstractUser
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.auth.User
import io.vertx.ext.auth.shiro.ShiroAuth
import io.vertx.ext.auth.shiro.ShiroAuthOptions
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import net.corda.core.concurrent.CordaFuture
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.util.concurrent.ConcurrentHashMap

@CordaService
class TestBraidCordaService(private val serviceHub: AppServiceHub) :
  SingletonSerializeAsToken() {

  companion object {
    private val log = loggerFor<TestBraidCordaService>()
    private val cache =
      ConcurrentHashMap<Pair<String, Int>, BraidServer>() // to work around a problem with Corda 3.1. See https://github.com/corda/corda/issues/2898
  }

  private val org = serviceHub.myInfo.legalIdentities.first().name.organisation

  init {
    val port = getBraidPort()
    if (port > 0) {
      val key = org to port
      val service = CustomService()
      cache.computeIfAbsent(key) {
        log.info("Starting Braid service for $org on port $port")
        // DOCSTART 1
        BraidConfig()
          .withThreadPoolSize(1)
          .withFlow("echo", EchoFlow::class)
          .withService(service)
          .withAuthConstructor(this::shiroFactory)
          .withPort(port)
          .withRestConfig(RestConfig().withPaths {
            get("/add", service::add)
            post("/echo", ::doEcho)
          })
          .bootstrapBraid(serviceHub)
        // DOCEND 1
      }
    } else {
      log.info("No port defined for $org")
    }
  }

  fun doEcho(payload: String): CordaFuture<String> {
    return serviceHub.startFlow(EchoFlow(payload)).returnValue
  }

  private fun getBraidPort(): Int {
    val property = "braid.$org.port"
    return System.getProperty(property)?.toInt() ?: when (org) {
      "PartyA" -> 8080
      "PartyB" -> 8081
      else -> 0
    }
  }

  private fun shiroFactory(it: Vertx): AuthProvider {
    val shiroConfig = json {
      obj {
        put("properties_path", "classpath:auth/shiro.properties")
      }
    }
    return ShiroAuth.create(it, ShiroAuthOptions().setConfig(shiroConfig))
  }

  class MyAuthService : AuthProvider {
    override fun authenticate(
      authInfo: JsonObject,
      resultHandler: Handler<AsyncResult<User>>
    ) {
      val username = authInfo.getString("username", "")
      val password = authInfo.getString("password", "")
      if (username == "admin" && password == "admin") {
        resultHandler.handle(succeededFuture(MyAuthUser(username)))
      } else {
        resultHandler.handle(failedFuture("authentication failed"))
      }
    }
  }

  class MyAuthUser(username: String) : AbstractUser() {
    private val principal = JsonObject().put("username", username)

    override fun doIsPermitted(
      permission: String,
      resultHandler: Handler<AsyncResult<Boolean>>
    ) {
      // all is permitted
      resultHandler.handle(succeededFuture(true))
    }

    override fun setAuthProvider(authProvider: AuthProvider?) {
    }

    override fun principal(): JsonObject {
      return principal
    }
  }
}