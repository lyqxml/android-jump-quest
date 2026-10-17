package com.lyq.jumpquest.game

import java.util.concurrent.atomic.AtomicBoolean

/**
 * UI 线程写、渲染线程读的输入快照。
 * 跳跃按下用一次性事件（AtomicBoolean）传递，避免掉帧时吃掉边沿。
 */
class InputState {
    @Volatile var left = false
    @Volatile var right = false
    @Volatile var jump = false

    private val jumpPressed = AtomicBoolean(false)

    fun pressJump() {
        jumpPressed.set(true)
    }

    fun consumeJumpPress(): Boolean = jumpPressed.getAndSet(false)

    fun reset() {
        left = false
        right = false
        jump = false
        jumpPressed.set(false)
    }
}
