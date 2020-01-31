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
package io.bluebank.braid.corda.rest

import io.vertx.ext.web.RoutingContext
import org.apache.commons.lang3.LocaleUtils
import java.net.HttpCookie
import java.time.Instant
import java.util.*
import javax.ws.rs.core.*

class HttpHeadersImpl(val context: RoutingContext) : HttpHeaders {
  override fun getLength(): Int {
    return context.request().getHeader(HttpHeaders.CONTENT_LENGTH)?.toInt() ?: -1
  }

  override fun getAcceptableLanguages(): MutableList<Locale> {
    val result = context.request().headers().getAll(HttpHeaders.ACCEPT_LANGUAGE)
      .map { LocaleUtils.toLocale(it) }
    return when (result.isEmpty()) {
      true -> mutableListOf(Locale("*"))
      else -> result.toMutableList()
    }
  }

  override fun getRequestHeader(name: String?): MutableList<String> {
    if (name == null) error("name must not be null")
    return context.request().headers().getAll(name)
  }

  override fun getCookies(): MutableMap<String, Cookie> {
    return context.request().headers().getAll(HttpHeaders.COOKIE).flatMap {
      HttpCookie.parse(it)
    }.map {
      it.name to Cookie(it.name, it.value, it.domain, it.version.toString())
    }.toMap().toMutableMap()
  }

  override fun getRequestHeaders(): MultivaluedMap<String, String> {
    val headers = MultivaluedHashMap<String, String>()
    context.request().headers().apply {
      names().forEach { name ->
        val values = getAll(name)
        headers[name] = values
      }
    }
    return headers
  }

  override fun getDate(): Date? {
    val value = context.request().getHeader("Date")
    return when (value) {
      null -> null
      else -> Date.from(Instant.parse(value))
    }
  }

  override fun getHeaderString(name: String?): String? {
    return context.request().getHeader(name)
  }

  override fun getAcceptableMediaTypes(): MutableList<MediaType> {
    val default = listOf(MediaType.MEDIA_TYPE_WILDCARD)
    val mediaTypes = context.request().getHeader(HttpHeaders.ACCEPT)?.let { header ->
      header.split(",").map { it.trim() }
    } ?: default
    return mediaTypes
      .map { mediaType ->
        okhttp3.MediaType.parse(mediaType)
      }.map {
        if (it != null) {
          MediaType(it.type(), it.subtype())
        } else {
          MediaType.WILDCARD_TYPE
        }
      }.toMutableList()
  }

  override fun getMediaType(): MediaType? {
    val contentType = getHeaderString(HttpHeaders.CONTENT_TYPE) ?: return null
    return okhttp3.MediaType.parse(contentType)?.let { MediaType(it.type(), it.subtype()) }
  }

  override fun getLanguage(): Locale? {
    val lang = getHeaderString(HttpHeaders.CONTENT_LANGUAGE) ?: return null
    val languages = lang.split(",").map { it.trim() }
    return languages.first().let<String, Locale> {
      val parts = it.split("-")
      val language = parts[0]
      if (parts.size >= 2) {
        val country = parts[1]
        Locale(language, country)
      } else {
        Locale(language)
      }
    }
  }
}