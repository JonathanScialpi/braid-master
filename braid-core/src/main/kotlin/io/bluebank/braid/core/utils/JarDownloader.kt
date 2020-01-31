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
package io.bluebank.braid.core.utils

import io.bluebank.braid.core.logging.loggerFor
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels

class JarDownloader {

  companion object {
    private val log = loggerFor<JarDownloader>()
  }

  private val dir by lazy {
    val homeDir = System.getProperty("user.home")
    File("$homeDir/.downloaded-cordapps").also {
      it.mkdirs()
    }
  }

  fun uriToFile(url: URL): URL {
    val filename = url.path.replace(url.protocol, "")
    val destination = File(dir, filename)
    if (shouldDownload(url, destination.exists())) {
      log.info("creating ${destination.absolutePath}")
      destination.parentFile.mkdirs()
      Channels.newChannel(url.openStream()).use { rbc ->
        FileOutputStream(destination).use { fos ->
          fos.channel.transferFrom(rbc, 0, Long.MAX_VALUE);
        }
      }
    } else {
      log.info("already exists ${destination.absolutePath}")
    }
    return destination.toURI().toURL()
  }

  fun shouldDownload(destination: URL, destinationExists: Boolean) =
    destination.path.contains("SNAPSHOT") || !destinationExists
}