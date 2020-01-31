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
package io.bluebank.braid.core.synth

import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.PropertyMetadata
import io.bluebank.braid.core.logging.loggerFor
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.RefUtils.constructRef
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import java.io.IOException
import java.lang.reflect.Parameter
import java.lang.reflect.Type
import kotlin.reflect.KClass

// used to get property type name from QualifiedTypeNameConverter
// which is currently up in the braid-corda module
interface TypeNames {

  fun getTypeName(type: JavaType?, beanDesc: BeanDescription?): String
}

// deriving from AbstractModelConverter class instead of from ModelConverter interface
// helps to implement the getPropTypeName function
class SyntheticModelConverter(val typeNames: TypeNames) : ModelConverter {

  private data class Registered(
    val parameters: List<Parameter>
  )

  companion object {
    private val log = loggerFor<SyntheticModelConverter>()
    private val syntheticClasses: MutableMap<String, Registered> = mutableMapOf()

    fun registerClass(
      className: String,
      parameters: List<Parameter>
    ) {
      log.info("registerClass -- $className(${parameters.joinToString(", ")})")
      syntheticClasses.put(className, Registered(parameters))
    }

    // this could be static here, or per class, or constructed JIT on the stack
    private val _mapper = Json.mapper()
    private val _modelResolverFunctions = ModelResolverFunctions(_mapper)

    private fun constructType(type: Type): JavaType {
      return _mapper.constructType(type)
    }

    private fun getBeanDescription(javaType: JavaType): BeanDescription {
      return _mapper.serializationConfig.introspect(javaType)
    }

    private fun clone(property: Schema<*>?): Schema<*>? {
      var property: Schema<*>? = property
      if (property == null) return property
      try {
        val cloneName: String? = property.getName()
        property = Json.mapper().readValue(Json.pretty(property), Schema::class.java)
        property.setName(cloneName)
      } catch (e: IOException) {
        log.error("Could not clone property, e")
      }
      return property
    }
  }

  override fun resolve(
    type: AnnotatedType,
    context: ModelConverterContext,
    chain: MutableIterator<ModelConverter>?
  ): Schema<*>? {
    // the next 5 lines of source code are copied from CustomModelConverterV3
    val javaType = constructType(type.type)
    return try {
      when (javaType) {
        null -> chain?.next()?.resolve(type, context, chain)
        else -> {
          // create model just in time, because we need a ModelConverterContext to do it
          // and because there may be several different contexts especially when testing

          // I guess the following using context.defineModel and type.isResolveAsRef is
          // how we should implement this but it's not how CustomModelConverterV3 does it
          var model: Schema<*> = getOrCreateModel(javaType, context)
            ?: // className doesn't identify a synthetic class for which we create a model
            return chain?.next()?.resolve(type, context, chain)
          if (type.isResolveAsRef) {
            model = Schema<Any>().`$ref`(constructRef(model.name))
          }
          return model
        }
      }
    } catch (e: Throwable) {
      log.error("failed to parse or resolve type: ${type.type}")
      throw e
    }
  }

  private fun getOrCreateModel(
    javaType: JavaType,
    context: ModelConverterContext
  ): Schema<*>? {
    // pick a SyntheticRefOption based on the ObjectStrategy
    val opt = when (SynthesisOptions.currentStrategy) {
      SynthesisOptions.ObjectStrategy.NONE -> SyntheticRefOption.REF
      SynthesisOptions.ObjectStrategy.INLINE -> SyntheticRefOption.INLINE
      SynthesisOptions.ObjectStrategy.ALLOF -> SyntheticRefOption.ALLOF
    }
    if (opt == SyntheticRefOption.REF) {
      // SynthesisOptions.ObjectStrategy.NONE implies we want this converter disabled
      // instead annotations instances are added to the synthetic type by AsmUtilities
      // SyntheticRefOption.REF only exists for unit-testing, to verify that the
      // resolveSchema function is able to create the same kind of schema as vanilla
      return null
    }
    // test whether this is even for a synthetic type, else return null
    val className = javaType.rawClass.canonicalName
    val registered: Registered = syntheticClasses[className] ?: return null
    val (parameters) = registered
    // return the cached schema if there is one --
    // so beware reusing the same SyntheticModelConverter instance
    // if you change the option value during unit test suites --
    // currently FlowInitiatorTest.FlowResults.get() creates a new DocsHandlerV3 instance
    // each time it's run, so SyntheticModelConverter is short-lived in that test suite
    val found = context.definedModels[className]
    if (found != null)
      return found;
    // resolve and return the new schema
    log.info("Creating model for $className")

    fun getParameterAnnotations(propName: String, propType: Type): Array<Annotation> {
      val parameter = parameters.find { it.name == propName }!!
      return parameter.annotations
    }

    val created =
      resolveSchema(javaType, context, opt, ::getParameterAnnotations)
    context.defineModel(className, created, javaType.rawClass, null)
    return created
  }

  // public function for unit testing
  fun resolveSchema(
    kclass: KClass<*>,
    context: ModelConverterContext,
    opt: SyntheticRefOption
  ): Schema<*> {
    val annotatedType = AnnotatedType().type(kclass.java)
    val javaType = constructType(annotatedType.type)
    fun getParameterAnnotations(propName: String, propType: Type): Array<Annotation> {
      return emptyArray()
    }
    return resolveSchema(javaType, context, opt, ::getParameterAnnotations)
  }

