package com.lyq.jumpquest.game

/** 所有会移动的东西的基类；(x, y) 是左上角，y 轴向下。 */
open class Entity(
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float
) {
    var vx = 0f
    var vy = 0f
    var onGround = false

    val right: Float get() = x + w
    val bottom: Float get() = y + h
    val cx: Float get() = x + w * 0.5f
    val cy: Float get() = y + h * 0.5f

    fun overlaps(o: Entity): Boolean =
        x < o.right && right > o.x && y < o.bottom && bottom > o.y

    fun overlapsRect(rx: Float, ry: Float, rw: Float, rh: Float): Boolean =
        x < rx + rw && right > rx && y < ry + rh && bottom > ry
}
