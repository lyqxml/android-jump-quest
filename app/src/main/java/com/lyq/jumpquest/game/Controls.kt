package com.lyq.jumpquest.game

import android.graphics.RectF

enum class Control { LEFT, RIGHT, JUMP, RESTART, NONE }

/**
 * 虚拟按键布局：所有尺寸按 dp 走，横屏下永远贴在左下 / 右下。
 * 触摸命中用圆形判定，比矩形宽容，搓招不容易掉。
 */
class Controls(private val density: Float) {

    val left = RectF()
    val right = RectF()
    val jump = RectF()
    val restart = RectF()

    var viewW = 1
    var viewH = 1
        private set

    fun layout(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        viewW = w
        viewH = h

        val pad = 16f * density
        val moveR = 34f * density
        val gap = 10f * density
        val bottomY = h - pad

        val lcx = pad + moveR
        val lcy = bottomY - moveR
        left.set(lcx - moveR, lcy - moveR, lcx + moveR, lcy + moveR)

        val rcx = lcx + moveR * 2f + gap
        right.set(rcx - moveR, lcy - moveR, rcx + moveR, lcy + moveR)

        val jr = 42f * density
        val jcx = w - pad - jr
        val jcy = bottomY - jr
        jump.set(jcx - jr, jcy - jr, jcx + jr, jcy + jr)

        val rs = 20f * density
        val rrx = w - pad - rs
        val rry = pad + rs
        restart.set(rrx - rs, rry - rs, rrx + rs, rry + rs)
    }

    fun hitTest(x: Float, y: Float): Control {
        if (insideCircle(jump, x, y, 8f * density)) return Control.JUMP
        if (insideCircle(left, x, y, 8f * density)) return Control.LEFT
        if (insideCircle(right, x, y, 8f * density)) return Control.RIGHT
        if (restart.contains(x, y)) return Control.RESTART
        return Control.NONE
    }

    private fun insideCircle(r: RectF, x: Float, y: Float, slack: Float): Boolean {
        val dx = x - r.centerX()
        val dy = y - r.centerY()
        val rad = r.width() * 0.5f + slack
        return dx * dx + dy * dy <= rad * rad
    }
}
