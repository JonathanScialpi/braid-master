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
package io.bluebank.braid.core.utils

/**
 * Provides a means of acquiring parameters provided for braid from outside of the JVM process
 * All names are prefixed with [PREFIX]
 * Parameters are looked up in system properties using the convention "$[PREFIX].[name]" in lowercase form
 * and then in environment variables "$[PREFIX].[name]" in uppercase form
 */
object BraidParameterLookup {
  const val PREFIX = "braid"
  fun getParameter(paramName: String, default: String): String {
    return getParameter(paramName) ?: default
  }

  fun getParameter(paramName: String): String? {
    return getParameterSystemProperty(paramName)
      ?: getParameterEnvironmentVariable(paramName)
  }

  private fun getParameterSystemProperty(paramName: String): String? {
    return System.getProperty("$PREFIX.$paramName".toLowerCase())
  }

  private fun getParameterEnvironmentVariable(paramName: String): String? {
    return System.getenv("$PREFIX.$paramName".toUpperCase())
  }
}