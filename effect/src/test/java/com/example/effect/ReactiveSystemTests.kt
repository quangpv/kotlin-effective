package com.example.effect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReactiveSystemTests {

    @Test
    fun testRefTracksAccess() {
        val ref = ref(10)
        var accessed = false

        runEffect {
            accessed = ref.value == 10
        }.close()

        assertTrue(accessed)
    }

    @Test
    fun testRefTriggersEffect() {
        val ref = ref(1)
        var observed = 0

        val effect = runEffect {
            observed = ref.value
        }

        assertEquals(1, observed)

        ref.value = 42
        assertEquals(42, observed)

        effect.close()
    }

    @Test
    fun testRefNoTriggerOnSameValue() {
        val ref = ref(5)
        var count = 0

        val effect = runEffect {
            ref.value
            count++
        }

        assertEquals(1, count)
        ref.value = 5
        assertEquals(1, count)

        effect.close()
    }

    @Test
    fun testComputedCachesValue() {
        var computeCount = 0
        val base = ref(1)
        val comp = computed {
            computeCount++
            base.value * 2
        }

        assertEquals(2, comp.value)
        assertEquals(1, computeCount)

        assertEquals(2, comp.value)
        assertEquals(1, computeCount)

        base.value = 3
        assertEquals(6, comp.value)
        assertEquals(2, computeCount)
    }

    @Test
    fun testComputedTriggersDependentEffect() {
        val base = ref(2)
        val double = computed { base.value * 2 }

        var result = 0
        val effect = runEffect {
            result = double.value
        }

        assertEquals(4, result)

        base.value = 10
        assertEquals(20, result)

        effect.close()
    }

    @Test
    fun testBatchEffectExecution() {
        val a = ref(1)
        val b = ref(2)
        var total = 0

        runEffect {
            total = a.value + b.value
        }

        batch {
            a.value = 10
            b.value = 20
        }

        assertEquals(30, total)
    }

    @Test
    fun testEffectCleanup() {
        val a = ref(1)
        var value = 0

        val effect = runEffect {
            value = a.value
        }

        effect.close()
        a.value = 2
        assertEquals(1, value)
    }

    @Test
    fun testNestedEffectsDispose() {
        val parentEffect = WatchEffect(null) {}
        val childEffect = WatchEffect(parentEffect) {}

        assertNotNull(parentEffect.childTail)
        assertEquals(childEffect, parentEffect.childTail)

        parentEffect.close()
        assertNull(parentEffect.childTail)
    }

    @Test
    fun testMultipleEffectsOnSameRef() {
        val ref = ref(0)
        var a = 0
        var b = 0

        val effectA = runEffect { a = ref.value + 1 }
        val effectB = runEffect { b = ref.value + 2 }

        ref.value = 10
        assertEquals(11, a)
        assertEquals(12, b)

        effectA.close()
        effectB.close()
    }

    @Test
    fun testEffectDisposalPreventsTrigger() {
        val ref = ref(1)
        var triggerCount = 0

        val effect = runEffect {
            ref.value
            triggerCount++
        }

        assertEquals(1, triggerCount)

        effect.close()
        ref.value = 2

        assertEquals(1, triggerCount)
    }

    @Test
    fun testEffectReuseFromPool() {
        val ref = ref(0)
        var log = mutableListOf<Int>()

        repeat(5) {
            val effect = runEffect { log.add(ref.value + it) }
            effect.close()
        }

        ref.value = 1

        // All effects are closed, so no new logs should be added
        assertEquals(5, log.size)
    }

    @Test
    fun testDeepComputedChain() {
        val base = ref(1)
        val double = computed { base.value * 2 }
        val triple = computed { double.value * 3 }
        val result = computed { triple.value + 1 }

        assertEquals(1 * 2 * 3 + 1, result.value)

        base.value = 2
        assertEquals(2 * 2 * 3 + 1, result.value)
    }

    @Test
    fun testBatchWithNestedEffect() {
        val a = ref(1)
        val b = ref(2)
        var total = 0

        val effect = runEffect {
            total = a.value + b.value
        }

        batch {
            a.value = 10
            runEffect {
                b.value = 20
            }.close()
        }

        assertEquals(30, total)

        effect.close()
    }

    @Test
    fun testEffectDoesNotReRunWhenUntrackedRefChanges() {
        val a = ref(1)
        val b = ref(2)
        var value = 0

        runEffect {
            value = a.value
        }

        b.value = 100 // should not affect the effect
        assertEquals(1, value)

        a.value = 2
        assertEquals(2, value)
    }

    @Test
    fun testNoMemoryLeakAfterRefChanges() {
        val ref = ref(0)
        var triggerCount = 0

        val effect = runEffect {
            ref.value
            triggerCount++
        }

        repeat(10) {
            ref.value = it + 1
        }

        assertEquals(11, triggerCount)
        effect.close()

        // After close, value changes shouldn't trigger more updates
        ref.value = 100
        assertEquals(11, triggerCount)
    }
}