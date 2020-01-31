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
package io.bluebank.braid.core.jsonrpc

import io.vertx.core.json.Json
import java.lang.reflect.Parameter
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

object Converter {
  fun convert(value: Any?, parameter: KParameter) =
    convert(value, parameter.type.jvmErasure.javaObjectType)

  fun convert(value: Any?, parameter: Parameter) = convert(value, parameter.type)

  fun convert(value: Any?, type: KType) = convert(value, type.jvmErasure.javaObjectType)

  fun convert(value: Any?, clazz: Class<*>): Any? {
    return when (value) {
      null -> null
      else -> {
        Json.mapper.convertValue(value, clazz)
      }
    }
  }
}
