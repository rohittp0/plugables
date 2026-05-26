package com.rohittp.plugables.autoassert

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor

abstract class AssertInjectorFactory :
    AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor,
    ): ClassVisitor = AssertInjectorVisitor(nextClassVisitor)

    override fun isInstrumentable(classData: ClassData): Boolean =
        classData.classAnnotations.contains(ASSERT_FOR_ALL_CALLS_FQCN)
}
