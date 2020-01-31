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
package io.bluebank.braid.corda.server.experiment

import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import net.corda.core.utilities.loggerFor
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

private val log = loggerFor<SuiteTest>()

/**
 * This is a prototype for the structure of the code in BraidCordaStandaloneServerTest
 *
 * The basic idea is described in
 * https://stackoverflow.com/questions/1921515/junit-4-beforeclass-afterclass-when-using-suites
 *
 * The fact that @BeforeClass must be static makes that a bit more complicated --
 * e.g. https://github.com/junit-team/junit4/issues/122 complains about that --
 * to resolve that, I define the @BeforeClass in each subclass.
 *
 * The names of the Suite classes all begin with `SuiteClass`.
 * This matches an exclusion rule for the surefire plugin in pom.xml, so that the suite
 * classes will not be tested twice (i.e. once inside and again outside the suite),
 * however see also https://gitlab.com/bluebank/braid/issues/208
 *
 * This file could be deleted but I'm keeping it in case we want to modify the
 * structure of BraidCordaStandaloneServerTest again in future,
 * and use this to further experiment (so instead I've simply added @Ignore to this test).
 */
@Ignore
@RunWith(Suite::class)
@Suite.SuiteClasses(SuiteClassFirst::class, SuiteClassSecond::class)
class SuiteTest {

  companion object {
    init {
      log.info("suite init")
    }

    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      log.info("suite beforeClass")
    }
  }
}

data class SuiteClassSetup(
  val loginToken: String?,
  val port: Int
)

class SuiteClassFirst : SuiteClassEither(suiteClassSetup) {

  companion object {
    private lateinit var suiteClassSetup: SuiteClassSetup
    @BeforeClass
    @JvmStatic
    fun beforeClass(testContext: TestContext) {
      log.info("first beforeClass")
      val finished = testContext.async()
      suiteClassSetup = startBraidAndLogin(true)
      finished.complete()
    }
  }
}

class SuiteClassSecond : SuiteClassEither(suiteClassSetup) {

  companion object {
    private lateinit var suiteClassSetup: SuiteClassSetup
    @BeforeClass
    @JvmStatic
    fun beforeClass(testContext: TestContext) {
      log.info("second beforeClass")
      val finished = testContext.async()
      suiteClassSetup = startBraidAndLogin(true)
      finished.complete()
    }
  }
}

@RunWith(VertxUnitRunner::class)
open class SuiteClassEither(private val suiteClassSetup: SuiteClassSetup) {

  companion object {
    fun startBraidAndLogin(
      userLogin: Boolean
    ): SuiteClassSetup {
      log.info("startBraidAndLogin $userLogin")
      return SuiteClassSetup(loginToken = if (userLogin) "foo" else null, port = 42)
    }
  }

  @Test
  fun firstTest(testContext: TestContext) {
    testContext.assertEquals(42, suiteClassSetup.port)
    log.info("firstTest ${suiteClassSetup.loginToken != null}")
  }

  @Test
  fun secondTest() {
    log.info("secondTest ${suiteClassSetup.loginToken != null}")
  }
}