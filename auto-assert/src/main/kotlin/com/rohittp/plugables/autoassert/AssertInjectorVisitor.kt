package com.rohittp.plugables.autoassert

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter
import org.objectweb.asm.commons.Method
import kotlin.metadata.jvm.KotlinClassMetadata

internal const val ASSERT_FOR_ALL_CALLS_FQCN =
    "com.rohittp.plugables.autoassert.AssertForAllCalls"

private const val ASSERT_FOR_ALL_CALLS_DESC =
    "Lcom/rohittp/plugables/autoassert/AssertForAllCalls;"
private const val NO_ASSERT_DESC =
    "Lcom/rohittp/plugables/autoassert/NoAssert;"
private const val KOTLIN_METADATA_DESC = "Lkotlin/Metadata;"

internal class AssertInjectorVisitor(cv: ClassVisitor) : ClassVisitor(Opcodes.ASM9, cv) {

    // Asserter target captured from @AssertForAllCalls
    private var asserterOwner: String? = null
    private var asserterMethod: String? = null

    // Exact JVM signatures to skip: "name+descriptor", e.g. "getFoo()I", "setFoo(I)V"
    private val accessorSigs = HashSet<String>()

    // Property accessor method names derived from Kotlin metadata
    private val propertyAccessorNames = HashSet<String>()

