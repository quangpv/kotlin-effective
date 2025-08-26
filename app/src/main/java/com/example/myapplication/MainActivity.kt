package com.example.myapplication

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import com.example.myapplication.effect.batch
import com.example.myapplication.effect.computed
import com.example.myapplication.effect.effect
import com.example.myapplication.effect.ref
import com.example.myapplication.effect.runEffect

class MainActivity : BaseActivity(R.layout.activity_main) {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = findViewById<TextView>(R.id.txtText)

        val counter = ref(0)
        val counter2 = ref("Counter")

        val result = computed {
            if (counter.value % 3 == 0) computed {
                "${counter.value} - ${counter2.value}"
            } else computed {
                "${counter.value} + ${counter2.value}"
            }
        }
        effect {
            Log.e("Effect", "Counter ${counter.value}")
            if (counter.value % 2 == 0) runEffect {
                view.text = result.value.value
                Log.e("Effect", "Render")
            }
        }
        view.setOnClickListener {
            batch {
                counter.value++
                counter2.value = "Counter + ${Math.random()}"
            }
        }
        findViewById<View>(R.id.btnGotoTemp).setOnClickListener {
        }
    }
}