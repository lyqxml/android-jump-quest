package com.lyq.jumpquest.game

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 游戏世界：状态机 + 物理 + 碰撞 + 计分。
 * 只做逻辑，不碰 Canvas（绘制全在 Renderer）。
 * 每次 update 使用固定步长，保证不同帧率下手感一致。
 */
class GameWorld {

    var level: Level = Levels.level1()
    var player: Player = Player(0f, 0f)

    val enemies = ArrayList<Walker>()
    val items = ArrayList<PowerFruit>()
    val particles = ArrayList<Particle>()
    val popups = ArrayList<Popup>()
    val camera = Camera()

    @Volatile
    var state = GameState.PLAYING
        private set

    var score = 0
    var coinCount = 0
    var lives = 3
    var elapsed = 0f
    var viewWorldWidth = 900f

    private var stateTimer = 0f
    private var jumpHeld = false
    private var stompChain = 0

    init {
        rebuildWorld()
    }

    // ------------------------------------------------------------------ 生命周期

    fun restartGame() {
        score = 0
        coinCount = 0
        lives = 3
        rebuildWorld()
    }

    private fun rebuildWorld() {
        level = Levels.level1()
        enemies.clear()
        items.clear()
        particles.clear()
        popups.clear()

        for (pos in level.enemySpawns) {
            enemies.add(Walker(pos.col * TILE + 1f, (pos.row + 1) * TILE - 28f))
        }

        player = Player(
            level.spawn.col * TILE + 4f,
            (level.spawn.row + 1) * TILE - Player.SMALL_H
        )

        state = GameState.PLAYING
        stateTimer = 0f
        elapsed = 0f
        jumpHeld = false
        stompChain = 0
        camera.snap(player, level, viewWorldWidth)
    }

    // ------------------------------------------------------------------ 主循环

    fun update(dt: Float, input: InputState) {
        elapsed += dt
        stateTimer += dt
        when (state) {
            GameState.PLAYING -> updatePlaying(dt, input)
            GameState.DYING -> updateDying(dt)
            GameState.LEVEL_CLEAR, GameState.GAME_OVER -> Unit
        }
        stepParticles(dt)
        stepPopups(dt)
    }

    private fun updatePlaying(dt: Float, input: InputState) {
        jumpHeld = input.jump

        stepPlayer(dt, input)
        for (e in enemies) stepEnemy(e, dt)
        for (item in items) stepItem(item, dt)

        resolveEnemyContacts(dt)
        if (state != GameState.PLAYING) return

        resolveItemContacts()
        collectCoins()
        cullEntities()

        if (checkGoal()) return

        if (player.y > level.pixelHeight + 80f) {
            killPlayer()
            return
        }
        camera.follow(player, level, viewWorldWidth, dt)
    }

    // ------------------------------------------------------------------ 玩家

    private fun stepPlayer(dt: Float, input: InputState) {
        val p = player

        if (p.invuln > 0f) p.invuln = max(0f, p.invuln - dt)
        if (p.squash > 0f) {
            p.squash = max(0f, p.squash - dt * 2.4f)
        } else if (p.squash < 0f) {
            p.squash = min(0f, p.squash + dt * 2.4f)
        }

        // --- 水平 ---
        val dir = (if (input.right) 1 else 0) - (if (input.left) 1 else 0)
        if (dir != 0) {
            p.facing = dir
            val accel = if (p.onGround) Physics.WALK_ACCEL else Physics.AIR_ACCEL
            p.vx += dir * accel * dt
            if (p.vx > Physics.MAX_WALK) p.vx = Physics.MAX_WALK
            if (p.vx < -Physics.MAX_WALK) p.vx = -Physics.MAX_WALK
        } else {
            val friction = (if (p.onGround) Physics.FRICTION else Physics.AIR_FRICTION) * dt
            if (abs(p.vx) <= friction) {
                p.vx = 0f
            } else if (p.vx > 0f) {
                p.vx -= friction
            } else {
                p.vx += friction
            }
        }

        // --- 跳跃：缓冲 + 土狼时间 + 松手截断 ---
        if (input.consumeJumpPress()) p.jumpBuffer = Physics.JUMP_BUFFER
        p.jumpBuffer = max(0f, p.jumpBuffer - dt)
        p.coyote = if (p.onGround) Physics.COYOTE_TIME else max(0f, p.coyote - dt)

        if (p.jumpBuffer > 0f && p.coyote > 0f) {
            p.vy = Physics.JUMP_VELOCITY
            p.jumpBuffer = 0f
            p.coyote = 0f
            p.onGround = false
            p.squash = -0.18f
            spawnDust(p.cx, p.bottom, 5)
        }
        if (!input.jump && p.vy < -Physics.JUMP_CUT) p.vy = -Physics.JUMP_CUT

        // --- 位移与碰撞 ---
        moveX(p, dt)

        val wasOnGround = p.onGround
        p.onGround = false
        moveY(p, dt, true)

        if (!wasOnGround && p.onGround) {
            p.squash = 0.22f
            stompChain = 0
            spawnDust(p.cx, p.bottom, 4)
        }

        p.runAnim = if (abs(p.vx) > 12f) p.runAnim + dt * (3f + abs(p.vx) * 0.02f) else 0f
    }

