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

import io.swagger.v3.oas.annotations.media.Schema

const val MESSAGE_DESC = "the error message"
const val TYPE_DESC = "the type of error"

/**
 * A proxy type for Throwables - to work around the insanity of swaggers resolve() algorithm
 */
@Schema(name = "InvocationError")
data class BraidSwaggerError(
  @Schema(description = MESSAGE_DESC)
  val message: String,

  @Schema(description = TYPE_DESC)
  val type: String
)