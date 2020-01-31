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
import io.bluebank.braid.corda.serialisation.checkIsObject
import io.vertx.core.json.Json
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import java.util.*

const val PRODUCT_TYPE_FIELD = "_productType"
private const val ISSUER_FIELD = "issuer"
private const val PRODUCT_FIELD = "product"

class IssuedSerializer : StdSerializer<Issued<Any>>(Issued::class.java) {
  override fun serialize(
    value: Issued<Any>,
    generator: JsonGenerator,
    provider: SerializerProvider
  ) {
    generator.writeStartObject()
    generator.writeObjectField(ISSUER_FIELD, value.issuer)
    val product = value.product
    when (product) {
      is String -> generator.writeStringField(PRODUCT_FIELD, product)
      is Currency -> generator.writeObjectField(PRODUCT_FIELD, product)
      else -> {
        generator.writeObjectField(PRODUCT_FIELD, product)
        generator.writeObjectField(PRODUCT_TYPE_FIELD, product.javaClass.name)
      }
    }
    generator.writeEndObject()
  }
}

class IssuedDeserializer : StdDeserializer<Issued<Any>>(Issued::class.java) {
  override fun deserialize(
    parser: JsonParser,
    context: DeserializationContext
  ): Issued<Any> {
    val node = parseNode(parser)
    checkNode(node, parser)
    val partyAndRef = parserPartyAndRef(node)
    val product = parseProduct(node)
    return Issued(partyAndRef, product)
  }

  private fun parseNode(parser: JsonParser): JsonNode {
    return parser.readValueAsTree()
  }

  private fun checkNode(node: JsonNode, parser: JsonParser) {
    checkIsObject(node, parser)
    checkHasField(ISSUER_FIELD, node, parser)
    checkHasField(PRODUCT_FIELD, node, parser)
  }

  private fun parseProduct(node: JsonNode): Any {
    return if (node.has(PRODUCT_TYPE_FIELD)) {
      parserProductByType(node)
    } else {
      parseProductFromString(node)
    }
  }

  private fun parseProductFromString(node: JsonNode): Any {
    val productString = node[PRODUCT_FIELD].textValue()
    return try {
      Currency.getInstance(productString)
    } catch (err: IllegalArgumentException) {
      productString
    }
  }

  private fun parserProductByType(node: JsonNode): Any {
    val productClass = Class.forName(node[PRODUCT_TYPE_FIELD].textValue())
    return Json.mapper.readerFor(productClass).readValue<Any>(node[PRODUCT_FIELD])
  }

  private fun parserPartyAndRef(node: JsonNode): PartyAndReference {
    return Json.mapper.readerFor(PartyAndReference::class.java)
      .readValue<PartyAndReference>(node[ISSUER_FIELD])
  }
}