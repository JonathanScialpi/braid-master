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
package io.bluebank.braid.corda.rest.docs

import io.netty.buffer.ByteBuf
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.nio.ByteBuffer
import javax.ws.rs.core.MediaType
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

fun Type.actualType(): Type {
  return if (this is ParameterizedType && Future::class.java.isAssignableFrom(this.rawType as Class<*>)) {
    actualTypeArguments[0]
  } else {
    this
  }
}

fun Type.isEmptyResponseType(): Boolean {
  val actualReturnType = actualType()
  return (actualReturnType == Unit::class.java ||
    actualReturnType == Void::class.java ||
    actualReturnType.typeName == "void" ||
    actualReturnType.typeName == null)
}

fun Type.mediaType(): String {
  val actualType = this.actualType()
  return when {
    actualType.isBinary() -> MediaType.APPLICATION_OCTET_STREAM
    actualType == String::class.java -> MediaType.TEXT_PLAIN
    else -> MediaType.APPLICATION_JSON
  }
}

fun Type.isBinary(): Boolean {
  return when (this) {
    Buffer::class.java,
    ByteArray::class.java,
    ByteBuffer::class.java,
    ByteBuf::class.java -> true
    else -> false
  }
}

fun KType.getKType(): KType {
  return if (jvmErasure.java == Future::class.java) {
    this.arguments.last().type!!
  } else {
    this
  }
}
