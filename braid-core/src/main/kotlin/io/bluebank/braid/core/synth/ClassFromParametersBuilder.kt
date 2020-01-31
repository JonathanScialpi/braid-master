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

import io.bluebank.braid.core.logging.loggerFor
import org.objectweb.asm.ClassWriter
import java.lang.reflect.Method
import java.lang.reflect.Parameter

/**
 * Builder for synthetic POJO with just a single constructor and fields
 * from parameters of an existing constructor or method
 */
data class ClassFromParametersBuilder(
  val parameters: List<Parameter>,
  val className: String
) {

  companion object {
    private val logger = loggerFor<ClassFromParametersBuilder>()

    @JvmStatic
    fun acquireClass(
      parameters: Array<Parameter>,
      classLoader: ClassLoader,
      className: String
    ): Class<*> {
      return classLoader.lazyAcquire(className) {
        ClassFromParametersBuilder(parameters.toList(), className)
          .buildAndInject(classLoader)
      }
    }

    /**
     * attempts to load the class - if it fails builds the type, injects it
     * @return the class matching [className]
     */
    private fun ClassLoader.lazyAcquire(className: String, fn: () -> Class<*>): Class<*> {
      return try {
        loadClass(className)
      } catch (err: ClassNotFoundException) {
        return fn()
      }
    }

    /**
     * access to the [ClassLoader.defineClass] method - used to deploy the class bytecode
     */
    private val defineClassMethod: Method by lazy {
      val cls = Class.forName("java.lang.ClassLoader")
      cls.getDeclaredMethod(
        "defineClass",
        *arrayOf<Class<*>>(
          String::class.java,
          ByteArray::class.java,
          Int::class.java,
          Int::class.java
        )
      ).apply { isAccessible = true }
    }
  }

  /**
   * builds the bytecode of the class and injects it into [classLoader]
   */
  fun buildAndInject(classLoader: ClassLoader) = classLoader.inject(build())

  /**
   * builds the bytecode of the class
   * @return the bytecode array
   */
  fun build(): ByteArray {
    assert(className.isNotBlank()) { "class name was not set" }
    if (!SynthesisOptions.strategyUsesAnnotations) {
      // use SyntheticModelConverter instead of annotations
      SyntheticModelConverter.registerClass(className, parameters)
    }
    return ClassWriter(0).apply {
      declareSimplePublicClass(className)
      addFields(parameters.toTypedArray())
      writeDefaultConstructor()
      visitEnd()
    }.toByteArray()
  }

  private fun ClassLoader.inject(bytes: ByteArray): Class<*> {
    assert(className.isNotBlank()) { "class name not set" }
    val result = ClassLogger.readBytes(bytes)
    logger.info("$className\r\n$result")

    return try {
      loadClass(className).also {
        logger.warn("Payload type $className already declared")
      }
    } catch (error: ClassNotFoundException) {
      defineClassMethod.invoke(this, className, bytes, 0, bytes.size)
      return loadClass(className)
    }
  }
}

const val PAYLOAD_CLASS_SUFFIX = "Payload"
const val PAYLOAD_CLASS_PREFIX = "generated."
fun Class<*>.payloadClassName() =
  PAYLOAD_CLASS_PREFIX + this.name.replace("$", "_") + PAYLOAD_CLASS_SUFFIX