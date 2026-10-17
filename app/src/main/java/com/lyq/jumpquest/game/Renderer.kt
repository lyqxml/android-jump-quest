package com.lyq.jumpquest.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 全部美术都是代码画出来的几何图形，没有任何图片素材（不涉及版权素材）。
 * 世界坐标 → 屏幕坐标：scale = 屏幕高 / 关卡世界高，再平移 -camera.x。
 */
class Renderer(private val density: Float) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    private val rect = RectF()
    private val path = Path()

    private var skyShader: LinearGradient? = null
    private var skyW = 0
    private var skyH = 0

    private val skyTop = Color.rgb(64, 156, 236)
    private val skyBottom = Color.rgb(196, 234, 255)

    private val dirt = Color.rgb(178, 118, 72)
    private val dirtDark = Color.rgb(146, 92, 54)
    private val grass = Color.rgb(96, 196, 96)
    private val grassDark = Color.rgb(68, 164, 74)
    private val brick = Color.rgb(198, 112, 62)
    private val brickDark = Color.rgb(160, 84, 44)
    private val gold = Color.rgb(242, 193, 78)
    private val goldDark = Color.rgb(186, 132, 32)
    private val pipeGreen = Color.rgb(58, 170, 84)
    private val pipeDark = Color.rgb(38, 128, 62)

    fun draw(canvas: Canvas, world: GameWorld, w: Int, h: Int, controls: Controls) {
        if (w <= 0 || h <= 0 || world.level.pixelHeight <= 0f) return

        val scale = h / world.level.pixelHeight

        drawBackground(canvas, world, w, h, scale)

        canvas.save()
        canvas.translate(world.camera.shakeX * scale, world.camera.shakeY * scale)
        canvas.scale(scale, scale)
        canvas.translate(-world.camera.x, 0f)

        drawTiles(canvas, world)
        drawFlag(canvas, world)
        drawItems(canvas, world)
        drawEnemies(canvas, world)
        drawPlayer(canvas, world)
        drawParticles(canvas, world)
        drawPopups(canvas, world)

        canvas.restore()

        drawHud(canvas, world, w, h, controls)
        drawControls(canvas, controls, world)
        drawOverlay(canvas, world, w, h)
    }

    // ------------------------------------------------------------------ 背景

    private fun drawBackground(canvas: Canvas, world: GameWorld, w: Int, h: Int, scale: Float) {
        if (skyShader == null || skyW != w || skyH != h) {
            skyShader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                skyTop, skyBottom, Shader.TileMode.CLAMP
            )
            skyW = w
            skyH = h
        }
        paint.shader = skyShader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // 太阳
        paint.color = Color.argb(60, 255, 246, 190)
        canvas.drawCircle(w * 0.83f, h * 0.17f, h * 0.14f, paint)
        paint.color = Color.argb(120, 255, 250, 214)
        canvas.drawCircle(w * 0.83f, h * 0.17f, h * 0.085f, paint)

        drawClouds(canvas, world, w, h, scale)
        drawHills(canvas, world, w, h, scale)
    }

    private fun drawClouds(canvas: Canvas, world: GameWorld, w: Int, h: Int, scale: Float) {
        paint.color = Color.argb(205, 255, 255, 255)
        var i = 0
        while (i < 12) {
            val drift = world.elapsed * 5f
            val x = (i * 430f - world.camera.x * 0.16f + drift) * scale
            val y = h * (0.08f + 0.05f * ((i * 37) % 5))
            val r = 24f * scale
            if (x > -240f * scale && x < w + 240f * scale) {
                canvas.drawCircle(x, y, r, paint)
                canvas.drawCircle(x + r * 0.95f, y + r * 0.16f, r * 0.78f, paint)
                canvas.drawCircle(x - r * 0.95f, y + r * 0.2f, r * 0.66f, paint)
                rect.set(x - r * 1.5f, y, x + r * 1.7f, y + r * 0.85f)
                canvas.drawRoundRect(rect, r * 0.4f, r * 0.4f, paint)
            }
            i++
        }
    }

    private fun drawHills(canvas: Canvas, world: GameWorld, w: Int, h: Int, scale: Float) {
        paint.color = Color.rgb(150, 208, 172)
        var i = 0
        while (i < 10) {
            val x = (i * 520f - world.camera.x * 0.3f) * scale
            val baseY = h * 0.95f
            val rw = 300f * scale
            val rh = 150f * scale
            if (x > -rw * 2f && x < w + rw * 2f) {
                path.reset()
                path.moveTo(x - rw, baseY)
                path.quadTo(x, baseY - rh * 1.8f, x + rw, baseY)
                path.close()
                canvas.drawPath(path, paint)
            }
            i++
        }

        paint.color = Color.rgb(116, 190, 140)
        i = 0
        while (i < 10) {
            val x = (i * 460f + 240f - world.camera.x * 0.46f) * scale
            val baseY = h * 0.99f
            val rw = 220f * scale
            val rh = 100f * scale
            if (x > -rw * 2f && x < w + rw * 2f) {
                path.reset()
                path.moveTo(x - rw, baseY)
                path.quadTo(x, baseY - rh * 1.9f, x + rw, baseY)
                path.close()
                canvas.drawPath(path, paint)
            }
            i++
        }
    }

    // ------------------------------------------------------------------ 图块

    private fun drawTiles(canvas: Canvas, world: GameWorld) {
        val level = world.level
        val camX = world.camera.x
        val c0 = max(0, floor(camX / TILE).toInt() - 1)
        val c1 = min(level.cols - 1, floor((camX + world.viewWorldWidth) / TILE).toInt() + 1)

        for (col in c0..c1) {
            for (row in 0 until level.rows) {
                val ch = level.at(col, row)
                if (ch == '.') continue
                val x = col * TILE
                val y = row * TILE
                when (ch) {
                    '#' -> drawGround(canvas, level, col, row, x, y)
                    'B' -> drawBrick(canvas, x, y)
                    '?' -> drawQuestion(canvas, x, y, world.elapsed, col, true)
                    'M' -> drawQuestion(canvas, x, y, world.elapsed, col, false)
                    'X' -> drawUsedBlock(canvas, x, y)
                    'P' -> drawPipe(canvas, level, col, row, x, y)
                    'C' -> drawCoinTile(canvas, x, y, world.elapsed, col)
                }
            }
        }
    }

    private fun drawGround(canvas: Canvas, level: Level, col: Int, row: Int, x: Float, y: Float) {
        paint.color = dirt
        canvas.drawRect(x, y, x + TILE, y + TILE, paint)

        // 土块斑点（用坐标做伪随机，画面上不会闪）
        paint.color = dirtDark
        val seed = (col * 73856093) xor (row * 19349663)
        for (i in 0 until 3) {
            val rx = (abs(seed shr (i * 5)) % 20).toFloat() + 4f
            val ry = (abs(seed shr (i * 7)) % 20).toFloat() + 5f
            canvas.drawCircle(x + rx, y + ry, 2.2f, paint)
        }

        val above = level.at(col, row - 1)
        val covered = above == '#' || above == 'B' || above == 'P' ||
            above == '?' || above == 'M' || above == 'X'
        if (!covered) {
            paint.color = grassDark
            canvas.drawRect(x, y, x + TILE, y + 11f, paint)
            paint.color = grass
            canvas.drawRect(x, y, x + TILE, y + 7f, paint)
            paint.color = Color.rgb(140, 220, 132)
            canvas.drawRect(x, y, x + TILE, y + 2f, paint)
        }
    }

    private fun drawBrick(canvas: Canvas, x: Float, y: Float) {
        paint.color = brick
        canvas.drawRect(x, y, x + TILE, y + TILE, paint)
        paint.color = brickDark
        canvas.drawRect(x, y + TILE - 4f, x + TILE, y + TILE, paint)
        paint.color = Color.rgb(232, 152, 104)
        canvas.drawRect(x, y, x + TILE, y + 3f, paint)

        paint.color = brickDark
        paint.strokeWidth = 2.4f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x, y + TILE * 0.5f, x + TILE, y + TILE * 0.5f, paint)
        canvas.drawLine(x + TILE * 0.5f, y, x + TILE * 0.5f, y + TILE * 0.5f, paint)
        canvas.drawLine(x + TILE * 0.25f, y + TILE * 0.5f, x + TILE * 0.25f, y + TILE, paint)
        canvas.drawLine(x + TILE * 0.75f, y + TILE * 0.5f, x + TILE * 0.75f, y + TILE, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawQuestion(canvas: Canvas, x: Float, y: Float, time: Float, col: Int, withMark: Boolean) {
        val bob = sin(time * 3.2f + col * 0.9f) * 1.2f
        paint.color = goldDark
        rect.set(x + 1f, y + 2f + bob, x + TILE - 1f, y + TILE + bob)
        canvas.drawRoundRect(rect, 4f, 4f, paint)
        paint.color = gold
        rect.set(x + 2f, y + 3f + bob, x + TILE - 2f, y + TILE - 2f + bob)
        canvas.drawRoundRect(rect, 3.5f, 3.5f, paint)

        // 四角铆钉
        paint.color = Color.argb(150, 255, 240, 190)
        for (i in 0 until 4) {
            val rx = if (i % 2 == 0) x + 5f else x + TILE - 5f
            val ry = if (i < 2) y + 6f + bob else y + TILE - 6f + bob
            canvas.drawCircle(rx, ry, 1.8f, paint)
        }

        if (withMark) {
            textPaint.color = Color.rgb(120, 78, 12)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 19f
            canvas.drawText("?", x + TILE * 0.5f, y + TILE * 0.72f + bob, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawUsedBlock(canvas: Canvas, x: Float, y: Float) {
        paint.color = Color.rgb(150, 104, 66)
        rect.set(x, y, x + TILE, y + TILE)
        canvas.drawRoundRect(rect, 4f, 4f, paint)
        paint.color = Color.rgb(120, 80, 50)
        rect.set(x + 3f, y + 3f, x + TILE - 3f, y + TILE - 3f)
        canvas.drawRoundRect(rect, 3f, 3f, paint)
    }

    private fun drawPipe(canvas: Canvas, level: Level, col: Int, row: Int, x: Float, y: Float) {
        paint.color = pipeGreen
        canvas.drawRect(x, y, x + TILE, y + TILE, paint)
        paint.color = pipeDark
        canvas.drawRect(x + TILE - 7f, y, x + TILE, y + TILE, paint)
        paint.color = Color.rgb(96, 208, 120)
        canvas.drawRect(x + 4f, y, x + 12f, y + TILE, paint)

        val above = level.at(col, row - 1)
        if (above != 'P') {
            // 管口
            paint.color = pipeDark
            canvas.drawRect(x - 2f, y, x + TILE + 2f, y + 12f, paint)
            paint.color = pipeGreen
            canvas.drawRect(x - 1f, y + 1.5f, x + TILE + 1f, y + 9f, paint)
            paint.color = Color.rgb(110, 214, 132)
            canvas.drawRect(x + 3f, y + 2f, x + 11f, y + 9f, paint)
        }
    }

    private fun drawCoinTile(canvas: Canvas, x: Float, y: Float, time: Float, col: Int) {
        val phase = time * 5f + col * 0.8f
        val squeeze = 0.25f + 0.75f * abs(cos(phase))
        val cx = x + TILE * 0.5f
        val cy = y + TILE * 0.5f
        val rx = TILE * 0.28f * squeeze
        val ry = TILE * 0.32f

        paint.color = Color.argb(70, 255, 220, 90)
        rect.set(cx - rx * 1.6f, cy - ry * 1.4f, cx + rx * 1.6f, cy + ry * 1.4f)
        canvas.drawOval(rect, paint)

        paint.color = goldDark
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        canvas.drawOval(rect, paint)
        paint.color = gold
        rect.set(cx - rx * 0.72f, cy - ry * 0.78f, cx + rx * 0.72f, cy + ry * 0.78f)
        canvas.drawOval(rect, paint)
        paint.color = Color.argb(180, 255, 250, 210)
        rect.set(cx - rx * 0.3f, cy - ry * 0.45f, cx + rx * 0.1f, cy + ry * 0.3f)
        canvas.drawOval(rect, paint)
    }

    // ------------------------------------------------------------------ 终点旗

    private fun drawFlag(canvas: Canvas, world: GameWorld) {
        val x = world.level.flagCol * TILE + TILE * 0.5f
        val baseY = Levels.FLOOR_ROW * TILE
        val topY = baseY - 9f * TILE

        paint.color = Color.rgb(226, 230, 236)
        canvas.drawRect(x - 3f, topY, x + 3f, baseY, paint)
        paint.color = Color.rgb(168, 176, 188)
        canvas.drawRect(x + 1f, topY, x + 3f, baseY, paint)

        paint.color = Color.rgb(255, 208, 64)
        canvas.drawCircle(x, topY - 5f, 6f, paint)

        val cleared = world.state == GameState.LEVEL_CLEAR
        val flagTop = if (cleared) baseY - 3.2f * TILE else topY + 12f
        val wave = sin(world.elapsed * 4.5f) * 4f

        path.reset()
        path.moveTo(x - 2f, flagTop)
        path.lineTo(x - 44f + wave, flagTop + 13f)
        path.lineTo(x - 2f, flagTop + 28f)
        path.close()
        paint.color = Color.rgb(232, 76, 92)
        canvas.drawPath(path, paint)

        paint.color = Color.rgb(255, 140, 150)
        path.reset()
        path.moveTo(x - 2f, flagTop)
        path.lineTo(x - 30f + wave, flagTop + 10f)
        path.lineTo(x - 2f, flagTop + 14f)
        path.close()
        canvas.drawPath(path, paint)
    }

    // ------------------------------------------------------------------ 实体

    private fun drawItems(canvas: Canvas, world: GameWorld) {
        for (item in world.items) {
            val bob = sin(world.elapsed * 5f) * 1.5f
            val cx = item.cx
            val cy = item.cy + bob
            val r = item.w * 0.5f

            paint.color = Color.argb(60, 120, 255, 160)
            canvas.drawCircle(cx, cy, r * 1.5f, paint)

            paint.color = Color.rgb(86, 206, 128)
            canvas.drawCircle(cx, cy, r, paint)
            paint.color = Color.rgb(150, 238, 176)
            canvas.drawCircle(cx - r * 0.25f, cy - r * 0.3f, r * 0.55f, paint)

            paint.color = Color.rgb(64, 160, 96)
            rect.set(cx - r * 0.18f, cy - r * 1.5f, cx + r * 0.18f, cy - r * 0.85f)
            canvas.drawRect(rect, paint)
            paint.color = Color.rgb(96, 200, 110)
            rect.set(cx - r * 1.1f, cy - r * 1.45f, cx + r * 0.05f, cy - r * 0.95f)
            canvas.drawOval(rect, paint)

            paint.color = Color.WHITE
            canvas.drawCircle(cx + r * 0.3f, cy - r * 0.1f, r * 0.16f, paint)
            paint.color = Color.rgb(40, 60, 50)
            canvas.drawCircle(cx + r * 0.34f, cy - r * 0.1f, r * 0.08f, paint)
        }
    }

    private fun drawEnemies(canvas: Canvas, world: GameWorld) {
        for (e in world.enemies) {
            val squashT = if (e.squash > 0f) (1f - e.squash / 0.3f).coerceIn(0f, 1f) else 0f
            val bodyH = e.h * (1f - squashT * 0.75f)
            val topY = e.bottom - bodyH

            paint.color = Color.rgb(150, 88, 214)
            rect.set(e.x, topY, e.right, e.bottom)
            canvas.drawRoundRect(rect, e.w * 0.48f, e.w * 0.48f, paint)

            paint.color = Color.rgb(186, 136, 240)
            rect.set(e.x + 4f, topY + bodyH * 0.42f, e.right - 4f, e.bottom - 2f)
            canvas.drawRoundRect(rect, 9f, 9f, paint)

            // 小脚
            paint.color = Color.rgb(104, 56, 158)
            val step = sin(e.anim * 9f) * 2.5f
            rect.set(e.x + 1f, e.bottom - 4f + step, e.x + 12f, e.bottom + step)
            canvas.drawRoundRect(rect, 3f, 3f, paint)
            rect.set(e.right - 12f, e.bottom - 4f - step, e.right - 1f, e.bottom - step)
            canvas.drawRoundRect(rect, 3f, 3f, paint)

            if (squashT < 0.5f) {
                val eyeY = topY + bodyH * 0.36f
                paint.color = Color.WHITE
                canvas.drawCircle(e.cx - 6.5f, eyeY, 4.6f, paint)
                canvas.drawCircle(e.cx + 6.5f, eyeY, 4.6f, paint)
                paint.color = Color.rgb(34, 30, 56)
                val px = e.dir * 1.6f
                canvas.drawCircle(e.cx - 6.5f + px, eyeY + 0.6f, 2.2f, paint)
                canvas.drawCircle(e.cx + 6.5f + px, eyeY + 0.6f, 2.2f, paint)
                // 眉毛（凶一点）
                paint.color = Color.rgb(92, 48, 140)
                paint.strokeWidth = 2f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(e.cx - 10f, eyeY - 6f, e.cx - 3f, eyeY - 3.5f, paint)
                canvas.drawLine(e.cx + 10f, eyeY - 6f, e.cx + 3f, eyeY - 3.5f, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    private fun drawPlayer(canvas: Canvas, world: GameWorld) {
        val p = world.player
        val dying = world.state == GameState.DYING
        if (!dying && p.invuln > 0f && ((world.elapsed * 16f).toInt() % 2 == 0)) return

        canvas.save()
        canvas.translate(p.cx, p.bottom)

        if (dying) canvas.rotate(p.spin * 57.29578f)

        if (p.squash != 0f) {
            val sy = (1f - p.squash * 0.5f).coerceIn(0.62f, 1.28f)
            val sx = (1f + p.squash * 0.5f).coerceIn(0.72f, 1.28f)
            canvas.scale(sx, sy)
        }
        canvas.scale(if (p.facing >= 0) 1f else -1f, 1f)

        val w = p.w
        val h = p.h
        val footH = h * 0.16f
        val swing = sin(p.runAnim * 6f) * h * 0.05f

        // 影子
        paint.color = Color.argb(40, 0, 0, 0)
        rect.set(-w * 0.55f, -3f, w * 0.55f, 3f)
        canvas.drawOval(rect, paint)

        // 鞋
        paint.color = Color.rgb(28, 46, 84)
        rect.set(-w * 0.48f, -footH + swing, -w * 0.02f, 1f + swing)
        canvas.drawRoundRect(rect, 3.5f, 3.5f, paint)
        rect.set(w * 0.02f, -footH - swing, w * 0.48f, 1f - swing)
        canvas.drawRoundRect(rect, 3.5f, 3.5f, paint)

        // 身体
        paint.color = Color.rgb(58, 134, 240)
        rect.set(-w * 0.5f, -h * 0.8f, w * 0.5f, -footH * 0.5f)
        canvas.drawRoundRect(rect, w * 0.3f, w * 0.3f, paint)

        // 围巾
        paint.color = Color.rgb(249, 127, 79)
        path.reset()
        path.moveTo(-w * 0.12f, -h * 0.74f)
        path.lineTo(-w * 0.98f, -h * 0.66f + sin(world.elapsed * 9f) * 3f)
        path.lineTo(-w * 0.9f, -h * 0.5f)
        path.lineTo(-w * 0.1f, -h * 0.58f)
        path.close()
        canvas.drawPath(path, paint)

        // 头
        val headR = w * 0.42f
        val headY = -h * 0.84f
        paint.color = Color.rgb(255, 214, 170)
        canvas.drawCircle(0f, headY, headR, paint)

        // 头盔
        paint.color = Color.rgb(58, 134, 240)
        rect.set(-headR * 1.02f, headY - headR * 1.05f, headR * 1.02f, headY - headR * 0.2f)
        canvas.drawRoundRect(rect, headR * 0.85f, headR * 0.85f, paint)
        paint.color = Color.rgb(96, 176, 255)
        rect.set(-headR * 0.9f, headY - headR * 0.95f, headR * 0.3f, headY - headR * 0.55f)
        canvas.drawRoundRect(rect, headR * 0.3f, headR * 0.3f, paint)

        // 护目镜
        paint.color = Color.WHITE
        canvas.drawCircle(headR * 0.34f, headY + headR * 0.08f, headR * 0.42f, paint)
        paint.color = Color.rgb(28, 40, 64)
        canvas.drawCircle(headR * 0.48f, headY + headR * 0.08f, headR * 0.19f, paint)

        // 嘴
        paint.color = Color.rgb(206, 126, 116)
        rect.set(headR * 0.12f, headY + headR * 0.55f, headR * 0.62f, headY + headR * 0.74f)
        canvas.drawRoundRect(rect, 2f, 2f, paint)

        canvas.restore()
    }

    private fun drawParticles(canvas: Canvas, world: GameWorld) {
        for (p in world.particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            paint.color = p.color
            paint.alpha = (a * 255f).toInt()
            canvas.drawCircle(p.px, p.py, p.size, paint)
        }
        paint.alpha = 255
    }

    private fun drawPopups(canvas: Canvas, world: GameWorld) {
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 13f
        for (p in world.popups) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            textPaint.color = Color.argb((a * 255).toInt(), 255, 252, 235)
            canvas.drawText(p.text, p.px, p.py, textPaint)
        }
    }

    // ------------------------------------------------------------------ HUD

    private fun drawHud(canvas: Canvas, world: GameWorld, w: Int, h: Int, controls: Controls) {
        val pad = h * 0.035f
        val size = h * 0.046f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = size
        textPaint.color = Color.WHITE
        textPaint.setShadowLayer(size * 0.12f, 0f, size * 0.08f, Color.argb(160, 0, 0, 0))

        canvas.drawText("分数 ${padScore(world.score)}", pad, pad + size, textPaint)
        canvas.drawText("金币 x${world.coinCount}", pad, pad + size * 2.1f, textPaint)
        canvas.drawText("生命 x${world.lives}", pad, pad + size * 3.2f, textPaint)
        textPaint.clearShadowLayer()

        // 右上角重开按钮
        val r = controls.restart
        val cx = r.centerX()
        val cy = r.centerY()
        val rad = r.width() * 0.5f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(90, 8, 16, 30)
        canvas.drawCircle(cx, cy, rad, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = rad * 0.17f
        paint.color = Color.argb(230, 255, 255, 255)
        rect.set(cx - rad * 0.55f, cy - rad * 0.55f, cx + rad * 0.55f, cy + rad * 0.55f)
        canvas.drawArc(rect, -70f, 285f, false, paint)
        paint.style = Paint.Style.FILL
        path.reset()
        path.moveTo(cx + rad * 0.62f, cy - rad * 0.05f)
        path.lineTo(cx + rad * 0.16f, cy - rad * 0.34f)
        path.lineTo(cx + rad * 0.16f, cy + rad * 0.24f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun padScore(score: Int): String {
        val s = score.toString()
        return if (s.length >= 6) s else "0".repeat(6 - s.length) + s
    }

    // ------------------------------------------------------------------ 虚拟按键

    private fun drawControls(canvas: Canvas, controls: Controls, world: GameWorld) {
        if (world.state == GameState.GAME_OVER) return

        padButton(canvas, controls.left)
        padButton(canvas, controls.right)
        padButton(canvas, controls.jump)

        // ← →
        drawArrow(canvas, controls.left, true)
        drawArrow(canvas, controls.right, false)

        // 跳跃键文字
        val ir = controls.jump
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = ir.height() * 0.42f
        textPaint.color = Color.argb(235, 255, 255, 255)
        canvas.drawText("跳", ir.centerX(), ir.centerY() + textPaint.textSize * 0.36f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun padButton(canvas: Canvas, r: RectF) {
        val cx = r.centerX()
        val cy = r.centerY()
        val rad = r.width() * 0.5f

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(52, 255, 255, 255)
        canvas.drawCircle(cx, cy, rad, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = rad * 0.07f
        paint.color = Color.argb(150, 255, 255, 255)
        canvas.drawCircle(cx, cy, rad, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawArrow(canvas: Canvas, r: RectF, pointingLeft: Boolean) {
        val cx = r.centerX()
        val cy = r.centerY()
        val s = r.width() * 0.26f
        val dir = if (pointingLeft) -1f else 1f

        path.reset()
        path.moveTo(cx + dir * s, cy - s)
        path.lineTo(cx - dir * s * 0.6f, cy)
        path.lineTo(cx + dir * s, cy + s)
        path.close()
        paint.color = Color.argb(220, 255, 255, 255)
        canvas.drawPath(path, paint)
    }

    // ------------------------------------------------------------------ 遮罩

    private fun drawOverlay(canvas: Canvas, world: GameWorld, w: Int, h: Int) {
        when (world.state) {
            GameState.LEVEL_CLEAR -> drawCenterPanel(
                canvas, world, w, h,
                "关卡通过！",
                "得分 ${world.score}   金币 ${world.coinCount}",
                "点击屏幕再玩一次"
            )
            GameState.GAME_OVER -> drawCenterPanel(
                canvas, world, w, h,
                "游戏结束",
                "最终得分 ${world.score}",
                "点击屏幕重新开始"
            )
            else -> Unit
        }
    }

    private fun drawCenterPanel(
        canvas: Canvas,
        world: GameWorld,
        w: Int,
        h: Int,
        title: String,
        subtitle: String,
        hint: String
    ) {
        canvas.drawColor(Color.argb(165, 8, 14, 28))

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.textSize = h * 0.11f
        textPaint.setShadowLayer(h * 0.012f, 0f, h * 0.008f, Color.argb(180, 0, 0, 0))
        canvas.drawText(title, w * 0.5f, h * 0.42f, textPaint)

        textPaint.textSize = h * 0.05f
        canvas.drawText(subtitle, w * 0.5f, h * 0.54f, textPaint)

        val blink = 0.55f + 0.45f * sin(world.elapsed * 3.4f)
        textPaint.color = Color.argb((blink * 255).toInt(), 255, 236, 168)
        textPaint.textSize = h * 0.045f
        canvas.drawText(hint, w * 0.5f, h * 0.66f, textPaint)

        textPaint.clearShadowLayer()
        textPaint.textAlign = Paint.Align.LEFT
    }
}
