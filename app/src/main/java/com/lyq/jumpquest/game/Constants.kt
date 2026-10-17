package com.lyq.jumpquest.game

/** 世界单位：1 格 = 32 单位 */
const val TILE = 32f

/** 固定物理步长（秒），120Hz */
const val STEP = 1f / 120f

/**
 * 手感参数集中在这里，改数值就能调手感。
 * 跳跃高度 ≈ JUMP_VELOCITY² / (2 * GRAVITY) = 845² / 4800 ≈ 148 单位 ≈ 4.6 格
 */
object Physics {
    const val GRAVITY = 2400f
    const val MAX_FALL = 1500f

    const val WALK_ACCEL = 2600f
    const val AIR_ACCEL = 1900f
    const val MAX_WALK = 265f
    const val FRICTION = 3000f
    const val AIR_FRICTION = 500f

    const val JUMP_VELOCITY = -845f
    const val JUMP_CUT = 300f
    const val COYOTE_TIME = 0.10f
    const val JUMP_BUFFER = 0.13f

    const val STOMP_BOUNCE = -540f
    const val STOMP_BOUNCE_HELD = -820f

    const val ENEMY_SPEED = 62f
    const val FRUIT_SPEED = 85f
}

enum class GameState { PLAYING, DYING, LEVEL_CLEAR, GAME_OVER }

data class TilePos(val col: Int, val row: Int)
