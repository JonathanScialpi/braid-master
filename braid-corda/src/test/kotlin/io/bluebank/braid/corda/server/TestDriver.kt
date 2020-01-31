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
package io.bluebank.braid.corda.server

import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.uncheckedCast
import net.corda.nodeapi.internal.ShutdownHook
import net.corda.nodeapi.internal.addShutdownHook
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.VerifierType
import net.corda.testing.node.User
import net.corda.testing.node.internal.DriverDSLImpl
import net.corda.testing.node.internal.setDriverSerialization

data class TestDriver(
  val dsl: DriverDSLImpl,
  val serializationEnv: AutoCloseable?,
  val shutdownHook: ShutdownHook
): AutoCloseable {
  companion object {
    fun driver(defaultParameters: DriverParameters = DriverParameters()): TestDriver {
      val serializationEnv = setDriverSerialization()
      @Suppress("DEPRECATION") val driverDSL = DriverDSLImpl(
        portAllocation = defaultParameters.portAllocation,
        debugPortAllocation = defaultParameters.debugPortAllocation,
        systemProperties = defaultParameters.systemProperties,
        driverDirectory = defaultParameters.driverDirectory.toAbsolutePath(),
        useTestClock = defaultParameters.useTestClock,
        isDebug = defaultParameters.isDebug,
        startNodesInProcess = defaultParameters.startNodesInProcess,
        waitForAllNodesToFinish = defaultParameters.waitForAllNodesToFinish,
        extraCordappPackagesToScan = defaultParameters.extraCordappPackagesToScan,
        notarySpecs = defaultParameters.notarySpecs,
        jmxPolicy = defaultParameters.jmxPolicy,
        compatibilityZone = null,
        networkParameters = defaultParameters.networkParameters,
        notaryCustomOverrides = defaultParameters.notaryCustomOverrides,
        inMemoryDB = defaultParameters.inMemoryDB,
        cordappsForAllNodes = uncheckedCast(defaultParameters.cordappsForAllNodes)
      )
      val shutdownHook = addShutdownHook(driverDSL::shutdown)
      driverDSL.start()
      return TestDriver(driverDSL, serializationEnv, shutdownHook)
    }
  }

  fun startNode(
    defaultParameters: NodeParameters = NodeParameters(),
    providedName: CordaX500Name? = defaultParameters.providedName,
    rpcUsers: List<User> = defaultParameters.rpcUsers,
    verifierType: VerifierType = defaultParameters.verifierType,
    customOverrides: Map<String, Any?> = defaultParameters.customOverrides,
    startInSameProcess: Boolean? = defaultParameters.startInSameProcess,
    maximumHeapSize: String = defaultParameters.maximumHeapSize
  ): CordaFuture<NodeHandle> {
    return dsl.startNode(defaultParameters, providedName, rpcUsers, verifierType, customOverrides, startInSameProcess, maximumHeapSize)
  }

  override fun close() {
    dsl.shutdown()
    dsl.shutdown()
    serializationEnv?.close()
  }
}
