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
package io.bluebank.braid.core.async

import io.vertx.core.Future
import io.vertx.core.Future.failedFuture
import io.vertx.core.Future.succeededFuture
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FuturesKtTests {
  @Test
  fun `getOrThrow on Future works`() {
    assertEquals("hello", succeededFuture("hello").getOrThrow())
    assertFailsWith<IllegalStateException> {
      Future.failedFuture<String>(
        IllegalStateException("fail")
      ).getOrThrow()
    }
  }

  @Test
  fun `that we can chain successful and failed futures`() {
    var counter = 0
    withFuture<String> { it.complete("hello") }
      .onSuccess {
        ++counter
      }
      .catch {
        error("should never hit this")
      }
      .finally {
        ++counter
      }

    withFuture<String> { it.fail("failed future") }
      .onSuccess {
        error("should never hit this")
      }
      .catch {
        ++counter
      }
      .finally {
        ++counter
      }

    kotlin.test.assertEquals(4, counter, "all appropriate handlers should be hit")
  }

  @Test
  fun `that we fail if onSuccess fails`() {
    var counter = 0
    succeededFuture("success")
      .onSuccess {
        error("failure") // that
      }
      .catch {
        ++counter
      }
      .finally {
        ++counter
      }
    assertEquals(2, counter, "that we handle exception raised from onSuccess")
  }

  @Test
  fun `that we fail if catch fails`() {
    var counter = 0
    failedFuture<String>("failure 1")
      .catch {
        ++counter
        error("failure 2")
      }
      .catch {
        ++counter
        assertEquals(
          "failure 2",
          it.message,
          "that we receive the error raised from catch"
        )
      }
      .finally {
        ++counter
      }
    assertEquals(3, counter, "that all handlers were hit")
  }

  @Test
  fun `that we receive an error raised in finally`() {
    var counter = 0
    succeededFuture("hello")
      .finally {
        ++counter
        error("failure 1")
      }
      .catch {
        ++counter
        assertEquals(
          "failure 1",
          it.message,
          "that we received the error raised in finally"
        )
      }
    assertEquals(2, counter, "that we hit all handlers")
  }

  @Test
  fun `that we can reduce multiple succeeded futures in to one`() {
    var counter = 0
    val results = (1..10).toList()
    results.map { succeededFuture(it) }.all()
      .onSuccess {
        ++counter
        assertEquals(results, it, "that all the numbers match in the right order")
      }
      .catch {
        error("should never hit this")
      }
    assertEquals(1, counter, "that we hit all handlers")
  }

  @Test
  fun `that we can reduce multiple failed futures in to one`() {
    var counter = 0
    val errors = (1..10).map { it.toString() }
    errors.map { failedFuture<String>(it) }.all()
      .onSuccess {
        error("should never hit this")
      }
      .catch {
        ++counter
        assertEquals("1", it.message, "that we get the first error")
      }
    assertEquals(1, counter, "that we hit all handlers")
  }
}