    private fun updateDying(dt: Float) {
        val p = player
        p.spin += dt * 7f
        p.vy = min(p.vy + Physics.GRAVITY * dt, Physics.MAX_FALL)
        p.y += p.vy * dt
        if (stateTimer > 1.5f) {
            if (lives <= 0) {
                state = GameState.GAME_OVER
                stateTimer = 0f
            } else {
                rebuildWorld()
            }
        }
    }

    private fun hurtPlayer() {
        val p = player
        if (p.big) {
            p.changeSize(false)
            p.invuln = 1.8f
            camera.shake = 0.25f
            spawnDust(p.cx, p.cy, 8)
            popups.add(Popup(p.cx - 24f, p.y - 6f, "变小了"))
        } else {
            killPlayer()
        }
    }

    private fun killPlayer() {
        if (state != GameState.PLAYING) return
        lives = max(0, lives - 1)
        state = GameState.DYING
        stateTimer = 0f
        player.vy = -780f
        player.vx = 0f
        player.spin = 0f
        camera.shake = 0.3f
        spawnDust(player.cx, player.cy, 10)
    }

    // ------------------------------------------------------------------ 敌人

    private fun stepEnemy(e: Walker, dt: Float) {
        if (e.squash > 0f) {
            e.squash -= dt
            if (e.squash <= 0f) e.dead = true
            return
        }
        e.anim += dt
        e.vx = e.dir * e.speed
        moveX(e, dt)
        if (e.vx == 0f) e.dir = -e.dir

        e.onGround = false
        moveY(e, dt, false)

        if (e.y > level.pixelHeight + 120f) e.dead = true
    }

    private fun resolveEnemyContacts(dt: Float) {
        val p = player
        for (e in enemies) {
            if (e.dead || e.squash > 0f) continue
            if (!p.overlaps(e)) continue

            val falling = p.vy > 30f
            val fromAbove = p.bottom - p.vy * dt <= e.y + e.h * 0.7f

            if (falling && fromAbove) {
                e.squash = 0.3f
                stompChain++
                val gain = 100 * min(stompChain, 5)
                score += gain
                popups.add(Popup(e.cx - 16f, e.y - 8f, "+$gain"))
                p.vy = if (jumpHeld) Physics.STOMP_BOUNCE_HELD else Physics.STOMP_BOUNCE
                p.onGround = false
                spawnDust(e.cx, e.y + e.h * 0.4f, 6)
                camera.shake = 0.1f
            } else if (p.invuln <= 0f) {
                hurtPlayer()
                if (state != GameState.PLAYING) return
            }
        }
    }

    // ------------------------------------------------------------------ 道具

    private fun stepItem(item: PowerFruit, dt: Float) {
        item.spin += dt * 4f
        if (item.rising > 0f) {
            item.rising = max(0f, item.rising - dt)
            item.y += (item.targetY - item.y) * min(1f, dt * 12f)
            if (item.rising == 0f) item.y = item.targetY
            return
        }

        item.vx = item.dir * Physics.FRUIT_SPEED
        moveX(item, dt)
        if (item.vx == 0f) item.dir = -item.dir

        item.onGround = false
        moveY(item, dt, false)

        // 走到平台边缘掉头
        if (item.onGround) {
            val aheadX = item.cx + item.dir * (item.w * 0.5f + 8f)
            val aheadRow = floor((item.bottom + 8f) / TILE).toInt()
            if (!level.isSolid(floor(aheadX / TILE).toInt(), aheadRow)) item.dir = -item.dir
        }

        if (item.y > level.pixelHeight + 120f) item.collected = true
    }

