package com.lyq.jumpquest

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.lyq.jumpquest.game.Control
import com.lyq.jumpquest.game.Controls
import com.lyq.jumpquest.game.GameState
import com.lyq.jumpquest.game.GameWorld
import com.lyq.jumpquest.game.InputState
import com.lyq.jumpquest.game.Renderer
import com.lyq.jumpquest.game.STEP

/**
 * SurfaceView + 独立渲染线程的游戏主视图。
 *
 * 线程模型：
 *  - UI 线程：只负责把触摸事件翻译成 InputState，以及处理"重开"这类一次性动作；
 *  - 渲染线程：固定步长推进逻辑（120Hz），每一帧 lockCanvas 绘制。
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val world = GameWorld()
    private val input = InputState()
    private val renderer = Renderer(resources.displayMetrics.density)
    private val controls = Controls(resources.displayMetrics.density)

    /** pointerId -> 当前按住的虚拟键（支持多指同时按左 + 跳） */
    private val pointerControls = HashMap<Int, Control>()

    @Volatile private var running = false
    @Volatile private var surfaceReady = false
    @Volatile private var pendingRestart = false

    private var thread: GameThread? = null
    private var viewW = 1
    private var viewH = 1

    init {
        holder.addCallback(this)
        keepScreenOn = true
        isFocusable = true
    }

    // ------------------------------------------------------------------ 生命周期

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        viewW = width
        viewH = height
        controls.layout(width, height)
        updateViewport()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopThread()
    }

    fun pauseGame() {
        stopThread()
    }

    fun resumeGame() {
        if (holder.surface?.isValid == true) {
            surfaceReady = true
            controls.layout(viewW, viewH)
            startThread()
        }
    }

    private fun startThread() {
        if (thread != null) return
        running = true
        thread = GameThread().also { it.start() }
    }

    private fun stopThread() {
        running = false
        val t = thread ?: return
        thread = null
        try {
            t.join(700)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    // ------------------------------------------------------------------ 触摸

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val control = controls.hitTest(event.getX(idx), event.getY(idx))
                when (control) {
                    Control.RESTART -> pendingRestart = true
                    Control.NONE -> {
                        val st = world.state
                        if (st == GameState.GAME_OVER || st == GameState.LEVEL_CLEAR) {
                            pendingRestart = true
                        }
                    }
                    else -> pointerControls[id] = control
                }
                syncInput()
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val current = pointerControls[id] ?: continue
                    if (current == Control.JUMP) continue
                    val hit = controls.hitTest(event.getX(i), event.getY(i))
                    if (hit == Control.LEFT || hit == Control.RIGHT) {
                        pointerControls[id] = hit
                    }
                }
                syncInput()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointerControls.remove(event.getPointerId(event.actionIndex))
                if (event.actionMasked == MotionEvent.ACTION_UP) pointerControls.clear()
                syncInput()
            }

            MotionEvent.ACTION_CANCEL -> {
                pointerControls.clear()
                syncInput()
            }
        }
        return true
    }

    private fun syncInput() {
        var l = false
        var r = false
        var j = false
        for (c in pointerControls.values) {
            when (c) {
                Control.LEFT -> l = true
                Control.RIGHT -> r = true
                Control.JUMP -> j = true
                else -> Unit
            }
        }
        if (j && !input.jump) input.pressJump()
        input.left = l
        input.right = r
        input.jump = j
    }

    // ------------------------------------------------------------------ 循环

    private fun updateViewport() {
        if (viewH <= 0) return
        val scale = viewH / world.level.pixelHeight
        if (scale > 0f) world.viewWorldWidth = viewW / scale
    }

    private inner class GameThread : Thread("JumpQuestLoop") {
        override fun run() {
            var last = System.nanoTime()
            var accum = 0f

            while (running) {
                val now = System.nanoTime()
                var delta = (now - last) / 1_000_000_000f
                last = now
                if (delta > 0.25f) delta = 0.25f

                if (pendingRestart) {
                    pendingRestart = false
                    world.restartGame()
                    input.reset()
                }

                accum += delta
                var steps = 0
                while (accum >= STEP && steps < 8) {
                    updateViewport()
                    world.update(STEP, input)
                    accum -= STEP
                    steps++
                }
                if (accum > 0.4f) accum = 0f

                drawFrame()
            }
        }
    }

    private fun drawFrame() {
        if (!surfaceReady) return
        val h = holder
        var canvas: Canvas? = null
        try {
            canvas = h.lockCanvas()
            if (canvas != null && viewW > 1 && viewH > 1) {
                renderer.draw(canvas, world, viewW, viewH, controls)
            }
        } catch (e: Exception) {
            // 表面正在销毁时忽略本次绘制
        } finally {
            if (canvas != null) {
                try {
                    h.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    // 忽略
                }
            }
        }
    }
}
