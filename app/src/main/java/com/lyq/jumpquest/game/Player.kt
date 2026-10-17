package com.lyq.jumpquest.game

import kotlin.math.abs

/**
 * 玩家：小个子 24x40，吃了能量果变大 28x58。
 * 手感相关的计时器（土狼时间、跳跃缓冲、着地挤压）都挂在这里。
 */
class Player(x: Float, y: Float) : Entity(x, y, 24f, 40f) {

    var big = false
    var facing = 1
    var invuln = 0f
    var coyote = 0f
    var jumpBuffer = 0f
    var runAnim = 0f
    var squash = 0f
    var spin = 0f

    /** 变大/变小时保持脚底不动 */
    fun changeSize(value: Boolean) {
        if (big == value) return
        val footY = bottom
        big = value
        w = if (big) BIG_W else SMALL_W
        h = if (big) BIG_H else SMALL_H
        y = footY - h
    }

    val speed: Float get() = abs(vx)

    companion object {
        const val SMALL_W = 24f
        const val SMALL_H = 40f
        const val BIG_W = 28f
        const val BIG_H = 58f
    }
}
