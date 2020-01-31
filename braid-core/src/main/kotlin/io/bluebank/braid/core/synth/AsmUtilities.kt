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

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.signature.SignatureWriter
import java.lang.reflect.*
import javax.validation.constraints.NotNull
import kotlin.reflect.full.memberProperties
import io.swagger.v3.oas.annotations.Parameter as ParameterAnnotation
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation
import org.objectweb.asm.Type as AsmType

private val objectClassRef = Object::class.java.canonicalName.replace('.', '/')

/**
 * Writes out to the bytecode a default constructor
 * This is usually done by the Java / Kotlin compiler, but here using ASM we need to be explicit`
 *
 *  From https://asm.ow2.io/asm4-guide.pdf
 * The methods of the ClassVisitor class must be called in the following order,
specified in the Javadoc of this class:
visit visitSource?
visitOuterClass? ( visitAnnotation | visitAttribute )*
( visitInnerClass | visitField | visitMethod )*
visitEnd

 */
fun ClassWriter.writeDefaultConstructor() {
  val constructorMethod = visitMethod(
    Opcodes.ACC_PUBLIC, // public method
    "<init>",   // method name
    "()V",      // JVM description for this method - it's a default constructor
    null,       // signature (null means not generic)
    null        // no checked exceptions
  )
  constructorMethod.visitCode()
  constructorMethod.visitVarInsn(Opcodes.ALOAD, 0)
  // call super constructor
  constructorMethod.visitMethodInsn(
    Opcodes.INVOKESPECIAL,
    "java/lang/Object",
    "<init>",
    "()V",
    false
  )

  constructorMethod.visitInsn(Opcodes.RETURN)
  constructorMethod.visitMaxs(1, 1)
  constructorMethod.visitEnd()
}

/**
 * Works out the JVM byte code "signature" for a given type
 * The JVM signature denotes the specialisation of generic type
 * @return null if the type is not generic
 * @return a JVM bytecode signature for the type parameterisation
 */
fun Type.genericSignature(): String? {
  return when (this) {
    is ParameterizedType -> {
      SignatureWriter().let {
        it.writeSignature(this)
        it.toString()
      }
    }
    else -> null
  }
}

/**
 * add all parameters of the constructor as fields in the class writer
 * this method is used to build a payload type representing the parameters of a constructor
 */
fun ClassWriter.addFields(constructor: Constructor<*>) = addFields(constructor.parameters)

/**
 * add all parameters of the method as fields in the class writer
 * this method is used to build a payload type representing the parameters of a method
 */
fun ClassWriter.addFields(method: Method) = addFields(method.parameters)

/**
 * add all parameters as fields in the class writer
 */
fun ClassWriter.addFields(parameters: Array<Parameter>) =
  parameters.forEach { addField(it) }

/**
 * add a parameter as a field to the class being written
 */
fun ClassWriter.addField(parameter: Parameter) {
  val visitField = visitField(
    Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,   // access permissions
    parameter.name,                                  // field name
    AsmType.getDescriptor(parameter.type),           // raw type description
    parameter.parameterizedType.genericSignature(),  // generic parameterisation
    null                                      // default value is null
  )
  val visitAnnotation = visitField
    .visitAnnotation(AsmType.getDescriptor(NotNull::class.java), true)
  visitAnnotation.visitEnd()
  // and maybe copy the parameter's annotations to field annotations
  if (SynthesisOptions.strategyUsesAnnotations) {
    if (SynthesisOptions.isParameterTypeAnnotatable(parameter.type)) {
      parameter.annotations.forEach { annotation ->
        if (!SynthesisOptions.isParameterAnnotation(annotation)) {
          return
        }
        val visible = true // I'm not sure what this means or why it might ever be false?
        if (annotation.annotationClass == ParameterAnnotation::class) {
          // add this as a Schema annotation because Swagger doesn't
          // notice a Parameter annotation if it's applied to a field
          visitField.visitAnnotation(
            AsmType.getDescriptor(SchemaAnnotation::class.java),
            visible
          )
            .apply {
              // read the important @Parameter properties and make them @Schema properties
              val parameterAnnotation = annotation as ParameterAnnotation
              if (parameterAnnotation.schema != null) {
                this.copyAnnotationProperties(parameterAnnotation.schema)
              }
              if (parameterAnnotation.description != null) {
                this.addNamedValue("description", parameterAnnotation.description)
              }
              if (parameterAnnotation.example != null) {
                this.addNamedValue("example", parameterAnnotation.example)
              }
            }
            .visitEnd()
        } else {
          // add a clone of the annotation
          visitField.visitAnnotation(annotation.classDescriptor(), visible)
            .copyAnnotationProperties(annotation)
            .visitEnd()
        }
      }
    }
  }
  visitField.visitEnd()
}

