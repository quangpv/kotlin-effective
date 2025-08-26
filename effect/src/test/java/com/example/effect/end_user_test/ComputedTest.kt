package com.example.effect.end_user_test

import com.example.effect.BaseTest
import com.example.effect.computed
import com.example.effect.ref
import com.example.effect.runEffect
import kotlin.test.Test
import kotlin.test.assertEquals

class ComputedTest : BaseTest() {

    @Test
    fun `computed should compute when first get value`() {
        val a = ref(1)
        val sum = computed {
            logs.add(a.value.toString())
        }
        sum.value
        expectLogs("1")
    }

    @Test
    fun `computed should recalculate when dependency changes`() {
        val a = ref(1)
        val b = ref(2)
        val sum = computed { a.value + b.value }

        assertEquals(3, sum.value)

        a.value = 5
        assertEquals(7, sum.value)

        b.value = 3
        assertEquals(8, sum.value)
    }

    @Test
    fun `computed should cache until invalidated`() {
        val a = ref(1)
        var computeCount = 0
        val double = computed {
            computeCount++
            a.value * 2
        }

        assertEquals(2, double.value)
        assertEquals(1, computeCount)

        // Should not recompute if value unchanged
        assertEquals(2, double.value)
        assertEquals(1, computeCount)

        a.value = 3
        assertEquals(6, double.value)
        assertEquals(2, computeCount)
    }

    @Test
    fun `computed should compute when collected`() {
        val a = ref(1)
        val b = ref(2)
        val sum = computed { a.value + b.value }

        runEffect {
            logs.add(sum.value.toString())
        }
        expectLogs("3")
    }

    @Test
    fun `nested computed - should compute for the first retrieve`() {
        val a = ref(1)
        val value = computed {
            computed { a.value * 2 }
        }
        assertEquals(value.value.value, 2)
    }

    @Test
    fun `nested computed - should recompute when dependency changes`() {
        val a = ref(1)
        val value = computed {
            computed { a.value * 2 }
        }
        a.value = 3

        assertEquals(value.value.value, 6)
    }

    @Test
    fun `nested computed - should recompute child when it's parent is re-render`() {
        val a = ref(1)
        val c = computed {
            a.value
            computed {
                logs.add("recomputed ${a.value}")
                a.value * 2
            }
        }
        c.value.value
        expectLogs("recomputed 1")
        a.value = 2
        c.value.value
        expectLogs("recomputed 1", "recomputed 2")
    }

    @Test
    fun `nested computed - only recompute computed when its dependency changes`() {
        val a = ref(1)
        val b = ref(2)
        val c = computed {
            val x1 = computed {
                logs.add("x1 ${a.value}")
                a.value + 1
            }
            val x2 = computed {
                logs.add("x2 ${b.value}")
                b.value + 2
            }
            x1 to x2
        }
        c.value.first.value
        c.value.second.value
        logs.clear()

        b.value = 15
        c.value.first.value
        c.value.second.value

        expectLogs("x2 15")
    }
}
