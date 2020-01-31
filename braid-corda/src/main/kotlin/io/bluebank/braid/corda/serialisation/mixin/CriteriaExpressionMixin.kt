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
import net.corda.core.node.services.vault.CriteriaExpression

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "@class")
@JsonSubTypes(
    JsonSubTypes.Type(value = CriteriaExpression.AggregateFunctionExpression::class),
    JsonSubTypes.Type(value = CriteriaExpression.BinaryLogical::class),
    JsonSubTypes.Type(value = CriteriaExpression.ColumnPredicateExpression::class),
    JsonSubTypes.Type(value = CriteriaExpression.Not::class)
)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@io.swagger.v3.oas.annotations.media.Schema(
    type = "object",
    title = "CriteriaExpression",
    discriminatorProperty = "@class",
    discriminatorMapping = [
      DiscriminatorMapping(value = ".CriteriaExpression${'$'}AggregateFunctionExpression", schema = CriteriaExpression.AggregateFunctionExpression::class),
      DiscriminatorMapping(value = ".CriteriaExpression${'$'}BinaryLogical", schema = CriteriaExpression.BinaryLogical::class),
      DiscriminatorMapping(value = ".CriteriaExpression${'$'}ColumnPredicateExpression", schema = CriteriaExpression.ColumnPredicateExpression::class),
      DiscriminatorMapping(value = ".CriteriaExpression${'$'}Not", schema = CriteriaExpression.Not::class)
    ],
    subTypes = [CriteriaExpression.AggregateFunctionExpression::class,
      CriteriaExpression.AggregateFunctionExpression::class,
      CriteriaExpression.ColumnPredicateExpression::class,
      CriteriaExpression.Not::class]
)
interface CriteriaExpressionMixin {
}
