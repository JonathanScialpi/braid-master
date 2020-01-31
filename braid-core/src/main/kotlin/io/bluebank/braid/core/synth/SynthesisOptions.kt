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

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import org.apache.commons.lang3.ClassUtils
import kotlin.reflect.KClass

/*
 * This supports two implementations of which one is enabled by default (hard-coded), see the ./README.md for details.
 */

object SynthesisOptions {

  /**
   * When filter not enabled
   */
  fun unfiltered(annotation: Annotation): Boolean {
    return true
  }

  /**
   * When any Swagger annotation is acceptable
   */
  fun anySwagger(annotation: Annotation): Boolean {
    return annotation.annotationClass.qualifiedName?.startsWith("io.swagger") ?: false
  }

  /**
   * When only a specific type of annotation is acceptable
   */
  fun specific(vararg annotationClasses: KClass<*>): (Annotation) -> Boolean = {
    annotationClasses.any { annotationClass -> annotationClass == it.annotationClass }
  }

  /**
   * Any annotation on the call method might be applicable to the callable function
   * (presumably the annotation's target type is compatible since they're both methods).
   * Let's try to allow any Swagger annotation.
   * Or we could e.g. restrict this in future to only the @Operation annotation.
   */
  private val defaultMethodFilter: (Annotation) -> Boolean = ::anySwagger

  /**
   * The only annotations we support on the ctor parameters are @Parameter and @Schema --
   * in theory we could try to allow any which support ElementType.FIELD as their @Target
   * -- maybe only these are required though,
   * and if we wanted to support others we should also write test cases for them.
   */
  private val defaultParameterFilter: (Annotation) -> Boolean =
    specific(Parameter::class, Schema::class)

  /**
   * Call this after changing the default filters in a unit test
   */
  fun resetToDefaults() {
    isMethodAnnotation = defaultMethodFilter
    isParameterAnnotation = defaultParameterFilter
    isParameterTypeFilterEnabled = true;
    currentStrategy = defaultStrategy
  }

  /*
    The following are var not val because I want to test with different kinds of filter
   */
  var isMethodAnnotation: (Annotation) -> Boolean = defaultMethodFilter
  var isParameterAnnotation: (Annotation) -> Boolean = defaultParameterFilter
  var isParameterTypeFilterEnabled = true;

  /**
   * Annotations on a complex type don't work
   * because complex types are defined using `$ref` in the model.
   */
  fun isParameterTypeAnnotatable(type: Class<*>): Boolean {
    // I don't know what types are or are not represented using `$ref` because I haven't
    // looked in the Swagger implementation -- my guess is that this should return true
    // for the types described in https://swagger.io/specification/#dataTypes

    // I hope this is the right way to handle dates and binary data too -- they both seem
    // to be types of string, according to https://swagger.io/specification/#dataTypes

    // I use isPrimitiveOrWrapper to support unboxed `int` but also boxed `Integer`
    return ClassUtils.isPrimitiveOrWrapper(type) || type == String::class.java ||
      !isParameterTypeFilterEnabled
  }

  /**
   * This is the strategy for annotating constructor parameters which are types of object
   * and so which are naturally  `$ref` elements which are difficult to annotate as stated
   * in comments at the top of this file.
   */
  enum class ObjectStrategy {

    /**
     * Annotate other fields but not these fields -- use isParameterTypeAnnotatable
     * to discard annotations associated with object types.
     */
    NONE,
    /**
     * Use SyntheticModelConverter to create a swagger model instead of using annotations
     * and replace the `$ref` element with an `allOf` element.
     */
    ALLOF,
    /**
     * Use SyntheticModelConverter to create a swagger model instead of using annotations
     * and replace the `$ref` element with an inline (nested) element definition.
     */
    INLINE
  }

  private val defaultStrategy = ObjectStrategy.ALLOF
  var currentStrategy = defaultStrategy

  /**
   * If true then add annotations inside AsmUtilities, else use SyntheticModelConverter
   */
  val strategyUsesAnnotations: Boolean
    get() = (currentStrategy == ObjectStrategy.NONE)

  /**
   * If true then use `allOf`, else use inline definitions -- don't call this unless
   * strategyUsesAnnotations returns true
   */
  val strategyUsesAllOf: Boolean
    get() {
      return when (currentStrategy) {
        ObjectStrategy.ALLOF -> true
        ObjectStrategy.INLINE -> false
        else -> throw IllegalStateException()
      }
    }
}
