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
package io.bluebank.braid.core.jsonrpc

import java.util.concurrent.ConcurrentHashMap

class Memoize2<in T1, in T2, out R>(val f: (T1, T2) -> R) : (T1, T2) -> R {
  private val values = ConcurrentHashMap<Pair<T1, T2>, R>()
  override fun invoke(x: T1, y: T2) = values.computeIfAbsent(x to y) { f(x, y) }
}

fun <T1, T2, R> ((T1, T2) -> R).memoize(): (T1, T2) -> R = Memoize2(this)