package com.example.effect.end_user_test

import com.example.effect.BaseTest
import com.example.effect.RefImpl
import com.example.effect.ref
import com.example.effect.runEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefTest : BaseTest() {

    @Test
    fun `ref as data holder`() {
        val count = ref(0)
        assertEquals(count.value, 0)
        count.value = 1
        assertEquals(count.value, 1)
    }

    @Test
    fun `ref updates should trigger effect`() {
        val count = ref(0)
        runEffect {
            logs += "effect: ${count.value}"
        }
        count.value = 1
        expectLogs("effect: 0", "effect: 1")
    }

    @Test
    fun `ref update same value should not trigger effect`() {
        val count = ref(0)
        runEffect {
            logs += "effect: ${count.value}"
        }
        count.value = 0
        expectLogs("effect: 0")
    }

    @Test
    fun `ref with no observers should trigger onInactive`() {
        var inactiveCalled = false
        val a = object : RefImpl<Int>(0) {
            override fun onInactive() {
                inactiveCalled = true
            }
        }

        val disposable = runEffect {
            a.value
        }

        assertFalse(inactiveCalled)
        disposable.close()
        assertTrue(inactiveCalled)
    }
}
