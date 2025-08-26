package com.example.myapplication.effect

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner


fun LifecycleOwner.effect(block: () -> Unit) {
    val eff = WatchEffect(null, block)
    var isSubscribed = false
    lifecycle.addObserver(object : DefaultLifecycleObserver {

        override fun onStart(owner: LifecycleOwner) {
            if (isSubscribed) return
            eff.start()
            isSubscribed = true
        }

        override fun onDestroy(owner: LifecycleOwner) {
            lifecycle.removeObserver(this)
            eff.close()
        }
    })
}