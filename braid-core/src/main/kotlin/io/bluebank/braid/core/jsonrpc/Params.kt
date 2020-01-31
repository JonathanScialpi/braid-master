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

import io.bluebank.braid.core.logging.loggerFor
import java.lang.reflect.Constructor
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.valueParameters

private val log = loggerFor<Params>()

interface Params {
  companion object {
    fun build(params: Any?): Params {
      if (params == null) {
        return NullParams()
      }
      if (params is List<*>) {
        return ListParams(params as List<Any?>)
      }
      if (params is Map<*, *>) {
        @Suppress("UNCHECKED_CAST")
        return NamedParams(params as Map<String, Any?>)
      } else {
        return SingleValueParam(params)
      }
    }
  }

  val count: Int

  fun mapParams(method: KFunction<*>): List<Any?>
  fun mapParams(constructor: Constructor<*>): List<Any?>
  fun computeScore(fn: KFunction<*>): Int
}

abstract class AbstractParams : Params {
  companion object {
    @JvmStatic
    protected fun computeScore(parameters: List<KParameter>, values: List<Any?>): Int {
      return when {
        parameters.size != values.size -> 0
        else -> {
          if (parameters.isEmpty()) return 100
          parameters
            .zip(values)
            .map { computeScore(it.first, it.second) }
            .fold(1) { acc, i -> acc * i }
        }
      }
    }

    private fun computeScore(parameter: KParameter, value: Any?): Int {
      return when {
        value == null && parameter.type.isMarkedNullable -> 10
        value == null && !parameter.type.isMarkedNullable -> 0
        parameter.type.classifier != null -> {
          when (parameter.type.classifier) {
            is KClass<*> -> {
              val classifier = parameter.type.classifier!! as KClass<*>
              computeScore(classifier, value!!.javaClass)
            }
            else -> {
              log.warn("attempted to compute score from ${parameter.type.classifier}! This should never happen. Parameter was $parameter value was $value")
              0
            }
          }
        }
        else -> 1
      }
    }

    // we memoize the heavy reflection stuff that's at the heart of the loops
    // we could do better in theory, memoizing at the function signatures level as well ... perhaps consider this for a future optimisation
    private val computeScore = { targetType: KClass<*>, sourceType: Class<*> ->
      this.computeScoreInternal(
        targetType,
        sourceType
      )
    }.memoize()

    /**
     * This class computes the logical score between a value and a target type using pure type analysis
     * In other words, this is dynamic binding voodoo and part of the course of trying to bind a dynamic language
     * to something chewy-typed like the JVM (yeah, you heard me, chewy-typed - if you want a better typed VM look look at the CLR)
     * A different approach to all this dynamic typing is to introduce additional type information payloads in the request
     * to provide type hints. Until we decide to do that, we'll need some heuristics (i.e. hacky rules) to make overloads work
     */
    private fun computeScoreInternal(targetType: KClass<*>, sourceType: Class<*>): Int {
      return when {
        // we give preference for parsing of strings rather than direct string to string matching. this is to give a higher
        // weight to say "200.00" -> BigDecimal
        // we don't do the same for complex type e.g. Map<*,*> -> ComplexType, because the use-cases are very different
        // If someone declares a method with Map<*,*> it signals that they don't care about strong types anyhow
        String::class.assignableFrom(sourceType) && targetType.isSubclassOf(String::class) -> 8
        Map::class.assignableFrom(sourceType) && targetType.isSubclassOf(Map::class) -> 10
        // otherwise, when we have a perfect match we use it - this means
        targetType.javaObjectType == sourceType -> 10
        // if we are dealing with "list" types that are not a perfect match (e.g. Array <-> List)
        sourceType.isListType() && targetType.isListType() -> {
          when {
            // we give preference to List receivers
            targetType.isSubclassOf(List::class) -> 9
            else -> 8
          }
        }
        // this is a subordinate to a perfect match. e.g. a perfect match with Map<*, *> will always trump a complex type
        sourceType.isParsableType() -> 8
        // a subtype is always slightly better than a perfect match
        sourceType.kotlin.isSubclassOf(targetType) -> 9
        // a common super type (e.g. Numbers) will have lower precedence than a perfect match of a subclass
        targetType.haveCommonSuperClass(sourceType.kotlin) -> 8
        // if nothing else matches, we can always convert Any to a string
        targetType.isSubclassOf(String::class) -> 5
        // for everything else, we reject it
        else -> 0
      }
    }

    private fun KClass<*>.assignableFrom(clazz: Class<*>) =
      this.java.isAssignableFrom(clazz)

    private fun KClass<*>.realSuperClasses() =
      allSuperclasses.filter { !it.java.isInterface && it != Any::class }

    private fun KClass<*>.intersectRealSuperClasses(other: KClass<*>) =
      this.realSuperClasses().intersect(other.realSuperClasses())

    private fun KClass<*>.haveCommonSuperClass(other: KClass<*>) =
      this.intersectRealSuperClasses(other).isNotEmpty()

    private fun KClass<*>.isListType() = isSubclassOf(List::class) || java.isArray
    private fun Class<*>.isListType() = List::class.assignableFrom(this) || this.isArray
    private fun Class<*>.isParsableType() =
      Map::class.assignableFrom(this) || String::class.assignableFrom(this)
  }
}

