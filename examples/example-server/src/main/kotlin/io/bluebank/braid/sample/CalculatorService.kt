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
package io.bluebank.braid.sample

import io.bluebank.braid.core.annotation.MethodDescription
import io.bluebank.braid.core.annotation.ServiceDescription
import io.vertx.core.Future
import io.vertx.core.Future.succeededFuture

@ServiceDescription("calculator", "a simple calculator")
class CalculatorService {

  @MethodDescription(description = "add two ints")
  fun add(lhs: Int, rhs: Int): Int {
    return lhs + rhs
  }

  // N.B. how to document the return type on an async function
  @MethodDescription(
    description = "subtract the second int from the first",
    returnType = Int::class
  )
  fun subtract(lhs: Int, rhs: Int): Future<Int> {
    return succeededFuture(lhs - rhs)
  }
}
