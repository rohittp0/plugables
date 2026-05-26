package com.rohittp.plugables.autoassert

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssertInjectorVisitorTest {

    private val asserterInternal = "com/example/Asserter"
    private val asserterMethod = "check"

    @Test
    fun `injects INVOKESTATIC at entry of plain instance method`() {
        val output = transform(buildClass {
            withAssertAnnotation()
            instanceMethod("doWork", "()V") { v ->
                v.visitInsn(Opcodes.RETURN)
            }
        })
        assertMethodContains(output, "doWork()V", "INVOKESTATIC $asserterInternal.$asserterMethod ()V")
    }

    @Test
    fun `skips method annotated with @NoAssert`() {
        val output = transform(buildClass {
            withAssertAnnotation()
            instanceMethod("skipMe", "()V", annotateNoAssert = true) { v ->
                v.visitInsn(Opcodes.RETURN)
            }
        })
        assertMethodDoesNotContain(output, "skipMe()V", "INVOKESTATIC $asserterInternal.$asserterMethod ()V")
    }

    @Test
    fun `skips constructor`() {
        // <init> is added by default in buildClass; just verify it isn't instrumented.
        val output = transform(buildClass { withAssertAnnotation() })
        assertMethodDoesNotContain(output, "<init>()V", "INVOKESTATIC $asserterInternal.$asserterMethod ()V")
    }

    @Test
    fun `skips synthetic method`() {
        val output = transform(buildClass {
            withAssertAnnotation()
            instanceMethod("synth\$accessor", "()V", access = Opcodes.ACC_SYNTHETIC) { v ->
                v.visitInsn(Opcodes.RETURN)
            }
        })
        assertMethodDoesNotContain(output, "synth\$accessor()V", "INVOKESTATIC $asserterInternal.$asserterMethod ()V")
    }

    @Test
    fun `skips bridge method`() {
        val output = transform(buildClass {
            withAssertAnnotation()
            instanceMethod("bridge", "()V", access = Opcodes.ACC_BRIDGE) { v ->
                v.visitInsn(Opcodes.RETURN)
            }
        })
        assertMethodDoesNotContain(output, "bridge()V", "INVOKESTATIC $asserterInternal.$asserterMethod ()V")
    }

    @Test
    fun `skips lambda$ methods`() {
        val output = transform(buildClass {
            withAssertAnnotation()
            instanceMethod("lambda\$onClick\$0", "()V") { v ->
                v.visitInsn(Opcodes.RETURN)
            }
        })
        assertMethodDoesNotContain(output, "lambda\$onClick\$0()V", "INVOKESTATIC $asserterInternal.$asserterMethod ()V")
    }

    @Test
    fun `skips equals hashCode toString`() {
        val output = transform(buildClass {
            withAssertAnnotation()
            instanceMethod("equals", "(Ljava/lang/Object;)Z") { v ->
                v.visitInsn(Opcodes.ICONST_0)
                v.visitInsn(Opcodes.IRETURN)
            }
            instanceMethod("hashCode", "()I") { v ->
                v.visitInsn(Opcodes.ICONST_0)
                v.visitInsn(Opcodes.IRETURN)
            }
            instanceMethod("toString", "()Ljava/lang/String;") { v ->
                v.visitInsn(Opcodes.ACONST_NULL)
                v.visitInsn(Opcodes.ARETURN)
            }
        })
        assertMethodDoesNotContain(output, "equals(Ljava/lang/Object;)Z", "INVOKESTATIC")
        assertMethodDoesNotContain(output, "hashCode()I", "INVOKESTATIC")
        assertMethodDoesNotContain(output, "toString()Ljava/lang/String;", "INVOKESTATIC")
    }

    @Test
    fun `skips field-derived accessor methods`() {
        val output = transform(buildClass {
            withAssertAnnotation()
            field("foo", "I")
            instanceMethod("getFoo", "()I") { v ->
                v.visitInsn(Opcodes.ICONST_0)
                v.visitInsn(Opcodes.IRETURN)
            }
            instanceMethod("setFoo", "(I)V") { v -> v.visitInsn(Opcodes.RETURN) }
        })
        assertMethodDoesNotContain(output, "getFoo()I", "INVOKESTATIC")
        assertMethodDoesNotContain(output, "setFoo(I)V", "INVOKESTATIC")
    }

    @Test
    fun `does NOT instrument classes without @AssertForAllCalls`() {
        // The class visitor still runs (in tests we drive it directly), but with no annotation
        // captured the asserterOwner is null, so onMethodEnter must inject nothing.
        val output = transform(buildClass {
            // no withAssertAnnotation()
            instanceMethod("doWork", "()V") { v -> v.visitInsn(Opcodes.RETURN) }
        })
        assertMethodDoesNotContain(output, "doWork()V", "INVOKESTATIC")
    }

    // --- helpers ---

    private fun transform(input: ByteArray): String {
        val reader = ClassReader(input)
        val writer = ClassWriter(0)
        val sw = StringWriter()
        val trace = TraceClassVisitor(writer, Textifier(), PrintWriter(sw))
        val visitor = AssertInjectorVisitor(trace)
        reader.accept(visitor, 0)
        return sw.toString()
    }

    private fun assertMethodContains(disasm: String, methodSig: String, needle: String) {
        val body = extractMethodBody(disasm, methodSig)
            ?: error("Method $methodSig not found in:\n$disasm")
        assertTrue(needle in body, "Expected '$needle' in method $methodSig body:\n$body")
    }

    private fun assertMethodDoesNotContain(disasm: String, methodSig: String, needle: String) {
        val body = extractMethodBody(disasm, methodSig)
            ?: error("Method $methodSig not found in:\n$disasm")
        assertFalse(needle in body, "Did not expect '$needle' in method $methodSig body:\n$body")
    }

    // Textifier prints method headers like:
    //   // access flags 0x1
    //   public doWork()V
    // We match the signature as the suffix of the header line.
    private fun extractMethodBody(disasm: String, methodSig: String): String? {
        val lines = disasm.lines()
        val startIdx = lines.indexOfFirst {
            val t = it.trim()
            t == methodSig || t.endsWith(" $methodSig")
        }
        if (startIdx == -1) return null
        val endIdx = (startIdx + 1 until lines.size).firstOrNull { i ->
            val l = lines[i].trim()
            l.startsWith("// access flags") || l == "}"
        } ?: lines.size
        return lines.subList(startIdx, endIdx).joinToString("\n")
    }

    // --- ASM bytecode builder DSL for tests ---

    private fun buildClass(configure: ClassBuilder.() -> Unit): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        val builder = ClassBuilder(cw)
        cw.visit(
            Opcodes.V21,
            Opcodes.ACC_PUBLIC,
            "com/example/Sample",
            null,
            "java/lang/Object",
            null,
        )
        builder.configure()
        // default no-arg ctor
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(0, 0)
        ctor.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private inner class ClassBuilder(val cw: ClassWriter) {

        fun withAssertAnnotation() {
            val av = cw.visitAnnotation(
                "Lcom/rohittp/plugables/autoassert/AssertForAllCalls;",
                false,
            )
            av.visit("klass", Type.getObjectType(asserterInternal))
            av.visit("method", asserterMethod)
            av.visitEnd()
        }

        fun field(name: String, descriptor: String) {
            val fv = cw.visitField(Opcodes.ACC_PRIVATE, name, descriptor, null, null)
            fv.visitEnd()
        }

        fun instanceMethod(
            name: String,
            descriptor: String,
            access: Int = Opcodes.ACC_PUBLIC,
            annotateNoAssert: Boolean = false,
            body: (MethodVisitor) -> Unit,
        ) {
            val mv = cw.visitMethod(access, name, descriptor, null, null)
            if (annotateNoAssert) {
                val av: AnnotationVisitor = mv.visitAnnotation(
                    "Lcom/rohittp/plugables/autoassert/NoAssert;",
                    false,
                )
                av.visitEnd()
            }
            mv.visitCode()
            body(mv)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
        }
    }
}
