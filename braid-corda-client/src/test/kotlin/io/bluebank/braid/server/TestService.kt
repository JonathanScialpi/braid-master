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
package io.bluebank.braid.server

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.bluebank.braid.core.annotation.ServiceDescription
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Vertx
import rx.Observable
import java.math.BigDecimal

interface MyService {
  fun add(lhs: Double, rhs: Double): Double
  fun noArgs(): Int
  fun noResult()
  fun longRunning(): Future<Int>
  fun stream(): Observable<Int>
  fun largelyNotStream(): Observable<Int>
  fun echoComplexObject(inComplexObject: ComplexObject): ComplexObject
  fun stuffedJsonObject(): JsonStuffedObject
  fun blowUp()
  fun exposeParameterListTypeIssue(str: String, md: ModelData): ModelData
  fun functionWithTheSameNameAndNumberOfParameters(
    amount: String,
    accountId: String
  ): Int = 1

  fun functionWithTheSameNameAndNumberOfParameters(
    amount: BigDecimal,
    accountId: String
  ): Int = 2

  fun functionWithTheSameNameAndNumberOfParameters(
    amount: BigDecimal,
    accountId: BigDecimal
  ): Int = 3

  fun functionWithTheSameNameAndNumberOfParameters(amount: Long, accountId: String): Int =
    4

  fun functionWithTheSameNameAndNumberOfParameters(amount: Int, accountId: String): Int =
    5

  fun functionWithTheSameNameAndNumberOfParameters(
    amount: Float,
    accountId: String
  ): Int = 6

  fun functionWithTheSameNameAndNumberOfParameters(
    amount: Double,
    accountId: String
  ): Int = 7

  fun functionWithTheSameNameAndNumberOfParameters(
    amount: ComplexObject?,
    accountId: String
  ): Int = 8

  fun functionWithTheSameNameAndNumberOfParameters(
    amount: List<String>,
    accountId: String
  ): Int = 9

  //  fun functionWithTheSameNameAndNumberOfParameters(amount: Map<String, String>, accountId: String): Int = 10
  fun functionWithTheSameNameAndNumberOfParameters(
    amount: Array<String>,
    accountId: String
  ): Int = 11

  fun functionWithTheSameNameAndASingleParameter(amount: String?): Int = 12
  fun functionWithTheSameNameAndASingleParameter(amount: Long): Int = 13
  fun functionWithTheSameNameAndASingleParameter(amount: Int): Int = 14
  fun functionWithTheSameNameAndASingleParameter(amount: Double): Int = 15
  fun functionWithTheSameNameAndASingleParameter(amount: Float): Int = 16
  fun functionWithTheSameNameAndASingleParameter(amount: ComplexObject): Int = 17
  fun functionWithTheSameNameAndASingleParameter(amount: List<String>): Int = 18
  //  fun functionWithTheSameNameAndASingleParameter(amount: Map<String, String>): Int = 19
  fun functionWithTheSameNameAndASingleParameter(amount: Array<String>): Int = 20

  fun functionWithBigDecimalParameters(
    amount: BigDecimal,
    anotherAmount: BigDecimal
  ): Int = 21

  fun functionWithComplexOrDynamicType(value: ComplexObject) = 22
  fun functionWithComplexOrDynamicType(value: Map<String, Any>) = 23
}

data class ComplexObject(val a: String, val b: Int, val c: Double)

data class JsonStuffedObject(val a: String) {
  val b: String
    get() = a
}

@ServiceDescription("my-service", "a simple service")
class MyServiceImpl(private val vertx: Vertx) : MyService {

  override fun add(lhs: Double, rhs: Double): Double {
    return lhs + rhs
  }

  override fun noArgs(): Int {
    return 5
  }

  override fun noResult() {
  }

  override fun longRunning(): Future<Int> {
    val result = future<Int>()
    vertx.setTimer(100) {
      result.complete(5)
    }
    return result
  }

  override fun stream(): Observable<Int> {
    return Observable.from(0..10)
  }

  override fun largelyNotStream(): Observable<Int> {
    return Observable.error(RuntimeException("stream error"))
  }

  override fun echoComplexObject(inComplexObject: ComplexObject): ComplexObject {
    return inComplexObject
  }

  override fun blowUp() {
    throw RuntimeException("expected exception")
  }

  override fun stuffedJsonObject(): JsonStuffedObject {
    return JsonStuffedObject("this is hosed")
  }

  override fun exposeParameterListTypeIssue(str: String, md: ModelData): ModelData {
    return md
  }
}

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type"
)
@JsonSubTypes(
  JsonSubTypes.Type(value = MeteringModelData::class, name = "MeteringModelData")
)
interface ModelData

data class MeteringModelData(val someString: String) : ModelData