class SingleValueParam(val param: Any) : AbstractParams() {
  override val count: Int = 1

  override fun mapParams(method: KFunction<*>): List<Any?> {
    return listOf(Converter.convert(param, method.valueParameters[0]))
  }

  override fun mapParams(constructor: Constructor<*>): List<Any?> {
    return listOf(Converter.convert(param, constructor.parameters[0]))
  }

  override fun toString(): String {
    return param.toString()
  }

  override fun computeScore(fn: KFunction<*>): Int {
    return computeScore(fn.parameters.drop(1), listOf(param))
  }
}

class NamedParams(val map: Map<String, Any?>) : AbstractParams() {
  override val count: Int = map.size
  override fun mapParams(method: KFunction<*>): List<Any?> {
    return method.valueParameters.map { parameter ->
      val value = map[parameter.name]
      Converter.convert(value, parameter)
    }
  }

  override fun mapParams(constructor: Constructor<*>): List<Any?> {
    return constructor.parameters.map { parameter ->
      val value = map[parameter.name]
      Converter.convert(value, parameter)
    }
  }

  override fun toString(): String {
    return map.map { "${it.key}: ${it.value}" }.joinToString(",")
  }

  override fun computeScore(fn: KFunction<*>): Int {
    if (fn.parameters.size - 1 != map.size) return Int.MAX_VALUE

    val (parameters, values) = fn.parameters.drop(1).map { it.name to it }.filter { (key, _) ->
      map.containsKey(
        key
      )
    }.map { it.second to map[it.first] }.unzip()
    return computeScore(parameters, values)
  }
}

class ListParams(val params: List<Any?>) : AbstractParams() {
  override val count: Int = params.size
  override fun mapParams(method: KFunction<*>): List<Any?> {
    return method.valueParameters.zip(params).map { (parameter, value) ->
      Converter.convert(value, parameter)
    }
  }

  override fun mapParams(constructor: Constructor<*>): List<Any?> {
    return constructor.parameters.zip(params).map { (parameter, value) ->
      Converter.convert(value, parameter)
    }
  }

  override fun toString(): String {
    return params.joinToString(",") { it.toString() }
  }

  override fun computeScore(fn: KFunction<*>): Int {
    return computeScore(fn.parameters.drop(1), params)
  }
}

class NullParams : Params {
  override val count: Int = 0

  override fun mapParams(method: KFunction<*>): List<Any?> {
    // assuming client has already checked the method parameters
    return emptyList()
  }

  override fun mapParams(constructor: Constructor<*>): List<Any?> {
    // assuming client has already checked the method parameters
    return emptyList()
  }

  override fun toString(): String {
    return ""
  }

  override fun computeScore(fn: KFunction<*>): Int {
    return when {
      fn.parameters.size == 1 -> 10
      else -> Int.MAX_VALUE
    }
  }
}
