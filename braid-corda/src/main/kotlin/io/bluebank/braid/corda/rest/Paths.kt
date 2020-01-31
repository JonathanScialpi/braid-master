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

internal object Paths {
  val PATH_PARAMS_RE: Regex = """(:([^\/]+))""".toRegex()
}

internal fun String.toSwaggerPath(): String {
  return Paths.PATH_PARAMS_RE.replace(this) { matchResult ->
    assert(matchResult.groups.size == 3)
    val match = matchResult.groups[2]!!.value
    "{$match}"
  }
}

internal fun String.vertxPathParams(): List<String> {
  return Paths.PATH_PARAMS_RE.findAll(this).map {
    assert(it.groups.size == 3) {
      "expected 3 groups in match, but got ${it.groups.size}"
    }
    it.groups[2]!!.value
  }.toList()
}

