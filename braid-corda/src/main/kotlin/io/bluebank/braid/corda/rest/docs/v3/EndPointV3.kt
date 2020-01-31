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

import io.bluebank.braid.corda.rest.HTTP_UNPROCESSABLE_STATUS_CODE
import io.bluebank.braid.corda.rest.docs.getKType
import io.bluebank.braid.corda.rest.docs.isBinary
import io.bluebank.braid.corda.rest.docs.isEmptyResponseType
import io.bluebank.braid.corda.rest.docs.javaTypeIncludingSynthetics
import io.bluebank.braid.corda.rest.nonEmptyOrNull
import io.bluebank.braid.core.annotation.MethodDescription
import io.swagger.v3.core.converter.ResolvedSchema
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.*
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.RoutingContext
import java.lang.reflect.Type
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.OK
import kotlin.reflect.KParameter
import kotlin.reflect.KType

abstract class EndPointV3(
  private val groupName: String,
  val protected: Boolean,
  val method: HttpMethod,
  val path: String,
  private val modelContext: ModelContextV3
) {
  companion object {
    fun create(
      groupName: String,
      protected: Boolean,
      method: HttpMethod,
      path: String,
      name: String,
      parameters: List<KParameter>,
      returnType: KType,
      annotations: List<Annotation>,
      modelContext: ModelContextV3
    ): EndPointV3 {
      return KEndPointV3(
        groupName,
        protected,
        method,
        path,
        name,
        parameters,
        returnType.javaTypeIncludingSynthetics(),
        annotations,
        modelContext
      ).resolveTypes()
    }

    fun create(
      groupName: String,
      protected: Boolean,
      method: HttpMethod,
      path: String,
      fn: RoutingContext.() -> Unit,
      modelContext: ModelContextV3
    ): EndPointV3 {
      return ImplicitParamsEndPointV3(groupName, protected, method, path, fn, modelContext).resolveTypes()
    }
  }

  abstract val returnType: Type
  abstract val parameterTypes: List<Type>
  protected abstract val annotations: List<Annotation>

  private val apiOperation: io.swagger.v3.oas.annotations.Operation? by lazy {
    annotations.filterIsInstance<io.swagger.v3.oas.annotations.Operation>().firstOrNull()
  }

  private val methodDescription: MethodDescription? by lazy {
    annotations.filterIsInstance<MethodDescription>().firstOrNull()
  }

  val operationId: String
    get() {
      return path.dropWhile { it == '/' }.replace('/', '_').replace('-', '_')
    }

  val description: String
    get() {
      return methodDescription?.description?.nonEmptyOrNull()
        ?: apiOperation?.description?.nonEmptyOrNull()
        ?: ""
    }

  internal fun resolveTypes(): EndPointV3 {
    modelContext.addType(this.returnType)
    this.parameterTypes.forEach {
      modelContext.addType(it)
    }
    return this
  }

  fun toOperation(): Operation {
    val operation = Operation()
      .operationId(operationId)
      // todo .consumes(consumes)
      .description(description)
      .parameters(toSwaggerParams())
      .requestBody(mapBodyParameter())
      .addTagsItem(groupName)

    if (protected) {
      operation.addSecurityItem(SecurityRequirement().addList(DocsHandlerV3.SECURITY_DEFINITION_NAME, listOf()))
    }

    operation.responses(
      ApiResponses()
        .addApiResponse(OK.statusCode.toString(), response(returnType))
        .addApiResponse(
          BAD_REQUEST.statusCode.toString(),
          response(Throwable::class.java).description("the server failed to parse the request")
        )
        .addApiResponse(
          HTTP_UNPROCESSABLE_STATUS_CODE.toString(),
          response(Throwable::class.java).description("the request could not be processed")
        )
    )
    return operation
  }

  protected abstract fun mapBodyParameter(): RequestBody?
  protected abstract fun mapQueryParameters(): List<QueryParameter>
  protected abstract fun mapHeaderParameters(): List<HeaderParameter>
  protected abstract fun mapPathParameters(): List<PathParameter>

  protected open fun toSwaggerParams(): List<Parameter> {
    return mapPathParameters() + mapQueryParameters() + mapHeaderParameters() + annotatedParameters()
  }

  private fun annotatedParameters(): List<Parameter> =
    apiOperation?.parameters?.map { p ->
      Parameter()
        .name(p.name)
        .description(p.description)
        .`$ref`(p.ref)
        // .examples(p.examples)    todo
        .required(p.required)
        .deprecated(p.deprecated)
        .allowEmptyValue(p.allowEmptyValue)
    } ?: emptyArray<Parameter>().toList()


  protected fun KType.getSwaggerProperty(): ResolvedSchema {
    return getKType().javaTypeIncludingSynthetics().getSwaggerProperty()
  }

  protected fun KType.getSchema(): Schema<*> {
    return getSwaggerProperty().schema
  }

  protected fun Type.getSwaggerProperty(): ResolvedSchema = modelContext.addType(this)

  private fun response(type: Type): ApiResponse {
    return if (type.isEmptyResponseType()) {
      ApiResponse().description("empty response")
    } else {
      val responseSchema = type.getSwaggerProperty()
      ApiResponse()
        .description("")
        .content(
          Content()
            .addMediaType(
              returnType(type),
              io.swagger.v3.oas.models.media.MediaType().schema(responseSchema.schema)
            )
        )
    }
  }

  private fun returnType(type: Type): String {
    return when {
      type.isBinary() -> MediaType.APPLICATION_OCTET_STREAM
      String::class.java.equals(type) -> MediaType.TEXT_PLAIN
      else -> MediaType.APPLICATION_JSON
    }
  }
}
