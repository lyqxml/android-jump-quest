package com.lyq.jumpquest

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 只有一个 Activity：全屏横屏 + GameView。
 *
 * 额外加了一层「崩溃可视化」：任何未捕获异常都会落盘，下次启动直接把堆栈显示在屏幕上，
 * 不用 adb 也能把现场发给开发者。
 */
class MainActivity : Activity() {

    private var gameView: GameView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 上次崩过：先把堆栈摆出来，避免「一打开就闪退」变成死循环
        val last = CrashLog.last(this)
        if (last != null) {
            showCrashScreen(last)
            return
        }

        try {
            val view = GameView(this)
            gameView = view
            setContentView(view)
        } catch (t: Throwable) {
            CrashLog.save(this, t, "onCreate")
            showCrashScreen(CrashLog.last(this) ?: t.stackTraceToString())
            return
        }

        try {
            hideSystemUi()
        } catch (ignored: Throwable) {
            // 某些定制 ROM 的 InsetsController 会抛异常，全屏失败不影响游戏
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            hideSystemUi()
        } catch (ignored: Throwable) {
        }
        gameView?.resumeGame()
    }

    override fun onPause() {
        gameView?.pauseGame()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            try {
                hideSystemUi()
            } catch (ignored: Throwable) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    /** 把崩溃堆栈直接显示出来，支持复制 / 清除后重开。 */
    private fun showCrashScreen(text: String) {
        val pad = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            this.text = "游戏启动/运行出错了（把这段发给开发者）"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(pad, pad, pad, pad / 2)
            setBackgroundColor(Color.rgb(18, 22, 30))
        }

        val body = TextView(this).apply {
            this.text = text
            setTextColor(Color.rgb(230, 232, 236))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(18, 22, 30))
            movementMethod = ScrollingMovementMethod()
        }

        val copy = Button(this).apply {
            this.text = "复制错误信息"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("JumpQuest crash", text))
                Toast.makeText(this@MainActivity, "已复制", Toast.LENGTH_SHORT).show()
            }
        }

        val restart = Button(this).apply {
            this.text = "清除记录并重新开始"
            setOnClickListener {
                CrashLog.clear(this@MainActivity)
                recreate()
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 22, 30))
            gravity = Gravity.START
            addView(title)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(18, 22, 30))
            addView(body, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(copy)
        root.addView(restart)
        setContentView(root)
    }
}
