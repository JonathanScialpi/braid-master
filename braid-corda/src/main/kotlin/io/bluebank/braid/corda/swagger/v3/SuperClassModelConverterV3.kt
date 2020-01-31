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
package io.bluebank.braid.corda.swagger.v3

import com.fasterxml.jackson.databind.JavaType
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import net.corda.core.utilities.loggerFor

/**
 * Workaround for https://github.com/swagger-api/swagger-core/issues/3310
 * This make sure super classes are also processed with Mixin Annotations
 */
class SuperClassModelConverterV3() : ModelConverter {

  companion object {
    private val log = loggerFor<SuperClassModelConverterV3>()
  }

  override fun resolve(
      type: AnnotatedType,
      context: ModelConverterContext,
      chain: MutableIterator<ModelConverter>?
  ): Schema<*>? {

    val subClass = chain?.next()?.resolve(type, context, chain)

    // have to resolve this afterwards as ModelResolver tries to find subtypes
    val underlyingType = type.underlyingType()
    underlyingType?.superclass?.apply{
      context.resolve(AnnotatedType(underlyingType.superclass))
    }

    return subClass
  }

  private fun AnnotatedType.underlyingType(): Class<*>?{
    if(this.type is Class<*>)
      return this.type as Class<*>
    if(this.type is JavaType)
      return (this.type as JavaType).rawClass
    return null;
  }
}