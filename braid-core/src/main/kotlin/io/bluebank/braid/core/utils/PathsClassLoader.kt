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
import java.net.URI
import java.net.URL
import java.net.URLClassLoader

object PathsClassLoader {
      var log = loggerFor<PathsClassLoader>()

  private val tempFileDownloader = JarDownloader()

  fun jarsClassLoader(vararg jarPaths: String) =
    jarsClassLoader(jarPaths.toList())

  fun jarsClassLoader(jarPaths: Collection<String>): ClassLoader {
    return when {
      jarPaths.isEmpty() -> Thread.currentThread().contextClassLoader
      else -> {
        val urls = jarPaths.asSequence().map {
          urlOrFiles(it)
        }.flatMap { it.asSequence() }.toList().toTypedArray()
        log.info("Using jars:${urls.toList()}")
        URLClassLoader(urls, Thread.currentThread().contextClassLoader)
      }
    }
  }

  private fun urlOrFiles(urlOrFileName: String): List<URL> {
    return try {
      // attempt to download the file if available
      listOf(tempFileDownloader.uriToFile(URI(urlOrFileName).toURL()))
    } catch (err: Exception) {
      // URI throws this exception if urlOrFileName starts with like "C:\" on Windows
      localFiles(File(urlOrFileName))
    }
  }

  private fun localFiles(directoryOrFile: File): List<URL> {
    return when {
      !directoryOrFile.exists() -> emptyList() // if the file doesn't exist we return an emptyList
      directoryOrFile.isDirectory -> directoryOrFile.listFiles()?.map { localFiles(it) }?.flatten()
        ?: emptyList()
      else -> listOf(directoryOrFile.toURI().toURL())
    }
  }
}

fun List<String>.toJarsClassLoader(): ClassLoader {
  return PathsClassLoader.jarsClassLoader(this)
}
