package com.example.effect

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

abstract class BaseTest {
    protected val logs = mutableListOf<String>()

    @BeforeTest
    open fun beforeEach() {

    }

    @AfterTest
    open fun afterEach() {
        logs.clear()
    }

    fun expectLogs(vararg logs: String) {
        assertEquals(logs.toList(), this.logs)
    }
}