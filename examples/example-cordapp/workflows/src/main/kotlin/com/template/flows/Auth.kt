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
package com.template.flows

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AbstractUser
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.auth.User

class MySimpleAuthProvider : AuthProvider {
  override fun authenticate(authInfo: JsonObject, callback: Handler<AsyncResult<User>>) {
    try {
      val username =
        authInfo.getString("username") ?: throw RuntimeException("no username found")
      callback.handle(Future.succeededFuture(MySimpleUser(username)))
    } catch (err: Throwable) {
      callback.handle(Future.failedFuture(err))
    }
  }
}

class MySimpleUser(private val userName: String) : AbstractUser() {
  override fun doIsPermitted(
    permission: String,
    callback: Handler<AsyncResult<Boolean>>
  ) {
    callback.handle(Future.succeededFuture(true))
  }

  override fun principal(): JsonObject {
    val result = JsonObject()
    result.put("username", userName)
    return result
  }

  override fun setAuthProvider(provider: AuthProvider) {}
}
