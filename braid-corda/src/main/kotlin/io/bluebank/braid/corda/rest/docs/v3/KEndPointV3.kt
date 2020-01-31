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

import io.bluebank.braid.corda.rest.Paths
import io.bluebank.braid.corda.rest.docs.javaTypeIncludingSynthetics
import io.bluebank.braid.corda.rest.parameterName
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.parameters.*
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpMethod.*
import java.lang.reflect.Type
import javax.ws.rs.HeaderParam
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

/**
 * Represents an end point of a function
 */

class KEndPointV3(
  groupName: String,
  protected: Boolean,
  method: HttpMethod,
  path: String,
  val name: String,
  val parameters: List<KParameter>,
  override val returnType: Type,
  override val annotations: List<Annotation>,
  modelContext: ModelContextV3
) : EndPointV3(groupName, protected, method, path, modelContext) {

  init {
    // TODO: check sanity of method parameters and types vs REST/HTTP limitations
  }

  private val contextParameters =
    parameters.filter { it.findAnnotation<Context>() != null }

  private val pathParamNames = Paths.PATH_PARAMS_RE.findAll(path)
    .map { it.groups[2]!!.value }

  private val pathParams = pathParamNames
    .map { paramName ->
      parameters.firstOrNull { it.parameterName() == paramName }
        ?: error("could not bind path parameter with name $paramName")
    }
    .toList()
  
  private val headerParams = parameters
    .subtract(pathParams)
    .filter { it.findAnnotation<HeaderParam>() != null }

  private val bodyParameter: KParameter? =
    when (method) {
      GET, HEAD, DELETE, CONNECT, OPTIONS -> null // can't have body parameters for this - everything that's not a path param, gets bound as query parameter
      else -> parameters.subtract(pathParams).subtract(headerParams).lastOrNull { it.findAnnotation<QueryParam>() == null }
    }

  private val queryParams = parameters.subtract(pathParams).subtract(headerParams).let {
    when (bodyParameter) {
      null -> it - contextParameters
      else -> it - contextParameters - bodyParameter
    }
  }

  override val parameterTypes: List<Type>
    get() = parameters.map { it.type.javaTypeIncludingSynthetics() }

  override fun mapPathParameters(): List<PathParameter> {
    return pathParams.map { param ->
      val swaggerProperty = param.type.getSwaggerProperty()
      val p = PathParameter()
        .name(param.parameterName())
        .schema(swaggerProperty.schema)
      // .example()
      //.property(swaggerProperty)
      //.type(swaggerProperty.type)
      // todo migrate to V3
      //  applyDefaultValueAnnotation(param, p)
      //  applyApiParamDocs(param, p)
      //  applyRequiredAndVarArg(param, p)
      p as PathParameter
    }
  }


  override fun mapQueryParameters(): List<QueryParameter> {
    return queryParams.map { param ->
      val q = QueryParameter()
        .name(param.parameterName())
        .schema(param.type.getSwaggerProperty().schema)

      // todo convert to V3
//      applyDefaultValueAnnotation(param, q)
//      applyApiParamDocs(param, q)
//      applyRequiredAndVarArg(param, q)
      q as QueryParameter
    }
  }

  override fun mapHeaderParameters(): List<HeaderParameter> {
    return headerParams.map { param ->
      val q = HeaderParameter()
        .name(param.parameterName())
        .schema(param.type.getSwaggerProperty().schema)

      // todo convert to V3
//      applyDefaultValueAnnotation(param, q)
//      applyApiParamDocs(param, q)
//      applyRequiredAndVarArg(param, q)
      q as HeaderParameter
    }
  }
// todo convert to V3
// private fun <T : Parameter> applyDefaultValueAnnotation(
//    param: KParameter,
//    q: T
//  ) {
//    param.findAnnotation<DefaultValue>()?.apply {
//      if (value.isNotBlank()) q.de (value)
//    }
//  }
//
//  private fun <T : Parameter> applyApiParamDocs(
//    pathParam: KParameter,
//    p: T
//  ) {
//    pathParam.findAnnotation<ApiParam>()?.apply {
//      if (value.isNotBlank()) p.description(value)
//      if (name.isNotBlank()) p.name(name)
//      if (type.isNotBlank()) p.type(type)
//      if (example.isNotBlank()) p.example(example)
//      if (defaultValue.isNotBlank()) p.setDefaultValue(defaultValue)
//    }
//  }
//
//  private fun <T : Parameter> applyRequiredAndVarArg(
//    param: KParameter,
//    p: T
//  ) {
//    p.required = !param.isOptional && !param.isVararg
//    p.min = if (param.isOptional || param.isVararg) {
//      0
//    } else {
//      p.minItems
//    }
//  }

  override fun mapBodyParameter(): RequestBody? {
    return bodyParameter?.let {
      RequestBody()
        .required(true)
        .description(bodyParameter.name)
        .content(
          Content()
            .addMediaType(
              MediaType.APPLICATION_JSON,
              io.swagger.v3.oas.models.media.MediaType()
                .schema(bodyParameter.type.getSchema())
                .example(example(bodyParameter))
            )
        )

    }
  }

  private fun example(parameter: KParameter): String? {
    return parameter.findAnnotation<io.swagger.v3.oas.annotations.Parameter>()?.example
  }

  override fun toString(): String {
    return "KEndPoint(name='$name', parameters=$parameters, returnType=$returnType)"
  }
}