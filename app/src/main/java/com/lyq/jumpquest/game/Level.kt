package com.lyq.jumpquest.game

/**
 * 关卡：tile 网格 + 出生点/敌人出生点/终点。
 *
 * 图块字符：
 *  '.' 空   '#' 地面   'B' 砖块   '?' 问号块   'M' 道具块
 *  'X' 已用块   'P' 管道   'C' 金币
 */
class Level(
    val cols: Int,
    val rows: Int,
    private val grid: CharArray,
    val enemySpawns: List<TilePos>,
    val spawn: TilePos,
    val flagCol: Int
) {
    val pixelWidth: Float = cols * TILE
    val pixelHeight: Float = rows * TILE

    fun at(col: Int, row: Int): Char {
        if (col < 0 || col >= cols || row < 0 || row >= rows) return '.'
        return grid[row * cols + col]
    }

    fun set(col: Int, row: Int, ch: Char) {
        if (col in 0 until cols && row in 0 until rows) {
            grid[row * cols + col] = ch
        }
    }

    fun isSolid(col: Int, row: Int): Boolean {
        if (col < 0) return true          // 关卡左侧当墙，防止走出边界
        if (col >= cols || row < 0 || row >= rows) return false
        return when (grid[row * cols + col]) {
            '#', 'B', '?', 'M', 'X', 'P' -> true
            else -> false
        }
    }
}
