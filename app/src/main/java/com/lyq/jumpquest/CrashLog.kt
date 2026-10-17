package com.lyq.jumpquest

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃记录：把未捕获异常写到 SharedPreferences + 外部私有目录，
 * 下次启动时 MainActivity 直接把堆栈画在屏幕上，没有 adb 也能看到现场。
 */
object CrashLog {

    private const val TAG = "JumpQuestCrash"
    private const val PREFS = "jumpquest_crash"
    private const val KEY_LAST = "last_crash"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                save(app, throwable, thread.name)
            } catch (ignored: Throwable) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    fun save(context: Context, throwable: Throwable, threadName: String = Thread.currentThread().name) {
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        val header = buildString {
            appendLine("时间: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            appendLine("线程: " + threadName)
            appendLine("设备: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")")
            appendLine("系统: Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT)
            appendLine("ABI: " + Build.SUPPORTED_ABIS.joinToString())
            appendLine("应用: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
            appendLine("---")
        }
        val text = header + sw.toString()
        Log.e(TAG, text)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST, text).apply()

        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, "crash.log").writeText(text)
        } catch (ignored: Throwable) {
        }
    }

    fun last(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LAST).apply()
    }
}
