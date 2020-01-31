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

import io.bluebank.braid.core.logging.LogInitialiser
import io.bluebank.braid.server.JsonRPCServerBuilder.Companion.createServerBuilder
import io.vertx.core.Vertx
import io.vertx.ext.auth.shiro.ShiroAuth
import io.vertx.ext.auth.shiro.ShiroAuthOptions
import io.vertx.ext.auth.shiro.ShiroAuthRealmType
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

fun main(args: Array<String>) {
  LogInitialiser.init()
  val vertx = Vertx.vertx()
  val server = createServerBuilder()
    .withVertx(vertx)
    .withService(CalculatorService())
    .withService(TimeService(vertx))
    .withAuthProvider(getAuthProvider(vertx))
    .build()

  server.start()
}

/**
 * We could use any auth provider
 */
private fun getAuthProvider(vertx: Vertx): ShiroAuth {
  val config = json {
    obj("properties_path" to "classpath:auth/shiro.properties")
  }
  return ShiroAuth.create(
    vertx,
    ShiroAuthOptions().setConfig(config).setType(ShiroAuthRealmType.PROPERTIES)
  )
}