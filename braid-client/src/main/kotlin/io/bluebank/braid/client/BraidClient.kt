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
package io.bluebank.braid.client

import io.bluebank.braid.client.invocations.Invocations
import io.bluebank.braid.client.invocations.impl.InvocationsImpl
import io.bluebank.braid.core.json.BraidJacksonInit
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import java.io.Closeable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

open class BraidClient protected constructor(
  config: BraidClientConfig,
  vertx: Vertx,
  exceptionHandler: (Throwable) -> Unit = Invocations.defaultSocketExceptionHandler(),
  closeHandler: (() -> Unit) = Invocations.defaultSocketCloseHandler(),
  clientOptions: HttpClientOptions = InvocationsImpl.defaultClientHttpOptions
) : Closeable, InvocationHandler {

  private val invocations =
    Invocations.create(vertx, config, exceptionHandler, closeHandler, clientOptions)

  companion object {
    init {
      BraidJacksonInit.init()
    }

    fun createClient(
      config: BraidClientConfig,
      vertx: Vertx = Vertx.vertx(),
      exceptionHandler: (Throwable) -> Unit = Invocations.defaultSocketExceptionHandler(),
      closeHandler: (() -> Unit) = Invocations.defaultSocketCloseHandler(),
      clientOptions: HttpClientOptions = InvocationsImpl.defaultClientHttpOptions
    ): BraidClient {
      return BraidClient(config, vertx, exceptionHandler, closeHandler, clientOptions)
    }
  }

  fun activeRequestsCount(): Int {
    return invocations.activeRequestsCount
  }

  @Suppress("UNCHECKED_CAST")
  fun <ServiceType : Any> bind(clazz: Class<ServiceType>): ServiceType {
    return Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz), this) as ServiceType
  }

  override fun close() {
    invocations.close()
  }

  override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
    return invocations.invoke(
      method.name,
      method.genericReturnType,
      args ?: arrayOfNulls<Any>(0)
    )
  }
}
