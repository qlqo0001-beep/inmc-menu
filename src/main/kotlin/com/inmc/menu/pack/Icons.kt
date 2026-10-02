package com.inmc.menu.pack

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * 기본 버튼의 16×16 아이콘 — 도형을 **매끄럽게 하지 않고**(픽셀 그대로) 찍은 뒤 [finish] 가 1픽셀 어두운 테두리를 두른다.
 * 이름은 기본 메뉴의 그림 이름(`art`)과 같다. 관리자가 넣은 그림(`art/` 의 png)이 같은 이름이면 그것이 이긴다.
 */
object Icons {

    const val SIZE = 16

    private val OUTLINE = Color(0x14, 0x15, 0x1f).rgb

    private val DRAW: Map<String, (Graphics2D) -> Unit> = mapOf(
        "settings" to { g -> // 톱니 — 1/4 을 그려 네 방향으로 접는다(이빨 8개가 똑같게). 테섭 2026-10-02 "찌그러졌다"
            val grid = mirrored(GEAR_QUARTER)
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                if (grid[y][x] == '.') continue
                val open = { dx: Int, dy: Int -> grid.getOrNull(y + dy)?.getOrNull(x + dx)?.let { it == '.' } ?: true }
                g.color = when {
                    grid[y][x] == 'd' -> Color(0x6f7787)
                    open(0, -1) || open(-1, 0) -> Color(0xeef1f5)
                    open(0, 1) || open(1, 0) -> Color(0x9aa2b0)
                    else -> Color(0xc9ced8)
                }
                g.fillRect(x, y, 1, 1)
            }
        },
        "title" to { g -> // 이름표
            g.color = Color(0xe8d9b0); g.fillPolygon(intArrayOf(2, 6, 14, 14, 6), intArrayOf(8, 4, 4, 12, 12), 5)
            g.color = Color(0xc9b27a); g.fillRect(6, 10, 8, 2)
            g.color = Color(0x6b4f2a); g.fillRect(5, 7, 2, 2)
            g.color = Color(0xd6453d); g.fillRect(9, 7, 4, 1)
        },
        "achievements" to { g -> // 트로피
            g.color = Color(0xf2c14e); g.fillRect(4, 2, 8, 6); g.fillOval(4, 5, 8, 6); g.fillRect(2, 3, 2, 4); g.fillRect(12, 3, 2, 4)
            g.fillRect(7, 10, 2, 3); g.fillRect(5, 12, 6, 2)
            g.color = Color(0xb4812a); g.fillRect(5, 13, 6, 1); g.fillRect(9, 3, 2, 5)
            g.color = Color(0xfff2b0); g.fillRect(5, 3, 1, 4)
        },
        "money" to { g -> // 동전
            g.color = Color(0xf2c14e); g.fillOval(2, 2, 12, 12)
            g.color = Color(0xb4812a); g.drawOval(4, 4, 7, 7)
            g.fillRect(7, 5, 2, 6)
            g.color = Color(0xfff2b0); g.fillRect(4, 4, 2, 2)
        },
        "equipment" to { g -> // 흉갑
            g.color = Color(0x6fd6e8); g.fillPolygon(intArrayOf(2, 5, 11, 14, 13, 11, 11, 5, 5, 3), intArrayOf(4, 2, 2, 4, 8, 7, 14, 14, 7, 8), 10)
            g.color = Color(0x3f8fa8); g.fillRect(7, 3, 2, 11)
            g.color = Color(0xc8f4ff); g.fillRect(5, 4, 2, 2)
        },
        "sell" to { g -> // 깔때기 + 동전
            g.color = Color(0x8a8f99); g.fillPolygon(intArrayOf(1, 15, 10, 10, 6, 6), intArrayOf(3, 3, 9, 13, 13, 9), 6)
            g.color = Color(0x5b6070); g.fillRect(6, 9, 4, 2)
            g.color = Color(0xf2c14e); g.fillOval(10, 9, 5, 5)
            g.color = Color(0xb4812a); g.fillRect(12, 10, 1, 3)
        },
        "spawn" to { g -> // 집
            g.color = Color(0xd6453d); g.fillPolygon(intArrayOf(1, 8, 15), intArrayOf(8, 1, 8), 3)
            g.color = Color(0xe9dcc0); g.fillRect(3, 8, 10, 7)
            g.color = Color(0x8a5a34); g.fillRect(7, 10, 3, 5)
            g.color = Color(0x6fb7e8); g.fillRect(4, 9, 2, 2); g.fillRect(11, 9, 1, 2)
        },
        "rtp" to { g -> // 엔더 진주 + 반짝임
            g.color = Color(0x1f6f6a); g.fillOval(2, 3, 11, 11)
            g.color = Color(0x2fb3a3); g.fillOval(4, 5, 7, 7)
            g.color = Color(0x0f3a3a); g.fillRect(6, 7, 3, 3)
            g.color = Color(0xc8fff5); g.fillRect(12, 1, 1, 3); g.fillRect(11, 2, 3, 1); g.fillRect(4, 5, 1, 1)
        },
        "shop" to { g -> // 에메랄드
            g.color = Color(0x2ecc71); g.fillPolygon(intArrayOf(8, 14, 14, 8, 2, 2), intArrayOf(1, 5, 11, 15, 11, 5), 6)
            g.color = Color(0x1b8f4c); g.fillPolygon(intArrayOf(8, 14, 14, 8), intArrayOf(8, 5, 11, 15), 4)
            g.color = Color(0xb8ffd4); g.fillRect(5, 5, 2, 3)
        },
        "auction" to { g -> // 경매 망치
            g.color = Color(0x8a5a34); g.fillPolygon(intArrayOf(5, 7, 14, 12), intArrayOf(9, 7, 14, 15), 4)
            g.color = Color(0xc9ccd4); g.fillPolygon(intArrayOf(1, 6, 10, 5), intArrayOf(5, 1, 5, 9), 4)
            g.color = Color(0x8a8f99); g.fillPolygon(intArrayOf(5, 10, 9, 5), intArrayOf(9, 5, 8, 10), 4)
            g.color = Color(0x6b4f2a); g.fillRect(1, 13, 7, 2)
        },
        "backpack" to { g -> // 배낭
            g.color = Color(0xa0683c); g.fillRoundRect(3, 3, 10, 12, 4, 4)
            g.color = Color(0x7a4a26); g.fillRect(5, 1, 6, 3); g.fillRect(3, 9, 10, 1)
            g.color = Color(0xc98a52); g.fillRect(4, 4, 8, 4)
            g.color = Color(0xf2c14e); g.fillRect(7, 8, 2, 2)
        },
        "fishing" to { g -> // 물고기
            g.color = Color(0x4f8fd6); g.fillOval(2, 5, 10, 7)
            g.fillPolygon(intArrayOf(11, 15, 15), intArrayOf(8, 4, 13), 3)
            g.color = Color(0x2f62a8); g.fillRect(4, 10, 6, 1); g.fillRect(13, 6, 1, 5)
            g.color = Color(0xffffff); g.fillRect(4, 7, 2, 2)
            g.color = Color(0x10131c); g.fillRect(5, 7, 1, 1)
        },
        "contents" to { g -> // 상자
            g.color = Color(0xa0683c); g.fillRect(1, 4, 14, 10)
            g.color = Color(0x7a4a26); g.fillRect(1, 7, 14, 1); g.fillRect(1, 13, 14, 1)
            g.color = Color(0xc98a52); g.fillRect(2, 4, 12, 3)
            g.color = Color(0xc9ccd4); g.fillRect(7, 6, 2, 3)
        },
        "attendance" to { g -> // 달력
            g.color = Color(0xf4f4f4); g.fillRect(2, 3, 12, 11)
            g.color = Color(0xd6453d); g.fillRect(2, 3, 12, 3)
            g.color = Color(0x5b6070); g.fillRect(4, 1, 1, 3); g.fillRect(11, 1, 1, 3)
            g.color = Color(0x9aa3ad)
            for (y in listOf(7, 10)) for (x in listOf(4, 7, 10)) g.fillRect(x, y, 2, 2)
            g.color = Color(0x2ecc71); g.fillRect(10, 10, 2, 2)
        },
        "close" to { g -> // 엑스
            g.color = Color(0xe0504a)
            for (i in 0 until 10) { g.fillRect(3 + i, 3 + i, 2, 1); g.fillRect(12 - i, 3 + i, 2, 1) }
        },
        "back" to { g -> // 왼쪽 화살표
            g.color = Color(0xd9dde3); g.fillPolygon(intArrayOf(2, 8, 8, 14, 14, 8, 8), intArrayOf(8, 2, 5, 5, 11, 11, 14), 7)
        },
        "numbergame" to { g -> // 주사위
            g.color = Color(0xf4f4f4); g.fillRoundRect(2, 2, 12, 12, 4, 4)
            g.color = Color(0x2a2d43)
            for ((x, y) in listOf(4 to 4, 10 to 4, 7 to 7, 4 to 10, 10 to 10)) g.fillRect(x, y, 2, 2)
        },
        "drops" to { g -> // 떨어지는 보석
            g.color = Color(0xb06fe8); g.fillPolygon(intArrayOf(8, 13, 8, 3), intArrayOf(6, 10, 15, 10), 4)
            g.color = Color(0x7a3fb0); g.fillPolygon(intArrayOf(8, 13, 8), intArrayOf(10, 10, 15), 3)
            g.color = Color(0xd9dde3); g.fillRect(7, 1, 2, 3); g.fillRect(5, 3, 6, 1)
        },
        "craft" to { g -> // 제작대
            g.color = Color(0xa0683c); g.fillRect(2, 4, 12, 10)
            g.color = Color(0xc98a52); g.fillRect(2, 2, 12, 3)
            g.color = Color(0x6b4f2a); g.fillRect(2, 4, 12, 1); g.fillRect(5, 6, 1, 7); g.fillRect(10, 6, 1, 7)
            g.color = Color(0xc9ccd4); g.fillRect(7, 7, 2, 4)
        },
        "chestshop" to { g -> // 통
            g.color = Color(0x8a5a34); g.fillRoundRect(3, 1, 10, 14, 4, 2)
            g.color = Color(0x5b3a1f); g.fillRect(3, 4, 10, 1); g.fillRect(3, 11, 10, 1)
            g.color = Color(0xb07a4a); g.fillRect(5, 2, 2, 12)
        },
    )

    /**
     * 톱니의 왼쪽 위 1/4(8×8, 오른쪽 아래 모서리가 가운데). `#` 몸통 · `d` 안쪽 테 · `.` 빈칸(가운데 구멍 포함).
     * 대각선으로도 대칭이라 접으면 이빨 8개가 같은 모양이다.
     */
    private val GEAR_QUARTER = listOf(
        "......##",
        "......##",
        "..##..##",
        "..######",
        "...#####",
        "...##ddd",
        "#####dd.",
        "#####d..",
    )

    /** 1/4 을 좌우·상하로 접어 16×16 으로. */
    private fun mirrored(quarter: List<String>): List<String> {
        val top = quarter.map { it + it.reversed() }
        return top + top.reversed()
    }

    fun names(): Set<String> = DRAW.keys

    /** 기본 아이콘. 없는 이름이면 null. */
    fun draw(name: String): BufferedImage? {
        val painter = DRAW[name] ?: return null
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
        painter(g)
        g.dispose()
        return finish(image)
    }

    /** 1픽셀 어두운 테두리(투명한 곳 중 불투명한 곳 옆). */
    fun finish(image: BufferedImage): BufferedImage {
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val argb = image.getRGB(x, y)
            if (argb ushr 24 != 0) {
                out.setRGB(x, y, argb or (0xff shl 24))
                continue
            }
            val near = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).any { (nx, ny) ->
                nx in 0 until image.width && ny in 0 until image.height && image.getRGB(nx, ny) ushr 24 != 0
            }
            if (near) out.setRGB(x, y, OUTLINE)
        }
        return out
    }
}
