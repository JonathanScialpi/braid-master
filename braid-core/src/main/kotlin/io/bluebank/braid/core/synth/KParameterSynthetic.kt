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
package io.bluebank.braid.core.synth

import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.createType

class KParameterSynthetic(
  override val name: String,
  val clazz: Class<*>,
  override val annotations: List<Annotation> = emptyList()
) :
  KParameter {

  override val index: Int = 0
  override val isOptional: Boolean = false
  override val isVararg: Boolean = false
  override val kind: KParameter.Kind = KParameter.Kind.VALUE
  override val type: KType = clazz.kotlin.createType()

}

