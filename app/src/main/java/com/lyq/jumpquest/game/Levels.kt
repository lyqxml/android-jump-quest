package com.lyq.jumpquest.game

/**
 * 第一关：从起点跑到终点旗杆。
 * 关卡宽度 224 格，高度 15 格，地面在第 13 行（0 起）。
 */
object Levels {
    const val COLS = 224
    const val ROWS = 15
    const val FLOOR_ROW = 13

    fun level1(): Level {
        val b = LevelBuilder(COLS, ROWS, FLOOR_ROW)

        // ---------- 地面（中间留三个坑） ----------
        b.ground(0, 62)
        b.ground(66, 100)
        b.ground(104, 148)
        b.ground(152, COLS - 1)

        // ---------- 区域 A：教学段 ----------
        b.enemy(14)
        b.set(18, 9, '?')
        b.set(19, 9, 'B')
        b.set(20, 9, '?')
        b.coin(18, 7)
        b.coin(19, 7)
        b.coin(20, 7)
        b.enemy(26)
        b.pipe(31, 2)

        // ---------- 区域 B：管道 + 浮空砖块 ----------
        b.pipe(38, 3)
        b.enemy(44)
        b.fill(45, 48, 10, 10, 'B')
        b.set(47, 10, 'M')
        b.coin(45, 9)
        b.coin(46, 9)
        b.coin(47, 9)
        b.coin(48, 9)
        b.enemy(52)
        b.coin(63, 10)
        b.coin(64, 9)
        b.coin(65, 10)

        // ---------- 区域 C：连续坑 + 台阶 ----------
        b.enemy(70)
        b.fill(72, 75, 10, 10, 'B')
        b.coin(72, 9)
        b.coin(73, 9)
        b.coin(74, 9)
        b.coin(75, 9)
        b.enemy(78)
        b.set(84, 9, '?')
        b.set(85, 9, 'B')
        b.set(86, 9, 'B')
        b.enemy(88)
        b.stairs(92, 4)
        b.coin(101, 10)
        b.coin(102, 9)
        b.coin(103, 10)

        // ---------- 区域 D：高低管道 ----------
        b.pipe(108, 2)
        b.enemy(112)
        b.pipe(116, 3)
        b.enemy(122)
        b.enemy(128)
        b.fill(135, 139, 8, 8, 'B')
        b.set(137, 8, 'M')
        b.coin(135, 7)
        b.coin(136, 7)
        b.coin(137, 7)
        b.coin(138, 7)
        b.coin(139, 7)
        b.enemy(142)
        b.coin(149, 10)
        b.coin(150, 9)
        b.coin(151, 10)

        // ---------- 区域 E：金字塔台阶 + 终点 ----------
        b.enemy(157)
        b.enemy(164)
        b.set(166, 9, 'B')
        b.set(167, 9, '?')
        b.set(168, 9, 'B')
        b.coin(166, 8)
        b.coin(167, 8)
        b.coin(168, 8)
        b.enemy(172)
        b.stairs(180, 5)
        b.stairsDown(185, 5)
        b.enemy(196)
        b.coin(199, 10)
        b.coin(200, 9)
        b.coin(201, 10)
        b.set(210, 9, '?')

        return b.build(spawnCol = 3, flagCol = 205)
    }
}

/** 用代码拼关卡，比手写 224 列的 ASCII 更不容易错。 */
class LevelBuilder(
    private val cols: Int,
    private val rows: Int,
    private val floorRow: Int
) {
    private val grid = CharArray(cols * rows) { '.' }
    private val enemies = ArrayList<TilePos>()

    fun set(col: Int, row: Int, ch: Char) {
        if (col in 0 until cols && row in 0 until rows) {
            grid[row * cols + col] = ch
        }
    }

    fun fill(c0: Int, c1: Int, r0: Int, r1: Int, ch: Char) {
        for (c in c0..c1) {
            for (r in r0..r1) set(c, r, ch)
        }
    }

    fun ground(c0: Int, c1: Int) = fill(c0, c1, floorRow, rows - 1, '#')

    fun coin(col: Int, row: Int) = set(col, row, 'C')

    fun enemy(col: Int) {
        enemies.add(TilePos(col, floorRow - 1))
    }

    fun pipe(col: Int, heightTiles: Int) =
        fill(col, col + 1, floorRow - heightTiles, floorRow - 1, 'P')

    /** 从左到右递增的台阶，h = 1..steps */
    fun stairs(col: Int, steps: Int) {
        for (i in 0 until steps) {
            fill(col + i, col + i, floorRow - (i + 1), floorRow - 1, '#')
        }
    }

    /** 从左到右递减的台阶，h = steps..1 */
    fun stairsDown(col: Int, steps: Int) {
        for (i in 0 until steps) {
            fill(col + i, col + i, floorRow - (steps - i), floorRow - 1, '#')
        }
    }

    fun build(spawnCol: Int, flagCol: Int): Level =
        Level(cols, rows, grid, enemies, TilePos(spawnCol, floorRow - 1), flagCol)
}
