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
package io.bluebank.braid.corda.services

import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle

/**
 * interface for starting a flow either within the node or via Corda RPC
 */
interface FlowStarterAdapter {
  fun <T> startFlowDynamic(
    logicType: Class<out FlowLogic<T>>,
    vararg args: Any?
  ): FlowHandle<T>

  fun <T> startTrackedFlowDynamic(
    logicType: Class<out FlowLogic<T>>,
    vararg args: Any?
  ): FlowProgressHandle<T>
}
