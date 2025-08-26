package com.example.myapplication

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager


class SecureScreenWhenAppInRecentTasks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        activity.preventScreenshot()
        getSecureHolder(activity).showMainLayout()
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {
        activity.keepScreenOn()
//        if (isApplicationSentToBackground(activity)) {
            getSecureHolder(activity).showSecureLayout()
//        }
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    private fun Activity.keepScreenOn() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    private fun Activity.preventScreenshot() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    private fun getSecureHolder(activity: Activity): SecureScreenHolder {
        addSecureLayoutIfNeeded(activity)
        return activity.findViewById<View>(android.R.id.content)
            .getTag(R.id.tag_secure) as SecureScreenHolder
    }

    private fun addSecureLayoutIfNeeded(activity: Activity) {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        if (rootView.getTag(R.id.tag_secure) != null) return

        val mainLayout = rootView.getChildAt(0)
        val secureLayout =
            LayoutInflater.from(activity).inflate(R.layout.splash_screen, rootView, false)
        rootView.addView(secureLayout)
        rootView.setTag(R.id.tag_secure, SecureScreenHolder(mainLayout, secureLayout))
    }

    private class SecureScreenHolder(
        val mainLayout: View, val secureLayout: View
    ) {
        fun showMainLayout() {
            mainLayout.setVisibility(View.VISIBLE)
            secureLayout.setVisibility(View.GONE)
        }

        fun showSecureLayout() {
            mainLayout.setVisibility(View.GONE)
            secureLayout.setVisibility(View.VISIBLE)
        }
    }

    @Suppress("deprecation")
    private fun isApplicationSentToBackground(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val tasks = am.getRunningTasks(1)
        if (tasks.isNotEmpty()) {
            val task = tasks[0]
            val topActivity = task.topActivity
            if (topActivity!!.packageName != context.packageName) {
                return true
            }
            if (!task.isVisible) {
                return true
            }
        }
        return false
    }

}