fun Annotation.classDescriptor(): String {
  return AsmType.getDescriptor(this.annotationClass.java)
}

fun AnnotationVisitor.copyAnnotationProperties(
  annotation: Annotation
): AnnotationVisitor {
  // get the name and value of each field in the annotation
  // by using reflection to determine what fields exist in this type of annotation
  // then using call to extract the run-time value from the annotation instance
  annotation.annotationClass.memberProperties.forEach { member ->
    val name = member.name
    val value = member.call(annotation)
    this.addNamedValue(name, value)
  }
  return this
}

fun AnnotationVisitor.addNamedValue(name: String?, value: Any?) {
  // section 4.2.1 of https://asm.ow2.io/asm4-guide.pdf says that an annotation may
  // contain named fields, and that fields are restricted to the following types:
  // primitive, String, Class, enum, or annotation ... or an array of these
  when {
    value == null -> this.visit(name, value)
    value is Annotation -> // nested annotation
      this.visitAnnotation(name, value.classDescriptor())
        .copyAnnotationProperties(value) // recursive
        .visitEnd()
    value.javaClass.isArray -> {
      val visitor = this.visitArray(name)
      (value as Array<*>).forEach {
        // section 4.2.2 of https://asm.ow2.io/asm4-guide.pdf says,
        // "since the elements of an array are not named, the name arguments are ignored
        // by the methods of the visitor returned by visitArray, and can be set to null"
        visitor.addNamedValue(null, it) // recursive
      }
      visitor.visitEnd()
    }
    value.javaClass.isEnum ->
      this.visitEnum(
        name,
        AsmType.getDescriptor(value.javaClass),
        (value as Enum<*>).name
      )
    else -> if (value is Class<*>) {
      val type = org.objectweb.asm.Type.getType(value)
      this.visit(name, type)
    } else {
      this.visit(name, value)
    }
  }
}

/**
 * add the declaration of a class
 * NOTE: callers must call [ClassWriter.endVisit] when the class body is complete
 */
internal fun ClassWriter.declareSimplePublicClass(className: String) {
  val jvmByteCodeName = className.replace('.', '/');
  visit(
    Opcodes.V1_8,
    Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
    jvmByteCodeName,
    null, // no generic signature on this type
    objectClassRef, // base type is Object
    emptyArray() // no implemented interfaces
  )
}

/**
 * write the generic parameterisation signature to a [SignatureVisitor]
 */
private fun SignatureVisitor.writeSignature(type: java.lang.reflect.Type) {
  when (type) {
    is ParameterizedType -> writeParameterizedTypeSignature(type)
    is Class<*> -> writeClassTypeSignature(type)
    is WildcardType -> writeWildCardTypeSignature(type)
    is TypeVariable<*> -> writeTypeVariableSignature(type)
    else -> error("unhandled type: $type")
  }
}

private fun <D : GenericDeclaration> SignatureVisitor.writeTypeVariableSignature(type: TypeVariable<D>) {
  visitTypeVariable(type.name)
}

private fun SignatureVisitor.writeWildCardTypeSignature(type: WildcardType) {
  when {
    type.upperBounds.size == 1 -> {
      visitTypeArgument(SignatureVisitor.INSTANCEOF).writeSignature(type.upperBounds[0])
    }
    type.lowerBounds.size == 1 -> {
      visitTypeArgument(SignatureVisitor.INSTANCEOF).writeSignature(type.lowerBounds[0])
    }
    else -> error("unhandled wildcard type of upper bound size ${type.upperBounds.size} and lower bound size ${type.lowerBounds.size}")
  }
}

private fun SignatureVisitor.writeClassTypeSignature(type: Class<*>) {
  visitClassType(AsmType.getInternalName(type))
  visitEnd()
}

private fun SignatureVisitor.writeParameterizedTypeSignature(
  type: ParameterizedType
) {
  visitClassType(AsmType.getInternalName(type.rawType as Class<*>))
  type.actualTypeArguments.forEach { a ->
    val tv = visitTypeArgument(SignatureVisitor.INSTANCEOF)
    tv.writeSignature(a)
  }
  visitEnd()
}