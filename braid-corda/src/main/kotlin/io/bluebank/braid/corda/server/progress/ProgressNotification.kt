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
package io.bluebank.braid.corda.server.progress

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "pet status in the store")
data class ProgressNotification (
  @Schema(description = "The flow class name", example = "net.corda.finance.flows.CashExitFlow")
  val flowClass: Class<*>? = null,

  @Schema(description = "The invocation-id header as supplied in the flow initiation post request", example = "1234")
  val invocationId: String? = null,

  @Schema(description = "The step name as defined by the flow", example = "Starting,Done,Signing")
  val step: String? = null,

  @Schema(description = "Any errors returned by the Progress tracker observable")
  val error: Throwable? = null,

  @Schema(description = "Indicates if the progress tracker for this flow invocation-id is completed", example = "false")
  val complete: Boolean = false
) {
  fun withFlowClass(flowClass: Class<out Any>):ProgressNotification{
    return copy(flowClass = flowClass)
  }

  fun withInvocationId(invocationId:String?):ProgressNotification{
    return copy(invocationId = invocationId)
  }

  fun withStep(step:String?):ProgressNotification{
    return copy(step = step)
  }

  fun withError(error:Throwable?):ProgressNotification{
    return copy(error = error)
  }

  fun withComplete(complete:Boolean):ProgressNotification{
    return copy(complete = complete)
  }
}
