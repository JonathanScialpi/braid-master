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

import io.swagger.v3.core.util.AnnotationsUtils
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.extensions.Extension
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import kotlin.reflect.full.createInstance

val schemaAnnotationDefaults: Map<String, Any> = mapOf(
  "implementation" to Void::class.java,
  "not" to Void::class.java,
  "oneOf" to emptyArray<Class<*>>(),
  "anyOf" to emptyArray<Class<*>>(),
  "allOf" to emptyArray<Class<*>>(),
  "name" to "",
  "title" to "",
  "multipleOf" to 0.0,
  "maximum" to "",
  "exclusiveMaximum" to false,
  "minimum" to "",
  "exclusiveMinimum" to false,
  "maxLength" to 2147483647,
  "minLength" to 0,
  "pattern" to "",
  "maxProperties" to 0,
  "minProperties" to 0,
  "requiredProperties" to emptyArray<String>(),
  "required" to false,
  "description" to "",
  "format" to "",
  "ref" to "",
  "nullable" to false,
  "readOnly" to false,
  "writeOnly" to false,
  "accessMode" to Schema.AccessMode.AUTO,
  "example" to "",
  // https://stackoverflow.com/questions/33690283/create-an-annotation-instance-in-kotlin
  "externalDocs" to ExternalDocumentation::class.createInstance(), // ExternalDocumentation(),
  "deprecated" to false,
  "type" to "",
  "allowableValues" to emptyArray<String>(),
  "defaultValue" to "",
  "discriminatorProperty" to "",
  "discriminatorMapping" to emptyArray<DiscriminatorMapping>(),
  "hidden" to false,
  "subTypes" to emptyArray<Class<*>>(),
  "extensions" to emptyArray<Extension>()
)

fun schemaFromParameter(parameter: Parameter): Annotation {
  // get property values from the Parameter instance
  // properties which exist in a Schema instance will be ignored
  val parameterProperties: Map<String, Any> = mapOf(
    "name" to parameter.name,
    "in" to parameter.`in`,
    "description" to parameter.description,
    "required" to parameter.required,
    "deprecated" to parameter.deprecated,
    "allowEmptyValue" to parameter.allowEmptyValue,
    "style" to parameter.style,
    "explode" to parameter.explode,
    "allowReserved" to parameter.allowReserved,
    "schema" to parameter.schema,
    "array" to parameter.array,
    "content" to parameter.content,
    "hidden" to parameter.hidden,
    "examples" to parameter.examples,
    "example" to parameter.example,
    "extensions" to parameter.extensions,
    "ref" to parameter.ref
  )
  // combine specified parameter properties with default schema properties
  val schemaProperties: Map<String, Any> = schemaAnnotationDefaults.keys.associate {
    val value: Any? = if (parameterProperties.containsKey(it)) parameterProperties[it]
    else schemaAnnotationDefaults[it]
    Pair<String, Any>(it, value!!)
  }
  // initialize properties from the Parameters instance
  var schema: Schema = Schema::class.createAnnotationProxy(schemaProperties)
  if (AnnotationsUtils.hasSchemaAnnotation(parameter.schema))
    schema = AnnotationsUtils.mergeSchemaAnnotations(schema, parameter.schema)
  if (AnnotationsUtils.hasArrayAnnotation(parameter.array))
    return AnnotationsUtils.mergeArrayWithSchemaAnnotation(parameter.array, schema)
  return schema
}

fun convertParameters(annotations: Array<Annotation>): Array<Annotation> =
  annotations.map { if (it is Parameter) schemaFromParameter(it) else it }.toTypedArray()
