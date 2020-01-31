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
package io.bluebank.braid.corda.swagger.v3

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.bluebank.braid.corda.BraidCordaJacksonSwaggerInit
import io.bluebank.braid.corda.rest.docs.v3.QualifiedTypeNameConverter
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import kotlin.test.BeforeTest
import kotlin.test.Test

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "@class")
@JsonSubTypes(
  JsonSubTypes.Type(value = Orange::class),
  JsonSubTypes.Type(value = Apple::class)
)
abstract class Fruit(val size: Int)

class Orange(val type: String, size: Int) : Fruit(size)
class Apple(val type: String, size: Int) : Fruit(size)

class InheritanceTest {
  @BeforeTest
  fun before() {
    BraidCordaJacksonSwaggerInit.init()
  }

  @Test
  fun `that we can create a model`() {
    val converter = ModelConverters().apply {
      addConverter(QualifiedTypeNameConverter(Json.mapper()))
      addConverter(JSR310ModelConverterV3())
      addConverter(MixinModelConverterV3(io.vertx.core.json.Json.mapper))
      addConverter(SuperClassModelConverterV3())
      addConverter(ComposedSchemaFixV3())
      addConverter(CustomModelConverterV3())
    }

    val annotatedType = AnnotatedType(Fruit::class.java).resolveAsRef(true)
    val refSchemas = converter.resolveAsResolvedSchema(annotatedType).referencedSchemas
    val schemas = refSchemas.map { (key, value) -> key as String to value as Schema<*> }.toMap()
    val oapi = OpenAPI().components(Components()).paths(Paths()).info(Info().title("test").version("1.0.0"))

    addToSwagger(schemas, oapi)
    // TODO: code-gen here
  }
}

fun addToSwagger(schemas: Map<String, Schema<*>>, openApi: OpenAPI): OpenAPI {
  schemas.forEach { (name, model) ->
    try {
      openApi.schema(name, model)
    } catch (e: Throwable) {
      throw RuntimeException("Unable to model class: $name", e)
    }
  }
  return openApi
}
