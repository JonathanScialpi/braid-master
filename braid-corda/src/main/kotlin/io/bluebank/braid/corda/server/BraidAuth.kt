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
@file:Suppress("DEPRECATION")

package io.bluebank.braid.corda.server

import io.bluebank.braid.core.security.JWTUtils
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.auth.User
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTOptions
import java.io.File
import java.io.FileOutputStream

class BraidAuth {
  // CAUTION this is copied verbatim from TestServiceApp,
  // which is intended as unit test and may be unsuitable for use in production,
  // and which was copied from https://vertx.io/docs/vertx-web/kotlin/#_jwt_authorisation
  private val tempJKS = File.createTempFile("temp-", ".jceks")
  private val jwtSecret = "secret"
  private lateinit var jwtAuth: JWTAuth
  private val tokenKey = "user" // name the key in the token whose value is the userName

  fun getUserName(user: User): String {
    return user.principal().getString(tokenKey)
      ?: error("expected userName in token")
  }

  fun generateToken(userName: String): String {
    @Suppress("DEPRECATION")
    return jwtAuth.generateToken(
      JsonObject().put(tokenKey, userName),
      JWTOptions().setExpiresInMinutes(24 * 60)
    )
  }

  fun authConstructor(vertx: Vertx): AuthProvider {
    fun ensureJWTKeyStoreExists() {
      val ks = JWTUtils.createSimpleJWTKeyStore(jwtSecret)
      FileOutputStream(tempJKS.absoluteFile).use {
        ks.store(it, jwtSecret.toCharArray())
        it.flush()
      }
    }
    ensureJWTKeyStoreExists()
    @Suppress("DEPRECATION")
    return JWTAuth.create(
      vertx, JsonObject().put(
        "keyStore", JsonObject()
          .put("path", tempJKS.absolutePath)
          .put("type", "jceks")
          .put("password", jwtSecret)
      )
    ).apply {
      jwtAuth = this
    }
  }
}