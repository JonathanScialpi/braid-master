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

import com.fasterxml.jackson.databind.JavaType
import io.swagger.v3.core.jackson.TypeNameResolver
import io.swagger.v3.core.util.AnnotationsUtils
import io.swagger.v3.core.util.PrimitiveType

/**
 * A specialised type resolver that preserves package names
 */
class QualifiedTypeNameResolver : TypeNameResolver() {

  override fun nameForClass(cls: Class<*>, options: Set<Options>): String {
    return cls.swaggerTypeName(options)
  }

  override fun nameForGenericType(type: JavaType, options: MutableSet<Options>?): String {
    val generic = StringBuilder(nameForClass(type, options))
    val count = type.containedTypeCount()
    for (i in 0 until count) {
      val arg = type.containedType(i)
      val argName = if (PrimitiveType.fromType(arg) != null)
        nameForClass(arg, options)
      else
        nameForType(arg, options)
      generic.append("_${argName.replace('.', '_')}")
    }
    return generic.toString()
  }
}

fun Class<*>.swaggerTypeName(options: Set<TypeNameResolver.Options> = emptySet()): String {
  val className = when {
    this.name.startsWith("java.") -> this.simpleName
    else -> this.name
  }

  return when {
    options.contains(TypeNameResolver.Options.SKIP_API_MODEL) -> className
    else -> {
      val annotation = AnnotationsUtils.getSchemaDeclaredAnnotation(this)
      val preprocessed = annotation?.name?.trimToNull() ?: className
      preprocessed.replace("$", "_")
    }
  }
}

private fun String.trimToNull(): String? {
  val trimmed = this.trim()
  return when {
    trimmed.isEmpty() -> null
    else -> trimmed
  }
}