    private fun resolveItemContacts() {
        val p = player
        for (item in items) {
            if (item.collected || item.rising > 0f) continue
            if (!p.overlaps(item)) continue
            item.collected = true
            score += 1000
            popups.add(Popup(item.cx - 20f, item.y - 12f, "+1000"))
            if (!p.big) {
                p.changeSize(true)
                p.invuln = max(p.invuln, 0.8f)
            }
            spawnDust(item.cx, item.cy, 8)
            camera.shake = 0.08f
        }
    }

    private fun collectCoins() {
        val p = player
        val c0 = floor(p.x / TILE).toInt()
        val c1 = floor((p.right - 0.01f) / TILE).toInt()
        val r0 = floor(p.y / TILE).toInt()
        val r1 = floor((p.bottom - 0.01f) / TILE).toInt()
        for (c in c0..c1) {
            for (r in r0..r1) {
                if (level.at(c, r) != 'C') continue
                level.set(c, r, '.')
                coinCount++
                score += 100
                popups.add(Popup(c * TILE + 4f, r * TILE, "+100"))
                spawnCoinBurst(c, r)
            }
        }
    }

    private fun cullEntities() {
        enemies.removeAll { it.dead }
        items.removeAll { it.collected }
    }

    // ------------------------------------------------------------------ 终点 / 方块

    private fun checkGoal(): Boolean {
        if (state != GameState.PLAYING) return false
        if (player.right < level.flagCol * TILE + 6f) return false

        state = GameState.LEVEL_CLEAR
        stateTimer = 0f
        player.vx = 0f
        player.vy = 0f
        val timeBonus = max(0, 400 - elapsed.toInt() * 2)
        score += 1000 + timeBonus
        popups.add(Popup(level.flagCol * TILE - 30f, level.pixelHeight * 0.45f, "通关奖励 +${1000 + timeBonus}"))
        spawnConfetti(level.flagCol * TILE, level.pixelHeight * 0.4f, 30)
        camera.shake = 0.2f
        return true
    }

    private fun onHeadBump(col: Int, row: Int) {
        when (level.at(col, row)) {
            '?' -> {
                level.set(col, row, 'X')
                coinCount++
                score += 200
                popups.add(Popup(col * TILE + 2f, row * TILE - 10f, "+200"))
                spawnCoinBurst(col, row)
                camera.shake = 0.12f
            }
            'M' -> {
                level.set(col, row, 'X')
                val fruit = PowerFruit(col * TILE + 3f, row * TILE - 2f)
                fruit.targetY = row * TILE - fruit.h
                fruit.rising = 0.55f
                items.add(fruit)
                spawnDust(col * TILE + TILE * 0.5f, row * TILE, 6)
                camera.shake = 0.12f
            }
            'B' -> {
                if (player.big) {
                    level.set(col, row, '.')
                    score += 50
                    spawnBrickDebris(col, row)
                    camera.shake = 0.14f
                } else {
                    spawnDust(col * TILE + TILE * 0.5f, row * TILE + TILE, 4)
                }
            }
            'X' -> spawnDust(col * TILE + TILE * 0.5f, row * TILE + TILE, 3)
        }
    }

    // ------------------------------------------------------------------ 物理

    private fun moveX(e: Entity, dt: Float) {
        e.x += e.vx * dt
        val top = floor(e.y / TILE).toInt()
        val bottom = floor((e.bottom - 0.01f) / TILE).toInt()

        if (e.vx > 0f) {
            val col = floor(e.right / TILE).toInt()
            for (row in top..bottom) {
                if (level.isSolid(col, row)) {
                    e.x = col * TILE - e.w - 0.01f
                    e.vx = 0f
                    break
                }
            }
        } else if (e.vx < 0f) {
            val col = floor(e.x / TILE).toInt()
            for (row in top..bottom) {
                if (level.isSolid(col, row)) {
                    e.x = (col + 1) * TILE + 0.01f
                    e.vx = 0f
                    break
                }
            }
        }
    }

