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

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import net.corda.core.utilities.loggerFor
import java.lang.reflect.Type

/**
 * Workaround for https://github.com/swagger-api/swagger-core/issues/3310
 * This make sure super classes are also processed with Mixin Annotations
 */
class ComposedSchemaFixV3() : ModelConverter {

  companion object {
    private val log = loggerFor<ComposedSchemaFixV3>()
  }

  override fun resolve(
      type: AnnotatedType,
      context: ModelConverterContext,
      chain: MutableIterator<ModelConverter>?
  ): Schema<*>? {
    return chain?.next()?.resolve(type, FixingUpContext(context), chain)
  }

   class FixingUpContext(val context: ModelConverterContext) : ModelConverterContext {
     override fun resolve(p0: AnnotatedType?): Schema<*>? { return context.resolve(p0) }

     override fun getConverters(): MutableIterator<ModelConverter> {return context.converters}

     override fun defineModel(p0: String?, schema: Schema<*>?) {
       return context.defineModel(p0,fixUp(schema))
     }

     override fun defineModel(p0: String?, schema: Schema<*>?, p2: AnnotatedType?, p3: String?) {
       return context.defineModel(p0,fixUp(schema),p2, p3)
     }

     override fun defineModel(p0: String?, schema: Schema<*>?, p2: Type?, p3: String?) {
       return context.defineModel(p0,fixUp(schema),p2,p3)
     }

     override fun getDefinedModels(): MutableMap<String, Schema<Any>>? {
        return context.definedModels
     }

     private fun fixUp(schema: Schema<*>?): Schema<*>? {
       if(schema is ComposedSchema){
         schema.allOf.forEach {
           prepentComponents(it)
         }
       }
       return schema
     }

     private fun prepentComponents(it: Schema<Any>) {
       if (it.`$ref` != null && !it.`$ref`.startsWith("#/components/schemas/"))
         it.`$ref` = "#/components/schemas/" + it.`$ref`
     }

   }

}