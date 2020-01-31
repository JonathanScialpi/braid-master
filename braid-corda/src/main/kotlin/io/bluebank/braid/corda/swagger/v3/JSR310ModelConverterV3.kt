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
package io.bluebank.braid.corda.swagger.v3

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import net.corda.core.utilities.loggerFor
import java.time.*

/**
 * From https://github.com/swagger-api/swagger-core/issues/1167
 *
 * to be used when calling BraidCordaJacksonInit.init()
 *
 */
class JSR310ModelConverterV3 : ModelConverter {

  companion object {
    private val log = loggerFor<JSR310ModelConverterV3>()
    private val TIME_CLASSES = setOf(
        Instant::class.java,
        OffsetDateTime::class.java,
        ZonedDateTime::class.java,
        Duration::class.java,
        LocalDateTime::class.java,
        LocalDate::class.java,
        LocalTime::class.java,
        MonthDay::class.java,
        OffsetTime::class.java,
        Period::class.java,
        Year::class.java,
        YearMonth::class.java,
        ZoneId::class.java,
        ZoneOffset::class.java,
        Duration::class.java,
        Instant::class.java,
        LocalDateTime::class.java,
        LocalDate::class.java,
        LocalTime::class.java,
        MonthDay::class.java,
        OffsetDateTime::class.java,
        OffsetTime::class.java,
        Period::class.java,
        Year::class.java,
        YearMonth::class.java,
        ZonedDateTime::class.java,
        ZoneId::class.java,
        ZoneOffset::class.java,
        ZonedDateTime::class.java,
        Duration::class.java,
        Instant::class.java,
        LocalDateTime::class.java,
        LocalDate::class.java,
        LocalTime::class.java,
        MonthDay::class.java,
        OffsetDateTime::class.java,
        OffsetTime::class.java,
        Period::class.java,
        Year::class.java,
        YearMonth::class.java,
        ZonedDateTime::class.java,
        ZoneId::class.java,
        ZoneOffset::class.java

    )

  }

  override fun resolve(type: AnnotatedType, context: io.swagger.v3.core.converter.ModelConverterContext, chain: MutableIterator<ModelConverter>): Schema<*> {
    try {
      val jsonType = Json.mapper().constructType(type.type)
      if (jsonType != null) {
        val clazz = jsonType.rawClass
        TIME_CLASSES.forEach {
          if ( it.isAssignableFrom(clazz)) {
            return StringSchema()
                .description("JSR310 encoded time representation of ${clazz.simpleName}" )
          }
        }

      }
    } catch (e: Throwable) {
      log.error("Unable to parse: $type", e)
    }

    return try {
      chain.next().resolve(type, context, chain)
    } catch (e: Exception) {
      throw RuntimeException("Unable to resolve type: ${type.type}", e)
    }
  }
}

