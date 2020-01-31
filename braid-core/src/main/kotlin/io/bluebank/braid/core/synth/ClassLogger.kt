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

import org.objectweb.asm.*

/**
 * This class exists here for logging though it's only used for testing, because
 * the readClass method doesn't work with the synthetic type, but readBytes does.
 */

class ClassLogger : ClassVisitor(Opcodes.ASM5) {

  companion object {
    fun readClass(clazz: Class<*>): String {
      val self = ClassLogger()
      ClassReader(clazz.name).accept(self, ClassReader.EXPAND_FRAMES);
      return self.logger.lines.joinToString(separator = "\r\n")
    }

    fun readBytes(bytes: ByteArray): String {
      val self = ClassLogger()
      ClassReader(bytes).accept(self, ClassReader.EXPAND_FRAMES);
      return self.logger.lines.joinToString(separator = "\r\n")
    }

    private val option = object {
      val hiddenAnnotations = listOf(
        "org/jetbrains/annotations/NotNull",
        "org/jetbrains/annotations/Nullable",
        "javax/validation/constraints/NotNull"
      )
      val showAllAnnotationContent = false
      val showSwaggerAnnotationContent = false
    }

    private fun getAnnotationVisitor(
      desc: String?,
      logger: Logger,
      level: Int
    ): AnnotationVisitor? {
      return if ((option.showSwaggerAnnotationContent && desc != null && desc.startsWith("Lio/swagger")) || option.showAllAnnotationContent) AnnotationLogger(
        logger,
        level + 1
      ) else null
    }

    private fun showAnnotation(desc: String?): Boolean {
      if (desc == null) return true // shouldn't happen
      return !option.hiddenAnnotations.any { desc.contains(it) }
    }
  }

  class Logger {
    val lines: MutableList<String> = mutableListOf()
    fun addLine(level: Int, line: String) {
      lines.add("  ".repeat(level) + line)
    }
  }

  class FieldLogger(private val logger: Logger, private val level: Int) :
    FieldVisitor(Opcodes.ASM5) {

    private fun addLine(line: String) {
      logger.addLine(level, line)
    }

    override fun visitAnnotation(p0: String?, p1: Boolean): AnnotationVisitor? {
      val desc = p0
      if (!showAnnotation(desc)) return null
      addLine("ANNOTATION desc=$p0, visible=$p1")
      super.visitAnnotation(p0, p1)
      return getAnnotationVisitor(desc, logger, level)
    }

    override fun visitTypeAnnotation(
      p0: Int,
      p1: TypePath?,
      p2: String?,
      p3: Boolean
    ): AnnotationVisitor? {
      val desc = p2
      if (!showAnnotation(desc)) return null
      addLine("TYPE ANNOTATION typeRef=$p0, typePath=$p1, desc=$p2, visible=$p3")
      super.visitTypeAnnotation(p0, p1, p2, p3)
      return getAnnotationVisitor(desc, logger, level)
    }

    override fun visitAttribute(p0: Attribute?) {
      addLine("ATTRIBUTE attr=$p0")
      super.visitAttribute(p0)
    }
  }

  class AnnotationLogger(private val logger: Logger, private val level: Int) :
    AnnotationVisitor(Opcodes.ASM5) {

    private fun addLine(line: String) {
      logger.addLine(level, line)
    }

    override fun visit(p0: String?, p1: Any?) {
      addLine("PRIMITIVE name=$p0, value=$p1")
      super.visit(p0, p1)
    }

    override fun visitAnnotation(p0: String?, p1: String?): AnnotationVisitor? {
      val desc = p1
      if (!showAnnotation(desc)) return null
      addLine("ANNOTATION name=$p0, desc=$p1")
      super.visitAnnotation(p0, p1)
      return getAnnotationVisitor(desc, logger, level)
    }

    override fun visitArray(p0: String?): AnnotationVisitor? {
      addLine("ARRAY name=$p0")
      super.visitArray(p0)
      return getAnnotationVisitor(null, logger, level)
    }

    override fun visitEnum(p0: String?, p1: String?, p2: String?) {
      addLine("ENUM name=$p0, desc=$p1, value=$p2")
      super.visitEnum(p0, p1, p2)
    }
  }

