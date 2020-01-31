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
package io.bluebank.braid.corda.integration

import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.meta.DEFAULT_API_MOUNT
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavascriptIntegrationTests {

  companion object {
    private val log = loggerFor<JavascriptIntegrationTests>()
    private val javascriptLogger = LoggerFactory.getLogger("javascript-logger")
  }

  private val cordaNet = CordaNet.createCordaNet()

  @Test
  fun runNPMTests() {
    cordaNet.withCluster {
      log.info("project directory is ${getProjectDirectory()}")
      val testDir = getProjectDirectory().resolve("../braid-client-js")
      assertTrue { testDir.exists() }
      val npmScriptName = if (isWindows()) "npm.cmd" else "npm"
      log.info("npm script name is $npmScriptName")
      val pb = ProcessBuilder(npmScriptName, "run", "test:integration")
      pb.environment()["braidService"] =
        "https://localhost:${cordaNet.braidPortForParty("PartyA")}$DEFAULT_API_MOUNT"
      pb.directory(testDir)
      val process = pb.start()
      Thread {
        try {
          BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
            reader.lines().forEach {
              javascriptLogger.error(it)
            }
          }
        } catch (e: Exception) {
        }
      }.start()
      Thread {
        try {
          BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lines().forEach {
              javascriptLogger.info(it)
            }
          }
        } catch (e: Exception) {
        }
      }.start()
      process.waitFor(5, TimeUnit.MINUTES)
      assertEquals(0, process.exitValue(), "tests should succeed")
    }
  }

  private fun getProjectDirectory() = File(System.getProperty("user.dir"))

  private fun isWindows(): Boolean {
    // https://stackoverflow.com/questions/228477/how-do-i-programmatically-determine-operating-system-in-java
    val osName = System.getProperty("os.name")
    log.info("System.getProperty(\"os.name\") is $osName")
    return osName.startsWith("Windows")
  }
}