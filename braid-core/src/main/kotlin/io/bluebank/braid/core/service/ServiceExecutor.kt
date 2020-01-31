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
package io.bluebank.braid.core.service

import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import rx.Observable

class MethodDoesNotExist(val methodName: String) : Exception()

interface ServiceExecutor {
  fun invoke(request: JsonRPCRequest): Observable<Any>
  fun getStubs(): List<MethodDescriptor>
}

data class MethodDescriptor(
  val name: String,
  val description: String,
  val parameters: Map<String, String>,
  val returnType: String
)
