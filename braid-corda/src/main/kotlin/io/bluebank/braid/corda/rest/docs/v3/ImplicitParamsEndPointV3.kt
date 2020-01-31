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

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.*
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.RoutingContext
import java.lang.reflect.Type
import kotlin.reflect.KAnnotatedElement

class ImplicitParamsEndPointV3(
  groupName: String,
  protected: Boolean,
  method: HttpMethod,
  path: String,
  fn: (RoutingContext) -> Unit,
  modelContextV3: ModelContextV3
) : EndPointV3(groupName, protected, method, path, modelContextV3) {

  private val annotated = (fn as KAnnotatedElement)
  override val annotations: List<Annotation> = annotated.annotations

  private val implicitParams: List<io.swagger.v3.oas.annotations.Parameter> =
    annotations.filter {
      it is io.swagger.v3.oas.annotations.Parameters
        || it is io.swagger.v3.oas.annotations.Parameter
    }
      .flatMap {
        when (it) {
          is io.swagger.v3.oas.annotations.Parameters -> it.value.toList()
          is io.swagger.v3.oas.annotations.Parameter -> listOf(it)
          else -> throw IllegalArgumentException()
        }
      }

  override val parameterTypes: List<Type>
    //todo add array schema
    get() = implicitParams.map { parameter ->
      parameter.content
          .map { it.schema.allOf + it.schema.anyOf + it.schema.oneOf + it.schema.implementation }
          .flatMap { it.asIterable() }
          .map { it.java }
    }.flatMap { it.asIterable() }

  override val returnType: Type
    get() {
      return annotations.filterIsInstance<Operation>()
        .flatMap { it.responses.toList() }
        .flatMap { it.content.toList() }
        .map { it.schema.implementation.java }
        .firstOrNull() ?: Unit::class.java
    }

  private val headerParams = implicitParams.filter { it.`in` == ParameterIn.HEADER }
  private val pathParams = implicitParams.filter { it.`in` == ParameterIn.PATH }
  private val queryParams = implicitParams.filter { it.`in` == ParameterIn.QUERY }
  private val bodyParam = annotations
    .filter { it is io.swagger.v3.oas.annotations.parameters.RequestBody }
      .map { it as io.swagger.v3.oas.annotations.parameters.RequestBody }
      .firstOrNull()

  override fun mapBodyParameter(): RequestBody? {
    return bodyParam?.toModel()
  }

  override fun mapHeaderParameters(): List<HeaderParameter> {
    return headerParams.map { headerParam ->
      headerParam.toModel() as HeaderParameter
    }
  }

  override fun mapQueryParameters(): List<QueryParameter> {
    return queryParams.map { queryParam ->
      queryParam.toModel() as QueryParameter
    }
  }

  override fun mapPathParameters(): List<PathParameter> {
    return pathParams.map { pathParam ->
      pathParam.toModel() as PathParameter
    }
  }

//  private fun BodyParameter.setExamples(parameter: io.swagger.v3.oas.annotations.Parameter): BodyParameter {
//    this.examples = parameter.examples.map { it.mediaType to it.value }.toMap()
//    return this
//  }

  private fun io.swagger.v3.oas.annotations.parameters.RequestBody.toModel(): RequestBody {
    return RequestBody()
        .description(description)
        .required(required)
        .content(content.firstOrNull()?.toModel())
  }

  private fun io.swagger.v3.oas.annotations.media.Content.toModel(): Content {
    return Content()
      .addMediaType(this.mediaType,
        MediaType()
        .schema(this.schema.toModel())
        

      )
  }

  private fun io.swagger.v3.oas.annotations.media.Schema.toModel(): Schema<*> {
    return Schema<Any>()
      .name(this.name)
  }

  private fun io.swagger.v3.oas.annotations.Parameter.toModel(): Parameter {
    val ip = this
    val parameter = when (ip.`in`) {
      ParameterIn.QUERY -> QueryParameter()
      ParameterIn.PATH -> PathParameter()
      ParameterIn.HEADER -> HeaderParameter()
      ParameterIn.COOKIE -> CookieParameter()
      ParameterIn.DEFAULT -> TODO()
    }

    return parameter
      .example(ip.example)
      .description(ip.description)
      .schema(ip.schema.implementation.java.getSwaggerProperty().schema)
  }
}