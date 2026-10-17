package com.lyq.jumpquest

import android.app.Application

/** 尽量早地装崩溃钩子：Activity 还没起来就崩，也能留下记录。 */
class JumpQuestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
