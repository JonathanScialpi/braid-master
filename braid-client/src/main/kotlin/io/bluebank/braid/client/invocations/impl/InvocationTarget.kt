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
package io.bluebank.braid.client.invocations.impl

import rx.Observable
import java.lang.reflect.Type

/**
 * This type represents the invocation point used by [InvocationsInternalImpl].
 * It acts as a factory method to create the appropriate strategy and to request its
 * result, potentially causing the execution of the invocation (note: not in the case
 * of methods returning [Observable]
 */
internal typealias InvocationTarget = (
  parent: InvocationsInternal,
  method: String,
  returnType: Type,
  params: Array<out Any?>
) -> Any?
