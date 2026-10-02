package com.inmc.menu

import com.inmc.menu.pack.Glyphs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 리소스팩 화면의 글자 수치 — 틀리면 배경이 창과 어긋나고 오류는 없다. */
class GlyphsTest {

    @Test
    fun `배경 글자는 창 왼쪽 끝에서 시작해 제목 자리로 돌아온다`() {
        // 제목 시작(x 8)에서: -8 → 창 왼쪽 끝(0), 그림(너비 176 + 글자 사이 1) → 169, 돌아오기 → 0 = 다시 제목 자리.
        var cursor = 0
        cursor += Glyphs.ADVANCES.getValue(Glyphs.SHIFT_TO_EDGE)
        assertEquals(-Glyphs.TITLE_X, cursor, "그림이 창 왼쪽 끝(제목 x 8 의 8칸 왼쪽)에서")
        cursor += Glyphs.WIDTH + 1
        cursor += Glyphs.ADVANCES.getValue(Glyphs.RETURN_TO_TITLE)
        assertEquals(0, cursor, "제목 글자가 원래 자리에")
    }

    @Test
    fun `배경의 위끝이 창 위끝 — 제목 y 6 + 7 - ascent = 0`() {
        assertEquals(0, 6 + 7 - Glyphs.ASCENT)
    }

    @Test
    fun `칸 좌표는 바닐라 상자 창과 같다`() {
        // ChestMenu: Slot(8 + 18·열, 18 + 18·줄) — 아이템이 그려지는 곳. 칸 틀은 그 1 바깥.
        assertEquals(8, Glyphs.itemX(0))
        assertEquals(8 + 18 * 8, Glyphs.itemX(8))
        assertEquals(18, Glyphs.itemY(0))
        assertEquals(7, Glyphs.cellX(0))
        assertEquals(17, Glyphs.cellY(0))
        assertTrue(Glyphs.cellY(0) > 16, "첫 줄 칸이 제목 띠(0~16) 아래")
        assertTrue(Glyphs.cellX(8) + 18 <= Glyphs.WIDTH - 2, "마지막 칸이 오른쪽 테두리 안")
        for (rows in 1..6) {
            // 바닐라 상자 부분은 17 + 18·줄 까지, "인벤토리" 글자는 20 + 18·줄 — 배경은 그 사이에서 끝난다.
            assertTrue(Glyphs.cellY(rows - 1) + 18 <= 17 + 18 * rows, "$rows 줄의 마지막 칸이 상자 부분 안")
            assertTrue(Glyphs.height(rows) <= 20 + 18 * rows, "$rows 줄 — 인벤토리 글자를 덮지 않는다")
            assertTrue(Glyphs.height(rows) <= 256, "비트맵 글자는 256 을 넘을 수 없다")
            assertTrue(Glyphs.height(rows) >= Glyphs.ASCENT, "ascent 는 높이를 넘을 수 없다")
        }
    }

    @Test
    fun `배경 글자는 사용자 영역에서 겹치지 않는다`() {
        val chars = (0 until 64).map(Glyphs::background)
        assertEquals(64, chars.toSet().size)
        assertTrue(chars.all { it.code in 0xE000..0xF8FF })
        assertTrue(Glyphs.ADVANCES.keys.none { it in chars }, "공백 글자와 겹치지 않음")
    }
}
