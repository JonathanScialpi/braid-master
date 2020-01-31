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
package io.bluebank.braid.corda.server.rpc

import io.bluebank.braid.corda.server.flow.flowLogicType
import kotlin.reflect.*

class RPCCallable<T>(private val flow: KClass<*>, private val fn: KCallable<T>) : KCallable<T> {
  override val annotations: List<Annotation>
    get() = fn.annotations
  override val isAbstract: Boolean
    get() = fn.isAbstract
  override val isFinal: Boolean
    get() = fn.isFinal
  override val isOpen: Boolean
    get() = fn.isOpen
  override val name: String
    get() = fn.name
  override val parameters: List<KParameter>
    get() = fn.parameters
  override val typeParameters: List<KTypeParameter>
    get() = fn.typeParameters
  override val visibility: KVisibility?
    get() = fn.visibility

  override fun call(vararg args: Any?): T {
    return fn.call(*args)
  }

  override fun callBy(args: Map<KParameter, Any?>): T {
    return fn.callBy(args)
  }

  // change the return type for swagger to be able to see
  override val returnType: KType
    get() = flow.flowLogicType()
}