  class MethodLogger(private val logger: Logger, private val level: Int) :
    MethodVisitor(Opcodes.ASM5) {

    private fun addLine(line: String) {
      logger.addLine(level, line)
    }

    override fun visitParameter(p0: String?, p1: Int) {
      addLine("PARAMETER name=$p0, access=$p1")
      super.visitParameter(p0, p1)
    }

    override fun visitAnnotationDefault(): AnnotationVisitor? {
      addLine("DEFAULT")
      super.visitAnnotationDefault()
      return getAnnotationVisitor(null, logger, level)
    }

    override fun visitAnnotation(p0: String?, p1: Boolean): AnnotationVisitor? {
      val desc = p0
      if (!showAnnotation(desc)) return null
      addLine("ANNOTATION desc=$p0, visible=$p1")
      super.visitAnnotation(p0, p1)
      return getAnnotationVisitor(desc, logger, level)
    }

    override fun visitParameterAnnotation(
      p0: Int,
      p1: String?,
      p2: Boolean
    ): AnnotationVisitor? {
      val desc = p1
      if (!showAnnotation(desc)) return null
      addLine("PARAMETER ANNOTATION parameter=$p0, desc=$p1, visible=$p2")
      super.visitParameterAnnotation(p0, p1, p2)
      return getAnnotationVisitor(desc, logger, level)
    }

    override fun visitTypeAnnotation(
      p0: Int,
      p1: TypePath?,
      p2: String?,
      p3: Boolean
    ): AnnotationVisitor? {
      val desc = p2
      if (!showAnnotation(desc)) return null
      addLine("TYPE ANNOTATION typeRef=$p0, typePath=$p1, desc=$p2, visible=$p3")
      super.visitTypeAnnotation(p0, p1, p2, p3)
      return getAnnotationVisitor(desc, logger, level)
    }

    override fun visitAttribute(p0: Attribute?) {
      addLine("ATTRIBUTE attr=$p0")
      super.visitAttribute(p0)
    }
  }

  val logger = Logger()
  private val level = 0

  private fun addLine(line: String) {
    logger.addLine(level, line)
  }

  override fun visit(
    p0: Int,
    p1: Int,
    p2: String?,
    p3: String?,
    p4: String?,
    p5: Array<out String>?
  ) {
    addLine("CLASS version=$p0, access=$p1, name=$p2, signature=$p3, superName=$p4, interfaces=$p5")
    super.visit(p0, p1, p2, p3, p4, p5)
  }

  override fun visitField(
    p0: Int,
    p1: String?,
    p2: String?,
    p3: String?,
    p4: Any?
  ): FieldVisitor {
    addLine("FIELD access=$p0, name=$p1, desc=$p2, signature=$p3, value=$p4")
    super.visitField(p0, p1, p2, p3, p4)
    return FieldLogger(logger, level + 1)
  }

  override fun visitAnnotation(p0: String?, p1: Boolean): AnnotationVisitor? {
    val desc = p0
    if (!showAnnotation(desc)) return null
    addLine("ANNOTATION desc=$p0, visible=$p1")
    super.visitAnnotation(p0, p1)
    if (p0!!.contains("kotlin/Metadata")) {
      // let's not visit this ... it's long and contains binary data
      return null
    }
    return getAnnotationVisitor(desc, logger, level)
  }

  override fun visitAttribute(p0: Attribute?) {
    addLine("ATTRIBUTE attr=$p0")
    super.visitAttribute(p0)
  }

  override fun visitTypeAnnotation(
    p0: Int,
    p1: TypePath?,
    p2: String?,
    p3: Boolean
  ): AnnotationVisitor? {
    val desc = p2
    if (!showAnnotation(desc)) return null
    addLine("TYPE ANNOTATION typeRef=$p0, typePath=$p1, desc=$p2, visible=$p3")
    super.visitTypeAnnotation(p0, p1, p2, p3)
    return getAnnotationVisitor(desc, logger, level)
  }

  override fun visitMethod(
    p0: Int,
    p1: String?,
    p2: String?,
    p3: String?,
    p4: Array<out String>?
  ): MethodVisitor {
    addLine("METHOD access=$p0, name=$p1, desc=$p2, signature=$p3, exceptions=$p4")
    super.visitMethod(p0, p1, p2, p3, p4)
    return MethodLogger(logger, level + 1)
  }
}

