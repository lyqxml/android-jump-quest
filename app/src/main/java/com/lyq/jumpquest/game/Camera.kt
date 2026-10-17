package com.lyq.jumpquest.game

import kotlin.math.max
import kotlin.math.min

/** 横向跟随摄像机，带一点缓动和震屏。 */
class Camera {
    var x = 0f
    var shake = 0f
    var shakeX = 0f
    var shakeY = 0f

    fun snap(player: Player, level: Level, viewWorldWidth: Float) {
        x = (player.cx - viewWorldWidth * 0.42f)
            .coerceIn(0f, max(0f, level.pixelWidth - viewWorldWidth))
        shake = 0f
        shakeX = 0f
        shakeY = 0f
    }

    fun follow(player: Player, level: Level, viewWorldWidth: Float, dt: Float) {
        val goal = (player.cx - viewWorldWidth * 0.42f)
            .coerceIn(0f, max(0f, level.pixelWidth - viewWorldWidth))
        x += (goal - x) * min(1f, dt * 10f)

        if (shake > 0f) {
            shake = max(0f, shake - dt)
            val amp = shake * 40f
            shakeX = ((Math.random() * 2 - 1) * amp).toFloat()
            shakeY = ((Math.random() * 2 - 1) * amp).toFloat()
        } else {
            shakeX = 0f
            shakeY = 0f
        }
    }
}
