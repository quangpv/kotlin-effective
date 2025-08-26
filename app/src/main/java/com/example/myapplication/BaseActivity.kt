package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

abstract class BaseActivity(contentLayoutId: Int) : AppCompatActivity(contentLayoutId) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<View>(android.R.id.content).setPadding(200)
        logging(this, "Create")
    }

    override fun onRestart() {
        super.onRestart()
        logging(this, "Restart")
    }

    private fun logging(baseActivity: BaseActivity, s: String) {
        Log.e(baseActivity.javaClass.simpleName, "${baseActivity.javaClass.simpleName}_$s")
    }

    override fun onStart() {
        super.onStart()
        logging(this, "Start")
    }

    override fun onResume() {
        super.onResume()
        logging(this, "Resume")
    }

    override fun onPause() {
        super.onPause()
        logging(this, "Pause")
    }

    override fun onStop() {
        super.onStop()
        logging(this, "Stop")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        logging(this, "ActivityResult")
    }

    override fun onDestroy() {
        super.onDestroy()
        logging(this, "Destroy")
    }
}