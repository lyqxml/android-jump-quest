package com.lyq.jumpquest.game

/**
 * 巡逻怪（紫团子）：左右来回走，遇到墙掉头，被踩扁。
 * squash > 0 表示正在播压扁动画，播完标记 dead 交给世界移除。
 */
class Walker(x: Float, y: Float) : Entity(x, y, 30f, 28f) {
    var dir = -1
    var speed = Physics.ENEMY_SPEED
    var squash = 0f
    var anim = 0f
    var dead = false
}
