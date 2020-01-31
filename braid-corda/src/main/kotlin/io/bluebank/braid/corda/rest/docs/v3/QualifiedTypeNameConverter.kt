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

import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import io.bluebank.braid.core.synth.TypeNames
import io.swagger.v3.core.jackson.ModelResolver

class QualifiedTypeNameConverter(mapper: ObjectMapper) :
  ModelResolver(mapper, QualifiedTypeNameResolver()), TypeNames {

  // Expose a function which helps to implement SyntheticModelConverter which needs to
  // know the type name of a property.
  // This exposes (publishes) a method which would otherwise be protected in a base class.
  override fun getTypeName(type: JavaType?, beanDesc: BeanDescription?): String {
    return super._typeName(type, beanDesc)
  }
}
