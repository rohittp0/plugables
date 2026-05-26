package com.rohittp.plugables.sample

import com.rohittp.plugables.autoassert.AssertForAllCalls
import com.rohittp.plugables.autoassert.NoAssert
import java.util.concurrent.atomic.AtomicInteger

object DemoAsserter {
    val count = AtomicInteger()

    @JvmStatic
    fun record() {
        count.incrementAndGet()
    }
}

@AssertForAllCalls(klass = DemoAsserter::class, method = "record")
class DemoTarget {

    fun doWork(): Int = 42

    fun doMore() {
        // intentionally empty
    }

    @NoAssert
    fun skipped() {
        // intentionally empty
    }
}
