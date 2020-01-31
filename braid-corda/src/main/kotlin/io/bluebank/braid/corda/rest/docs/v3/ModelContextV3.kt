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
package io.bluebank.braid.corda.rest.docs.v3

import io.bluebank.braid.corda.rest.docs.isEmptyResponseType
import io.bluebank.braid.corda.swagger.v3.*
import io.bluebank.braid.core.synth.SyntheticModelConverter
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.converter.ResolvedSchema
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import io.vertx.core.Future
import io.vertx.core.json.Json
import net.corda.core.utilities.contextLogger
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class ModelContextV3 {
  companion object {
    private val log = contextLogger()
  }

  private val mutableModels = mutableMapOf<String, Schema<*>>()
  val models: Map<String, Schema<*>> get() = mutableModels
  private val modelConverters = ModelConverters().apply {
    val typeNameConverter = QualifiedTypeNameConverter(Json.mapper)
    addConverter(typeNameConverter)
    addConverter(JSR310ModelConverterV3())
    addConverter(MixinModelConverterV3(Json.mapper))
    addConverter(SuperClassModelConverterV3())
    addConverter(ComposedSchemaFixV3())
    addConverter(CustomModelConverterV3())
    addConverter(SyntheticModelConverter(typeNameConverter))
  }

  init {
    addType(Throwable::class.java)
  }

  fun addType(type: Type): ResolvedSchema {
    // todo move to CustomModelConverter
    val actualType = type.actualType()
    return when {
      actualType.isEmptyResponseType() -> ResolvedSchema()
      else -> {
        try {
          actualType.createSwaggerModels()
            .also { schema ->
              val referencedSchemas = schema.referencedSchemas
              referencedSchemas.keys.forEach { schemaName ->
                mutableModels.compute(schemaName) { _, prev ->
                  retainDiscriminator(prev, referencedSchemas[schemaName])
                }
              }
            }
        } catch (e: Throwable) {
          throw RuntimeException("Unable to convert actual type: $actualType", e)
        }
      }
    }
  }

  fun retainDiscriminator(
    current: Schema<*>?,
    possibleReplacement: Schema<*>?
  ): Schema<*> {
    if (current == null)
      return fixUp(possibleReplacement!!)
    if (current.discriminator != null)
      return current
    return fixUp(possibleReplacement!!)
  }

  private fun fixUp(schema: Schema<*>): Schema<*> {
    if (schema is ComposedSchema) {
      schema.allOf.forEach {
        prepentComponents(it)
      }
    }
    return schema
  }

  private fun prepentComponents(it: Schema<Any>) {
    if (it.`$ref` != null && !it.`$ref`.startsWith("#/components/schemas/"))
      it.`$ref` = "#/components/schemas/" + it.`$ref`
  }

  fun addToSwagger(openApi: OpenAPI): OpenAPI {
    models.forEach { (name, model) ->
      try {
        openApi.schema(name, model)
      } catch (e: Throwable) {
        log.error("Unable to model class:$name", e)
        throw RuntimeException("Unable to model class:$name", e)
      }
    }
    return openApi
  }

  private fun Type.createSwaggerModels(): ResolvedSchema {
    return modelConverters.resolveAsResolvedSchema(
      AnnotatedType(this)
        .resolveAsRef(true)
    )
  }

  private fun Type.actualType(): Type {
    return if (this is ParameterizedType && Future::class.java.isAssignableFrom(this.rawType as Class<*>)) {
      this.actualTypeArguments[0]
    } else {
      this
    }
  }
}