    // Kotlin @Metadata annotation values, collected by KotlinMetadataCollector
    private var metadataK = 1
    private var metadataMv = intArrayOf()
    private var metadataD1 = arrayOf<String>()
    private var metadataD2 = arrayOf<String>()
    private var metadataXs = ""
    private var metadataPn = ""
    private var metadataXi = 0

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        val av = super.visitAnnotation(descriptor, visible)
        return when (descriptor) {
            ASSERT_FOR_ALL_CALLS_DESC -> AsserterCollector(av)
            KOTLIN_METADATA_DESC -> KotlinMetadataCollector(av)
            else -> av
        }
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?,
    ): FieldVisitor {
        // Compute expected getter/setter names for this backing field.
        //  - val foo: T      -> getFoo(): T
        //  - var foo: T      -> getFoo(): T, setFoo(T): void
        //  - var isReady: Z  -> isReady(): Z, setReady(Z): void
        val cap = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        if (name.startsWith("is") && name.length > 2 && name[2].isUpperCase() && descriptor == "Z") {
            accessorSigs += "$name()Z"
            accessorSigs += ("set" + name.removePrefix("is")) + "(Z)V"
        } else {
            accessorSigs += "get$cap()$descriptor"
            accessorSigs += "set$cap($descriptor)V"
        }
        return super.visitField(access, name, descriptor, signature, value)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val base = super.visitMethod(access, name, descriptor, signature, exceptions)

        val isCtor = name == "<init>" || name == "<clinit>"
        val isSyntheticOrBridge = (access and (Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE)) != 0
        val isAnonymousLambda = name.contains("lambda$") || name.contains($$"$anonymous")

        if (isCtor || isSyntheticOrBridge || isAnonymousLambda) return base
        if (name == "equals" && descriptor == "(Ljava/lang/Object;)Z") return base
        if (name == "hashCode" && descriptor == "()I") return base
        if (name == "toString" && descriptor == "()Ljava/lang/String;") return base

        if ((name + descriptor) in accessorSigs) return base
        if (name in propertyAccessorNames) return base

        return object : AdviceAdapter(ASM9, base, access, name, descriptor) {
            private var skip = false
            private var firstLine = -1

            override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
                if (desc == NO_ASSERT_DESC) {
                    skip = true
                }
                return super.visitAnnotation(desc, visible)
            }

            override fun visitLineNumber(line: Int, start: Label) {
                if (firstLine == -1) firstLine = line
                super.visitLineNumber(line, start)
            }

            override fun onMethodEnter() {
                if (skip) return
                val owner = asserterOwner ?: return
                val methodName = asserterMethod ?: return
                if (firstLine != -1) {
                    val lbl = Label()
                    visitLabel(lbl)
                    visitLineNumber(firstLine, lbl)
                }
                invokeStatic(Type.getObjectType(owner), Method(methodName, "()V"))
            }
        }
    }

    // --- @AssertForAllCalls value collector ---

    private inner class AsserterCollector(av: AnnotationVisitor?) :
        AnnotationVisitor(Opcodes.ASM9, av) {

        override fun visit(name: String?, value: Any?) {
            when (name) {
                "klass" -> if (value is Type) asserterOwner = value.internalName
                "method" -> if (value is String) asserterMethod = value
            }
            super.visit(name, value)
        }
    }

    // --- Kotlin metadata parsing ---

    private fun parseKotlinMetadata() {
        try {
            val header = Metadata(
                kind = metadataK,
                metadataVersion = if (metadataMv.isEmpty()) intArrayOf(2, 1, 0) else metadataMv,
                data1 = metadataD1,
                data2 = metadataD2,
                extraString = metadataXs,
                packageName = metadataPn,
                extraInt = metadataXi,
            )
            when (val classMetadata = KotlinClassMetadata.readLenient(header)) {
                is KotlinClassMetadata.Class -> {
                    for (property in classMetadata.kmClass.properties) {
                        registerPropertyAccessors(property.name)
                    }
                }

                else -> {}
            }
        } catch (_: Exception) {
            // Metadata parsing failed; fall back to field-based approach only
        }
    }

    private fun registerPropertyAccessors(name: String) {
        val cap = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        if (name.startsWith("is") && name.length > 2 && name[2].isUpperCase()) {
            propertyAccessorNames += name
            propertyAccessorNames += "set" + name.removePrefix("is")
        } else {
            propertyAccessorNames += "get$cap"
            propertyAccessorNames += "set$cap"
        }
    }

    // --- ASM annotation visitors for collecting @kotlin.Metadata values ---

    private inner class KotlinMetadataCollector(av: AnnotationVisitor?) :
        AnnotationVisitor(Opcodes.ASM9, av) {

        override fun visit(name: String?, value: Any?) {
            when (name) {
                "k" -> metadataK = value as? Int ?: 1
                "xi" -> metadataXi = value as? Int ?: 0
                "xs" -> metadataXs = value as? String ?: ""
                "pn" -> metadataPn = value as? String ?: ""
            }
            super.visit(name, value)
        }

        override fun visitArray(name: String): AnnotationVisitor? {
            val inner = super.visitArray(name)
            return when (name) {
                "mv" -> IntArrayCollector(inner) { metadataMv = it }
                "d1" -> StringArrayCollector(inner) { metadataD1 = it }
                "d2" -> StringArrayCollector(inner) { metadataD2 = it }
                else -> inner
            }
        }

        override fun visitEnd() {
            super.visitEnd()
            parseKotlinMetadata()
        }
    }

    private class IntArrayCollector(
        av: AnnotationVisitor?,
        private val onEnd: (IntArray) -> Unit,
    ) : AnnotationVisitor(Opcodes.ASM9, av) {
        private val values = mutableListOf<Int>()
        override fun visit(name: String?, value: Any?) {
            if (value is Int) values.add(value)
            super.visit(name, value)
        }

        override fun visitEnd() {
            super.visitEnd()
            onEnd(values.toIntArray())
        }
    }

    private class StringArrayCollector(
        av: AnnotationVisitor?,
        private val onEnd: (Array<String>) -> Unit,
    ) : AnnotationVisitor(Opcodes.ASM9, av) {
        private val values = mutableListOf<String>()
        override fun visit(name: String?, value: Any?) {
            if (value is String) values.add(value)
            super.visit(name, value)
        }

        override fun visitEnd() {
            super.visitEnd()
            onEnd(values.toTypedArray())
        }
    }
}
