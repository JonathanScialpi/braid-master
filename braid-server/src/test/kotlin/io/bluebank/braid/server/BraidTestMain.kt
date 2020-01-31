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

import io.bluebank.braid.corda.server.BraidCordaStandaloneServer
import io.bluebank.braid.corda.server.BraidMain
import io.bluebank.braid.corda.server.BraidServerConfig
import io.bluebank.braid.core.logging.loggerFor

private val log = loggerFor<BraidCordaStandaloneServer>()

/**
 * BraidTestMainKt class
 */
fun main(args: Array<String>) {
  val config = BraidServerConfig.config(args)
  BraidMain().start(config)
    .map { openBrowser(config.port, it) }
}

private fun openBrowser(port: Int?, it: String?) {
  log.info("Started Vertical:$it on port:$port")
  ProcessBuilder().command("open", "https://localhost:$port/swagger.json").start()
  ProcessBuilder().command("open", "https://localhost:$port/api/rest/cordapps")
    .start()
}
