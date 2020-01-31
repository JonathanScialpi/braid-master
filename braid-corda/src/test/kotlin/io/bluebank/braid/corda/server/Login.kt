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

import io.bluebank.braid.corda.rest.LoginRequest
import io.bluebank.braid.core.async.catch
import io.bluebank.braid.core.async.onSuccess
import io.bluebank.braid.core.http.postFuture
import io.vertx.core.http.HttpClient
import io.vertx.core.json.Json
import io.vertx.ext.unit.TestContext

fun HttpClient.login(context: TestContext): String {
  val async = context.async()
  var token = ""
  this.postFuture(
    "/api/rest/login",
    mapOf("Accept" to "application/json; charset=utf8"),
    body = Json.encode(LoginRequest(user = "user1", password = "test"))
  )
    .onSuccess { response ->
      context.assertEquals(200, response.statusCode(), response.statusMessage())
      response.bodyHandler { body ->
        token = body.toString()
        async.complete()
      }
    }
    .catch(context::fail)

  async.awaitSuccess()
  return token
}

fun Map<String, Any>.addBearerToken(token: String?): Map<String, Any> {
  return if (token == null) this
  else (this.keys + setOf("Authorization")).associate {
    Pair<String, Any>(it, if (it == "Authorization") "Bearer $token" else this[it]!!)
  }
}