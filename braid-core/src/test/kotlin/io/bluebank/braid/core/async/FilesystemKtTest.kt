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

import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(VertxUnitRunner::class)
class FilesystemKtTest {

  private val vertx = Vertx.vertx()

  @After
  fun after(context: TestContext) {
    val async = context.async()
    vertx.close { async.complete() }
  }

  @Test
  fun `that we can write read and copy a file`(context: TestContext) {
    val fs = vertx.fileSystem()
    val async = context.async()
    val file = File.createTempFile("braid-test", ".txt")
    file.deleteOnExit()

    val text = "hello"
    fs.writeFile(file.absolutePath, text.toByteArray())
      .compose { fs.readFile(file.absolutePath) }
      .onSuccess {
        context.assertEquals(
          text,
          it.toString(),
          "that file contents matches"
        )
      }
      .compose {
        val file2 = File.createTempFile("braid-test", ".txt")
        fs.delete(file2.absolutePath)
          .compose { fs.copy(file.absolutePath, file2.absolutePath) }
          .compose { fs.readFile(file2.absolutePath) }
          .onSuccess {
            context.assertEquals(
              text,
              it.toString(),
              "that file contents matches"
            )
          }
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }
}