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
package io.bluebank.braid.corda.serialisation.mixin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import net.corda.core.node.services.vault.ColumnPredicate

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "@class")
@JsonSubTypes(
  JsonSubTypes.Type(value = ColumnPredicate.AggregateFunction::class),
  JsonSubTypes.Type(value = ColumnPredicate.Between::class),
  JsonSubTypes.Type(value = ColumnPredicate.BinaryComparison::class),
  JsonSubTypes.Type(value = ColumnPredicate.CollectionExpression::class),
  JsonSubTypes.Type(value = ColumnPredicate.EqualityComparison::class),
  JsonSubTypes.Type(value = ColumnPredicate.NullExpression::class),
  JsonSubTypes.Type(value = ColumnPredicate.Likeness::class)
    )
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@io.swagger.v3.oas.annotations.media.Schema(
    type = "object",
    title = "ColumnPredicate",
    discriminatorProperty = "@class",
    discriminatorMapping = [
        DiscriminatorMapping(value = ".ColumnPredicate${'$'}AggregateFunction", schema = ColumnPredicate.AggregateFunction::class),
        DiscriminatorMapping(value = ".ColumnPredicate${'$'}Between", schema = ColumnPredicate.Between::class),
        DiscriminatorMapping(value = ".ColumnPredicate${'$'}BinaryComparison", schema = ColumnPredicate.BinaryComparison::class),
        DiscriminatorMapping(value = ".ColumnPredicate${'$'}CollectionExpression", schema = ColumnPredicate.CollectionExpression::class),
        DiscriminatorMapping(value = ".ColumnPredicate${'$'}EqualityComparison", schema = ColumnPredicate.EqualityComparison::class),
        DiscriminatorMapping(value = ".ColumnPredicate${'$'}NullExpression", schema = ColumnPredicate.NullExpression::class),
        DiscriminatorMapping(value = ".ColumnPredicate${'$'}Likeness", schema = ColumnPredicate.Likeness::class)
    ],
    subTypes = [ColumnPredicate.AggregateFunction::class]
)
interface ColumnPredicateMixin<Long>