    private fun moveY(e: Entity, dt: Float, isPlayer: Boolean) {
        e.vy = min(e.vy + Physics.GRAVITY * dt, Physics.MAX_FALL)
        e.y += e.vy * dt

        val left = floor(e.x / TILE).toInt()
        val right = floor((e.right - 0.01f) / TILE).toInt()

        if (e.vy > 0f) {
            val row = floor(e.bottom / TILE).toInt()
            for (col in left..right) {
                if (level.isSolid(col, row)) {
                    e.y = row * TILE - e.h - 0.01f
                    e.vy = 0f
                    e.onGround = true
                    break
                }
            }
        } else if (e.vy < 0f) {
            val row = floor(e.y / TILE).toInt()
            var hitCol = -1
            for (col in left..right) {
                if (level.isSolid(col, row)) {
                    hitCol = col
                    break
                }
            }
            if (hitCol >= 0) {
                e.y = (row + 1) * TILE + 0.01f
                e.vy = 0f
                if (isPlayer) onHeadBump(hitCol, row)
            }
        }
    }

    // ------------------------------------------------------------------ 粒子 / 飘字

    private fun spawnDust(x: Float, y: Float, count: Int) {
        for (i in 0 until count) {
            val a = Math.random() * Math.PI * 2
            val speed = 40f + (Math.random() * 90f).toFloat()
            particles.add(
                Particle(
                    x + (Math.random() * 10 - 5).toFloat(),
                    y,
                    (cos(a) * speed).toFloat(),
                    (sin(a) * speed - 70f).toFloat(),
                    0.45f,
                    0.45f,
                    2.5f + (Math.random() * 3f).toFloat(),
                    Color.rgb(242, 238, 226),
                    420f
                )
            )
        }
    }

    private fun spawnCoinBurst(col: Int, row: Int) {
        for (i in 0 until 7) {
            val a = -Math.PI / 2 + (Math.random() - 0.5) * 2.4
            val speed = 90f + (Math.random() * 80f).toFloat()
            particles.add(
                Particle(
                    col * TILE + TILE * 0.5f,
                    row * TILE + TILE * 0.5f,
                    (cos(a) * speed).toFloat(),
                    (sin(a) * speed).toFloat(),
                    0.6f,
                    0.6f,
                    3.5f,
                    Color.rgb(255, 208, 64),
                    900f
                )
            )
        }
    }

    private fun spawnBrickDebris(col: Int, row: Int) {
        for (i in 0 until 8) {
            val left = i % 2 == 0
            particles.add(
                Particle(
                    col * TILE + if (left) 8f else 22f,
                    row * TILE + 8f,
                    if (left) -120f else 120f,
                    -320f + (Math.random() * 120).toFloat(),
                    0.8f,
                    0.8f,
                    6f,
                    Color.rgb(196, 112, 60),
                    1200f
                )
            )
        }
    }

    private fun spawnConfetti(x: Float, y: Float, count: Int) {
        val colors = intArrayOf(
            Color.rgb(255, 96, 112),
            Color.rgb(255, 208, 64),
            Color.rgb(96, 214, 255),
            Color.rgb(140, 240, 160)
        )
        for (i in 0 until count) {
            val a = -Math.PI / 2 + (Math.random() - 0.5) * 3.2
            val speed = 120f + (Math.random() * 220f).toFloat()
            particles.add(
                Particle(
                    x,
                    y,
                    (cos(a) * speed).toFloat(),
                    (sin(a) * speed).toFloat(),
                    1.4f,
                    1.4f,
                    4f + (Math.random() * 3f).toFloat(),
                    colors[i % colors.size],
                    500f
                )
            )
        }
    }

    private fun stepParticles(dt: Float) {
        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) {
                particles.removeAt(i)
                continue
            }
            p.pvy += p.gravity * dt
            p.px += p.pvx * dt
            p.py += p.pvy * dt
            i++
        }
    }

    private fun stepPopups(dt: Float) {
        var i = 0
        while (i < popups.size) {
            val p = popups[i]
            p.life -= dt
            if (p.life <= 0f) {
                popups.removeAt(i)
                continue
            }
            p.py -= 42f * dt
            i++
        }
    }
}
