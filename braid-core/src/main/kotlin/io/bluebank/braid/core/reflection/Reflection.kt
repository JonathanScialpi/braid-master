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
package io.bluebank.braid.core.reflection

import io.bluebank.braid.core.annotation.ServiceDescription
import io.vertx.core.Future
import rx.Observable
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

fun <T : Any> Class<T>.serviceName(): String {
  return getDeclaredAnnotation(ServiceDescription::class.java)?.name
    ?: simpleName.toLowerCase()
}

fun Method.underlyingGenericType(): Type {
  return genericReturnType.underlyingGenericType()
}

fun Type.isStreaming() = this.actualType() == Observable::class.java

/**
 * If this is an Observable<*> or a Future<*> this will return
 * Observable or Future (as _this_ will actually be a ParametrizedType.
 *
 * An Int will just return itself.
 */
fun Type.actualType() = (this as? ParameterizedType)?.rawType ?: this

/**
 * For ParametrizedTypes, this returns the underlying parameter.
 *
 * E.g. Observerble<Int> would return Int
 *
 * The slight subtlety here is that anything with underlying types is passed around
 * as a ParametrizedType.
 */
fun Type.underlyingGenericType(): Type {
  return when (this) {
    is ParameterizedType -> {
      when {
        this.isAsynResponse() -> getGenericParameterType(0)
        else -> this
      }
    }
    else -> this
  }
}

fun Type.isAsynResponse(): Boolean {
  return when (this) {
    is Class<*> -> this.isAsyncResponse()
    is ParameterizedType -> this.rawType.isAsynResponse()
    else -> false
  }
}

fun <T : Any> Class<T>.isAsyncResponse(): Boolean {
  return when (this) {
    Future::class.java, Observable::class.java -> true
    else -> false
  }
}

private fun Type.getGenericParameterType(index: Int): Type {
  return (this as ParameterizedType).actualTypeArguments[index]
}