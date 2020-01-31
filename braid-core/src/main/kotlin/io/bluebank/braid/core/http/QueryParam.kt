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
package io.bluebank.braid.core.http

import io.vertx.core.MultiMap
import java.net.URLDecoder

private val UTF8 = Charsets.UTF_8.name()

fun String?.parseQueryParams(): MultiMap {
  val mm = MultiMap.caseInsensitiveMultiMap()
  this?.split('&')?.map { it.split('=') }?.forEach { mm.add(URLDecoder.decode(it[0], UTF8), URLDecoder.decode(it[1], UTF8)) }
  return mm
}