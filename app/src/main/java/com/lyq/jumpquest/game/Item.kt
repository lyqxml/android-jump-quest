package com.lyq.jumpquest.game

/**
 * 能量果：从问号块里顶出来，先向上冒（rising），然后左右走。
 * 吃到后玩家变大。
 */
class PowerFruit(x: Float, y: Float) : Entity(x, y, 26f, 26f) {
    var dir = 1
    var rising = 0f
    var targetY = y
    var collected = false
    var spin = 0f
}

/** 粒子：尘土、金币碎屑、彩带 */
class Particle(
    var px: Float,
    var py: Float,
    var pvx: Float,
    var pvy: Float,
    var life: Float,
    var maxLife: Float,
    var size: Float,
    var color: Int,
    var gravity: Float
)

/** 飘字：+100 / +200 之类 */
class Popup(
    var px: Float,
    var py: Float,
    val text: String,
    var life: Float = 0.9f
) {
    val maxLife: Float = life
}
