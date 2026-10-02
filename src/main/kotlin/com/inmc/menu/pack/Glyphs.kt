package com.inmc.menu.pack

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack

/**
 * 리소스팩 화면의 글자 계산 — 서버 없이 도는 순수 부분(`GlyphsTest`).
 *
 * 상자 창의 제목은 창 왼쪽 위에서 (x 8, y 6)에 그려진다. 배경 그림 글자를 창 왼쪽 위(0, 0)에 맞추려면:
 * - **가로**: 그림 앞에 -8 칸 공백([SHIFT_TO_EDGE]).
 * - **세로**: 비트맵 글자의 위끝 = 제목 y + 7 − `ascent` 이므로 `ascent` 13 이면 0([ASCENT]). 줄 수와 상관없다 — 창이 커져도 제목이 같이 움직인다.
 * - 그림 글자의 너비는 (불투명한 마지막 열 + 1) — 그래서 배경은 가로 [WIDTH] 전부를 칠한다. 그 뒤 [RETURN_TO_TITLE] 만큼 돌아와 제목 글자를 원래 자리에 쓴다.
 * - 그림 글자는 **흰색**이어야 한다 — 제목 색(어두운 회색)이 그림에 곱해진다.
 */
object Glyphs {

    /** 우리 글꼴 — `assets/inmc/font/menu.json`. */
    val FONT: Key = Key.key("inmc", "menu")

    const val WIDTH = 176
    const val ASCENT = 13
    const val TITLE_X = 8

    /** 공백 제공자의 글자들. */
    const val SHIFT_TO_EDGE = ''
    const val RETURN_TO_TITLE = ''

    /** 공백 제공자의 너비 — 글꼴 json 과 같은 값이어야 한다. */
    val ADVANCES: Map<Char, Int> = mapOf(
        SHIFT_TO_EDGE to -TITLE_X,
        RETURN_TO_TITLE to -(WIDTH + 1 - TITLE_X),
    )

    /** 배경 글자 — 사용자 영역 `` 부터 메뉴(화면)마다 하나. */
    fun background(index: Int): Char = (0xE000 + index).toChar()

    /**
     * 배경 그림의 높이 — 창의 상자 부분(바닐라 `17 + 18·줄`) + 3. 그 아래 바닐라 가방 부분의 "인벤토리" 글자(y `20 + 18·줄`)는 덮지 않는다 —
     * 그 글자는 어두운 회색이라 우리 어두운 판 위에서 안 보인다.
     */
    fun height(rows: Int): Int = 20 + rows * 18

    /** 아이템이 그려지는 왼쪽 위(16×16) — 바닐라 상자 창의 칸 좌표 그대로(x = 8 + 18·열, y = 18 + 18·줄). 마우스를 올린 칸의 흰 덮개도 여기. */
    fun itemX(col: Int): Int = 8 + 18 * col

    fun itemY(row: Int): Int = 18 + 18 * row

    /** 칸 틀(18×18)의 왼쪽 위 — 아이템보다 1 바깥(바닐라 칸 그림처럼 아이템 둘레 1픽셀이 틀). 버튼 판은 칸 틀 묶음을 덮는다. */
    fun cellX(col: Int): Int = itemX(col) - 1

    fun cellY(row: Int): Int = itemY(row) - 1

    /**
     * 리소스팩 화면의 제목 = 배경 글자 + 원래 제목. 색을 정하지 않은 제목은 **흰색**(어두운 제목 띠 위) — 일반 화면에서는 바닐라 기본(어두운 회색)이라
     * 제목에 색을 적지 않는 것이 두 화면 모두에서 읽힌다.
     */
    fun title(background: Char, text: Component): Component =
        Component.text()
            .color(NamedTextColor.WHITE)
            .append(Component.text("$SHIFT_TO_EDGE$background$RETURN_TO_TITLE").font(FONT))
            .append(text)
            .build()

    /** 투명 모델 — `assets/inmc/items/gui/menu/blank.json`(`minecraft:empty`). 칸에 놓아 이름·설명만 뜨게 한다. */
    val BLANK_MODEL = NamespacedKey("inmc", "gui/menu/blank")

    fun blank(): ItemStack = ItemStack(Material.PAPER).also { stack -> stack.editMeta { it.setItemModel(BLANK_MODEL) } }

    /** 버튼 칸의 그림 조각 아이템(`PackExport` 의 칸 조각) — 마우스를 올렸을 때 뒤의 흰 칸을 가린다. */
    fun cell(name: String): ItemStack =
        ItemStack(Material.PAPER).also { stack -> stack.editMeta { it.setItemModel(NamespacedKey("inmc", "gui/menu/cell/$name")) } }
}
