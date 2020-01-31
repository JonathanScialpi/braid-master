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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import io.vertx.core.json.Json
import net.corda.core.utilities.NonEmptySet

class NonEmptySetDeserializer(private val elementType: JavaType?) : JsonDeserializer<NonEmptySet<*>>(),
                                                                    ContextualDeserializer {

  constructor() : this(null)

  override fun createContextual(ctx: DeserializationContext?, property: BeanProperty?): JsonDeserializer<*> {
    return if (ctx != null) {
      val type = ctx.contextualType
      val containedType = type.containedType(0)
      NonEmptySetDeserializer(containedType)
    } else {
      this
    }
  }

  override fun deserialize(parser: JsonParser, ctx: DeserializationContext): NonEmptySet<*> {
    val type = if (elementType != null) {
      Json.mapper.typeFactory.constructCollectionType(List::class.java, elementType)
    } else {
      Json.mapper.typeFactory.constructCollectionType(List::class.java, Any::class.java)
    }
    val result = Json.mapper.readValue<List<*>>(parser, type)
    return NonEmptySet.copyOf(result)
  }
}

