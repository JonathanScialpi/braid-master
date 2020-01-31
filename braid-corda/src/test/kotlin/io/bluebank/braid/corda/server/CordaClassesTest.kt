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

import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.utils.toJarsClassLoader
import io.bluebank.braid.core.utils.tryWithClassLoader
import io.github.classgraph.ClassGraph
import net.corda.finance.contracts.asset.Cash
import net.corda.node.internal.AbstractNode
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.assertThat
import org.junit.Test

private val log = loggerFor<CordaClassesTest>()

// @Ignore
class CordaClassesTest {

  companion object {
    val jars = listOf(
      "https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda-finance-workflows/4.1/corda-finance-workflows-4.1.jar",
      "https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda-finance-contracts/4.1/corda-finance-contracts-4.1.jar"
    )

    val classes = loadClasses()

    fun loadClasses(): List<Class<out Any>> {
      try {

        return tryWithClassLoader(jars.toJarsClassLoader()) {
          CordaClasses().cordaSerializableClasses.map { it.java }
        }
      } catch (err: Throwable) {
        log.info("CordaClassesTest init error", err)
        throw err;
      }

    }
  }

  @Test
  fun `should have cash state`() {
    assertThat(classes, hasItem(Cash.State::class.java))
  }

  @Test
  fun `should not have ServiceHubInternalImpl`() {
    assertThat(classes, not(hasItem(AbstractNode.ServiceHubInternalImpl::class.java)))
  }

  @Test
  fun `should match cash state`() {
    val classInfo = ClassGraph()
      .whitelistClasses(Cash.State::class.java.name)
      .enableAnnotationInfo()
      .scan()
      .allClasses[0]
    assertThat(classInfo.loadClass().name, `is`(Cash.State::class.java.name))
    assertThat(CordaClasses.isAppropriateForSerialization(classInfo), equalTo(true))
  }
}