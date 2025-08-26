package com.example.effect.end_user_test

import com.example.effect.BaseTest
import com.example.effect.WatchEffect
import com.example.effect.ref
import com.example.effect.runEffect
import kotlin.test.Test
import kotlin.test.assertEquals

class EffectTest : BaseTest() {

    @Test
    fun `effect should render at start`() {
        val count = ref(0)

        runEffect {
            logs += "effect: ${count.value}"
        }
        expectLogs("effect: 0")
    }

    @Test
    fun `effect should cleanup dependencies when closed`() {
        val a = ref(1)

        val closable = runEffect {
            logs += "effect: " + a.value
        }

        closable.close()
        a.value = 2
        expectLogs("effect: 1")
    }

    @Test
    fun `nested effects - should render by top down`() {
        val a = ref(1)
        val b = ref(10)
        runEffect {
            logs += "outer: ${a.value}"
            runEffect {
                logs += "inner: ${b.value}"
            }
        }
        expectLogs("outer: 1", "inner: 10")
    }

    @Test
    fun `nested effects - should render only effect with its dependencies changes`() {
        val a = ref(1)
        val b = ref(10)
        runEffect {
            logs += "outer: ${a.value}"
            runEffect {
                logs += "inner: ${b.value}"
            }
        }
        logs.clear()
        b.value = 30
        expectLogs("inner: 30")
    }

    @Test
    fun `nested effects - should not render child when its parent's dependencies changes`() {
        val a = ref(1)
        val b = ref(10)
        var innerCount = 0

        runEffect {
            logs += "outer: ${a.value}"
            runEffect {
                logs += "inner: ${b.value}"
                innerCount++
            }
        }
        logs.clear()
        innerCount = 0
        a.value = 2
        expectLogs("outer: 2", "inner: 10")
        assertEquals(innerCount, 1)
    }

    @Test
    fun `nested effects - should not render child when it disposed`() {
        val a = ref(1)
        val b = ref(10)
        var closable: AutoCloseable? = null

        runEffect {
            logs += "outer: ${a.value}"
            closable = runEffect {
                logs += "inner: ${b.value}"
            }
        }
        logs.clear()
        closable?.close()
        b.value = 2
        expectLogs()
    }

    @Test
    fun `nested effects - the old child effect should be closed when parent re-render`() {
        val a = ref(1)
        val b = ref(10)
        var previous: AutoCloseable? = null

        runEffect {
            a.value
            val closable = runEffect {
                b.value
            }
            if (previous == null) previous = closable
        }
        a.value = 2
        assertEquals((previous as? WatchEffect)?.isClosed, true)
    }
}
