package com.example.myapplication


import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class InputMaskActivity : AppCompatActivity(R.layout.activity_input_mask) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editText = findViewById<EditText>(R.id.editext)
        editText.addTextChangedListener(MaskedInputWatcher(editText).apply {
            mask = "+++___/____/_____+++"
        })
    }
}