  private fun resolveSchema(
    javaType: JavaType,
    context: ModelConverterContext,
    opt: SyntheticRefOption,
    getParameterAnnotations: (String, Type) -> Array<Annotation>
  ): Schema<*> {
    // assume that type is object and not e.g. simple nor array
    return ObjectSchema()
      .name(javaType.rawClass.canonicalName)
      .apply {
        val props: MutableMap<String, Schema<*>> = mutableMapOf()
        val required: MutableList<String> = mutableListOf()
        val beanDesc: BeanDescription = getBeanDescription(javaType)
        val properties = beanDesc.findProperties()
        for (propDef in properties) {
          val propName = propDef.name
          val member = propDef.primaryMember
          val propType = member.type
          // val annotations: Array<Annotation> = emptyArray()
          val annotations = getParameterAnnotations(propName, propType)

          // pass emptyArray instead of annotations becaue otherwise
          // are applied to the referenced $ref type, not to this property
          val atype = getPropertyAnnotationType(propType, emptyArray(), propName, this)

          // we need to poke the property's name into its schema
          // so clone the schema to avoid corrupting the original
          // this unconditional clone isn't in swagger core v2.0.9 but is a
          // bugfix which was added to the current version of swagger core
          var property: Schema<*> = clone(context.resolve(atype))
            ?: error("Expected to be able to resolve any property type")


          if (property.`get$ref`() == null) {
            // this is in ModelResolver but I don't know whether it's used here
            val md: PropertyMetadata = propDef.metadata
            val isRequired: Boolean? = md.getRequired()
            if (isRequired != null && isRequired) {
              required.add(propName)
            } else {
              if (propDef.isRequired()) {
                required.add(propName)
              }
            }
            // here ModelResolver gets the accessMode and may
            // set property.readOnly or property.writeOnly
            // but this function is only for a simplistic synthetic type
            // so we can assume that the property is read-write
          }

          // now this is where and how ModelResolver converts the inline schema to a $ref
          if (opt != SyntheticRefOption.INLINE) {
            if (property != null && !propType.isContainerType) {
              if ("object" == property.type) {
                // create a reference for the property
                // ModelResolver calls super._typeName but that I think that only works in
                // the initial ModelResolver which has cached a typename, so here we do
                // what I hope next best thing and get it directly from our TypeNames class.
                // If this proves inadequate in future then try implementing the TypeNames
                // interface on a subclass of the ModelResolver
                val propBeanDesc: BeanDescription = getBeanDescription(propType)
                var pName: String? = typeNames.getTypeName(propType, propBeanDesc)
                if (!property.name.isNullOrBlank()) {
                  pName = property.name
                }
                if (context.definedModels.containsKey(pName)) {
                  property = Schema<Any?>().`$ref`(constructRef(pName))
                }
              } else if (property.`$ref` != null) {
                // I don't know what this case is about or how to trigger/test it? perhaps
                //  this might not play nicely with SyntheticRefOption.INLINE if we use that
                property =
                  Schema<Any?>().`$ref`(if (property.`$ref`.isNotEmpty()) property.`$ref` else property.name)
              }
            }
          }

          if (opt == SyntheticRefOption.ALLOF && property.`$ref` != null) {
            property = ComposedSchema().apply {
              allOf = listOf(property)
            }
          }

          property.name = propName

          // apply the annotations now
          atype.ctxAnnotations = convertParameters(annotations)
          _modelResolverFunctions.resolveSchemaMembers(property, atype)
          // annotations.forEach { property.applyAnnotation(it) }

          props[propName] = property
        }
        if (props.isNotEmpty()) {
          this.properties = props
          // SyntheticModelConverterTest shows this should not be present
          // this.required = required
        }
        if (required.isNotEmpty()) {
          this.required = required
        }
      }
  }

  fun getPropertyAnnotationType(
    propType: java.lang.reflect.Type,
    annotations: Array<Annotation>,
    propName: String,
    model: Schema<*> // parent schema -- type is "object"
  ): AnnotatedType {
    return AnnotatedType().type(propType)
      .ctxAnnotations(annotations)
      .parent(model as Schema<*>?)
      .resolveAsRef(false) // annotatedType.isResolveAsRef()
      .jsonViewAnnotation(null) // annotatedType.getJsonViewAnnotation()
      .skipSchemaName(true)
      .schemaProperty(true)
      .propertyName(propName)
      .jsonUnwrappedHandler { t: AnnotatedType ->
        // This member is to optionally implement support for @JsonUnwrapped annotation
        // which is a type of Jackson annotation.
        // Our parameter annotation filter in SynthesisOptions will ensure that
        // this type of annotation won't exist in the synthetic type,
        // so here is the trivial implementation of this member.
        return@jsonUnwrappedHandler Schema<Any?>()
      }
  }
}

enum class SyntheticRefOption {
  /**
   * Use `$ref` which is the vanilla behaviour (for testing but not what we want usually)
   */
  REF,
  /**
   * Use `allOf` instead of `$ref` (the whole point of this exercise, see ./README.md)
   */
  ALLOF,
  /**
   * Include a copy of the referenced/resolved schema inline instead of `$ref` or `allOf`
   */
  INLINE
}
