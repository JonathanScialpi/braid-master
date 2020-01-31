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
package io.bluebank.braid.corda.serialisation.serializers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import net.corda.core.crypto.Base58
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.toBase58

object SecureHashSerializer : JsonSerializer<SecureHash>() {
  override fun serialize(
    obj: SecureHash,
    generator: JsonGenerator,
    provider: SerializerProvider
  ) {
    generator.writeString(obj.bytes.toBase58())
  }
}

class SecureHashDeserializer<T : SecureHash> : JsonDeserializer<T>() {
  override fun deserialize(parser: JsonParser, context: DeserializationContext): T {
    if (parser.currentToken == JsonToken.FIELD_NAME) {
      parser.nextToken()
    }
    try {
      val str = parser.text
      val result = Base58.decode(str).let {
        when (it.size) {
          32 -> SecureHash.SHA256(it)
          else -> throw IllegalArgumentException("Provided string is ${it.size} bytes not 32 bytes in hex: $str")
        }
      }
      return uncheckedCast(result)
    } catch (e: Exception) {
      throw JsonParseException(parser, "Invalid hash ${parser.text}: ${e.message}")
    }
  }
}