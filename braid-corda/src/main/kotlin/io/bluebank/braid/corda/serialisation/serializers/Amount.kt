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
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import io.bluebank.braid.corda.serialisation.checkHasField
import io.bluebank.braid.corda.serialisation.checkIsTextual
import io.vertx.core.json.Json
import net.corda.core.contracts.Amount
import java.util.*

private const val QUANTITY_FIELD = "quantity"
private const val DISPLAY_TOKEN_SIZE_FIELD = "displayTokenSize"
private const val TOKEN_FIELD = "token"
private const val TOKEN_TYPE_FIELD = "_tokenType"

class AmountSerializer : StdSerializer<Amount<*>>(Amount::class.java) {
  override fun serialize(
    amount: Amount<*>,
    generator: JsonGenerator,
    provider: SerializerProvider
  ) {
    generator.writeStartObject()

    try {
      generator.writeNumberField(QUANTITY_FIELD, amount.quantity)
      generator.writeNumberField(DISPLAY_TOKEN_SIZE_FIELD, amount.displayTokenSize)

      val token = amount.token
      when (token) {
        is Currency -> generator.writeStringField(TOKEN_FIELD, token.currencyCode)
        else -> {
          generator.writeObjectField(TOKEN_FIELD, token)
          generator.writeStringField(TOKEN_TYPE_FIELD, token.javaClass.name)
        }
      }
    } finally {
      generator.writeEndObject()
    }
  }
}

class AmountDeserializer : StdDeserializer<Amount<Any>>(Amount::class.java) {
  override fun deserialize(
    parser: JsonParser,
    context: DeserializationContext
  ): Amount<Any> {
    val node = parseNode(parser)
    checkNode(node, parser)
    return when {
      node.isObject -> {
        val quantity = node[QUANTITY_FIELD].asLong()
        val displayTokenSize = node[DISPLAY_TOKEN_SIZE_FIELD].decimalValue()
        val token = parseToken(node)
        Amount(quantity, displayTokenSize, token)
      }
      node.isTextual -> {
        @Suppress("UNCHECKED_CAST")
        Amount.parseCurrency(node.textValue()) as Amount<Any>
      }
      else -> {
        throw RuntimeException("should never get here")
      }
    }
  }

  private fun parseToken(node: JsonNode): Any = when {
    node.has(TOKEN_TYPE_FIELD) -> {
      val tokenClassName = node[TOKEN_TYPE_FIELD].textValue()
      val tokenClass = Class.forName(tokenClassName)
      Json.mapper.readerFor(tokenClass).readValue<Any>(node[TOKEN_FIELD])
    }
    node.has(TOKEN_FIELD) -> {
      val tokenString = node[TOKEN_FIELD].asText()
      try {
        Currency.getInstance(tokenString)
      } catch (err: IllegalArgumentException) {
        tokenString
      }
    }
    else -> throw RuntimeException("cannot parse amount")
  }

  private fun parseNode(parser: JsonParser): JsonNode = parser.codec.readTree(parser)

  private fun checkNode(node: JsonNode, parser: JsonParser) {
    if (node.isObject) {
      checkHasField(QUANTITY_FIELD, node, parser)
      checkHasField(DISPLAY_TOKEN_SIZE_FIELD, node, parser)
      checkHasField(TOKEN_FIELD, node, parser)
    } else {
      checkIsTextual(node, parser)
    }
  }
}