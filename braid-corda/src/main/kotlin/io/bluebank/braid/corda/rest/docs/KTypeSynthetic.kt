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
package io.bluebank.braid.corda.rest.docs

import java.lang.reflect.Type
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

fun KType.javaTypeIncludingSynthetics(): Type {
  return try {
    this.javaType
  } catch (e: Throwable) {
    this.classifier
    val toString = this.classifier.toString()
    //   return (this.classifier as KClassImpl).jClass
    Thread.currentThread().contextClassLoader.loadClass(toString.replace("class ", ""))
  }
}
