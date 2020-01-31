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
package io.bluebank.braid.corda.rest

import io.bluebank.braid.corda.rest.docs.DocsHandler
import io.bluebank.braid.corda.rest.docs.v3.DocsHandlerV3
import io.swagger.v3.oas.models.security.SecurityScheme
import net.corda.core.utilities.contextLogger

class DocsHandlerFactory(
  val config: RestConfig,
  val path: String = config.apiPath.trim().dropWhile { it == '/' }.dropLastWhile { it == '/' }
) {

  companion object {
    private val log = contextLogger()
  }
  fun createDocsHandler(): DocsHandler {
    return when (config.openApiVersion) {
      3 -> {
        log.info("activating OpenAPI V3")
        DocsHandlerV3(
          swaggerInfo = config.swaggerInfo,
          debugMode = config.debugMode,
          basePath = "${config.hostAndPortUri}/$path",
          auth = getV3SecurityType()
        )
      }
      else -> error("Unknown OpenAPI version ${config.openApiVersion}")
    }
  }

  private fun getV3SecurityType(): SecurityScheme? {
    return when (config.authSchema) {
      AuthSchema.Basic -> {
        SecurityScheme().apply {
          scheme = "basic"
          type = SecurityScheme.Type.HTTP
        }
      }
      AuthSchema.Token -> {
        SecurityScheme().apply {
          scheme = "bearer"
          type = SecurityScheme.Type.HTTP
          bearerFormat = "JWT"
        }
      }
      AuthSchema.None -> null
    }
  }
}