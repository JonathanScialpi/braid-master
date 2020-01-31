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
import com.fasterxml.jackson.databind.ObjectMapper
import io.bluebank.braid.corda.rest.docs.v3.QualifiedTypeNameResolver
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.converter.ModelConverterContextImpl
import io.swagger.v3.oas.models.media.Schema
import net.corda.core.utilities.loggerFor

/**
 * Workaround for https://github.com/swagger-api/swagger-core/issues/3310
 * This adds Annotations applied on mixins to allow us to describe inheritance heirarchies
 *
 */
class MixinModelConverterV3(val mapper: ObjectMapper) : ModelConverter {

  val namer = QualifiedTypeNameResolver()

  companion object {
    private val log = loggerFor<MixinModelConverterV3>()
  }

  override fun resolve(
      type: AnnotatedType,
      context: ModelConverterContext,
      chain: MutableIterator<ModelConverter>?
  ): Schema<*>? {

    val schema = chain?.next()?.resolve(type, context, chain)

    if(schema == null|| type.underlyingType() ==null)
      return schema

    val mixinClass = mapper.findMixInClassFor(type.underlyingType())
    if (mixinClass == null)
      return schema

    return addMixinSchema(mixinClass,type, context,schema)
  }

  private fun AnnotatedType.underlyingType(): Class<*>?{
    if(this.type is Class<*>)
      return this.type as Class<*>
    if(this.type is JavaType)
      return (this.type as JavaType).rawClass

    return null;
  }

  private fun addMixinSchema(mixin: Class<*>,type:AnnotatedType, context: ModelConverterContext, schema: Schema<Any>): Schema<*> {
    val mixinSchema = mixinModelConverter(context).resolve(AnnotatedType(mixin))
    schema.discriminator(mixinSchema.discriminator)   // to do add others?
    if (schema.name != null)
      context.defineModel(schema.name, schema, type, null)

    return schema

  }

  var mixinConverter: ModelConverterContextImpl? = null;
  private fun mixinModelConverter(context: ModelConverterContext): ModelConverterContextImpl {
    if (mixinConverter == null)
      mixinConverter = ModelConverterContextImpl(context.converters.asSequence().toList())
    return mixinConverter!!;
  }
}