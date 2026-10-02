package com.inmc.menu.pack

import com.inmc.menu.menu.ButtonDef
import com.inmc.menu.menu.MenuDef
import com.inmc.menu.menu.Special
import kr.inmc.core.util.Text
import java.awt.Color
import java.awt.image.BufferedImage

/**
 * 리소스팩 화면의 배경을 **배치에서** 그린다 — 칸 좌표는 [Glyphs.cellX]·[Glyphs.cellY] 하나의 식이라 그림과 칸이 어긋날 수 없다.
 *
 * 버튼 판은 칸 묶음 전체(18×너비, 18×높이)를 덮는다. 크기에 따라:
 * - 3칸 이상 × 2줄 이상: 위에 아이콘(16), 아래에 이름(갈무리 11)
 * - 3칸 이상 × 1줄: 왼쪽 아이콘, 오른쪽 이름
 * - 그 밖: 아이콘만(들어가는 만큼 정수배로 키움)
 * 이름이 안 들어가면 글자 간격을 1 줄여 보고, 그래도 넘치면 쓰지 않는다(마우스를 올리면 이름이 보인다).
 *
 * 서버 스레드와 상관없이 돈다(그림만 만든다) — 워커에서 불러도 된다.
 */
class ArtRenderer(
    private val font: BdfFont?,
    /** 관리자가 넣은 그림(`art/<이름>.png`). 없으면 null. */
    private val custom: (String) -> BufferedImage? = { null },
) {

    /** 메뉴 하나의 배경. */
    fun menu(def: MenuDef): BufferedImage {
        val image = frame(def.rows)
        for (button in def.buttons) tile(image, button)
        return image
    }

    /** 내장 화면(개인 설정·출석 …)의 배경 — 칸마다 오목한 자리. */
    fun grid(rows: Int): BufferedImage {
        val image = frame(rows)
        for (row in 0 until rows) for (col in 0 until 9) recess(image, Glyphs.cellX(col), Glyphs.cellY(row), 18, 18)
        return image
    }

    // --- 판 ---------------------------------------------------------------------------------

    private fun frame(rows: Int): BufferedImage {
        val image = BufferedImage(Glyphs.WIDTH, Glyphs.height(rows), BufferedImage.TYPE_INT_ARGB)
        fill(image, 0, 0, image.width, image.height, PANEL)
        // 바깥 테두리 + 안쪽 밝은 선(위·왼쪽) — 바닐라 창처럼 도드라져 보이게.
        rect(image, 0, 0, image.width, image.height, BORDER)
        line(image, 1, 1, image.width - 2, 1, PANEL_LIGHT)
        vline(image, 1, 1, image.height - 2, PANEL_LIGHT)
        line(image, 1, image.height - 2, image.width - 2, image.height - 2, PANEL_DARK)
        vline(image, image.width - 2, 1, image.height - 2, PANEL_DARK)
        // 제목 띠(제목 글자는 클라이언트가 그 위에 쓴다 — x 8, y 6).
        fill(image, 2, 2, image.width - 4, 14, TITLE_BAND)
        line(image, 2, 16, image.width - 3, 16, BORDER)
        return image
    }

    private fun tile(image: BufferedImage, button: ButtonDef) {
        val x = Glyphs.cellX(button.col)
        val y = Glyphs.cellY(button.row)
        val w = 18 * button.width
        val h = 18 * button.height
        if (button.special == Special.PROFILE) {
            // 진짜 아이템(머리)이 그 위에 놓인다 — 오목한 자리만.
            recess(image, x, y, w, h)
            return
        }
        val base = Color(colorOf(button.art.ifBlank { button.id }))
        fill(image, x, y, w, h, base.rgb)
        rect(image, x, y, w, h, BORDER)
        line(image, x + 1, y + 1, x + w - 2, y + 1, base.brighter().rgb)
        vline(image, x + 1, y + 1, y + h - 2, base.brighter().rgb)
        line(image, x + 1, y + h - 2, x + w - 2, y + h - 2, base.darker().rgb)
        vline(image, x + w - 2, y + 1, y + h - 2, base.darker().rgb)

        val icon = art(button.art)
        val label = Text.plain(button.name).trim()
        when {
            button.width >= 3 && button.height >= 2 -> {
                icon?.let { paste(image, it, x + (w - Icons.SIZE) / 2, y + 4, 1) }
                label(image, label, x, y + h - 4, w)
            }
            button.width >= 3 -> {
                icon?.let { paste(image, it, x + 2, y + 1, 1) }
                label(image, label, x + 18, y + 15, w - 18)
            }
            icon != null -> {
                val scale = minOf((w - 2) / Icons.SIZE, (h - 2) / Icons.SIZE).coerceAtLeast(1)
                val size = Icons.SIZE * scale
                paste(image, icon, x + (w - size) / 2, y + (h - size) / 2, scale)
            }
        }
    }

    /** 이름 — [left]..[left]+[width] 의 가운데, 기준선 [baseline]. 안 들어가면 간격을 줄여 보고 그래도 넘치면 쓰지 않는다. */
    private fun label(image: BufferedImage, text: String, left: Int, baseline: Int, width: Int) {
        val f = font ?: return
        if (text.isEmpty()) return
        val room = width - 4
        val spacing = listOf(0, -1).firstOrNull { f.width(text, it) <= room } ?: return
        val x = left + (width - f.width(text, spacing)) / 2
        f.draw(image, text, x + 1, baseline + 1, SHADOW, spacing)
        f.draw(image, text, x, baseline, TEXT, spacing)
    }

    /** 오목한 자리 — 칸 하나(18)면 바닐라 칸처럼 16×16 안쪽. */
    private fun recess(image: BufferedImage, x: Int, y: Int, w: Int, h: Int) {
        fill(image, x, y, w, h, RECESS_EDGE)
        fill(image, x + 1, y + 1, w - 2, h - 2, RECESS)
        line(image, x + 1, y + h - 1, x + w - 1, y + h - 1, PANEL_LIGHT)
        vline(image, x + w - 1, y + 1, y + h - 1, PANEL_LIGHT)
    }

    private fun art(name: String): BufferedImage? = if (name.isBlank()) null else custom(name) ?: Icons.draw(name)

    // --- 미리보기 -------------------------------------------------------------------------------

    /**
     * 확인용 — 배경을 [scale] 배로 키우고 **칸마다 아이템이 그려질 16×16 자리를 빨간 선으로** 겹친다. 버튼 판이 칸 경계와 맞는지 눈으로 본다.
     */
    fun preview(background: BufferedImage, rows: Int, scale: Int = 3): BufferedImage {
        val out = BufferedImage(background.width * scale, background.height * scale, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until out.height) for (x in 0 until out.width) out.setRGB(x, y, background.getRGB(x / scale, y / scale))
        val red = Color(255, 60, 60).rgb
        for (row in 0 until rows) for (col in 0 until 9) {
            val x0 = Glyphs.itemX(col) * scale
            val y0 = Glyphs.itemY(row) * scale
            rect(out, x0, y0, 16 * scale, 16 * scale, red)
        }
        return out
    }

    // --- 픽셀 도구 ------------------------------------------------------------------------------

    private fun paste(image: BufferedImage, icon: BufferedImage, x: Int, y: Int, scale: Int) {
        val sx = icon.width.toDouble() / (Icons.SIZE * scale).coerceAtLeast(1)
        val sy = icon.height.toDouble() / (Icons.SIZE * scale).coerceAtLeast(1)
        for (dy in 0 until Icons.SIZE * scale) for (dx in 0 until Icons.SIZE * scale) {
            val argb = icon.getRGB((dx * sx).toInt().coerceAtMost(icon.width - 1), (dy * sy).toInt().coerceAtMost(icon.height - 1))
            if (argb ushr 24 < 128) continue
            val px = x + dx
            val py = y + dy
            if (px in 0 until image.width && py in 0 until image.height) image.setRGB(px, py, argb or (0xff shl 24))
        }
    }

    private fun fill(image: BufferedImage, x: Int, y: Int, w: Int, h: Int, argb: Int) {
        for (py in y.coerceAtLeast(0) until (y + h).coerceAtMost(image.height)) for (px in x.coerceAtLeast(0) until (x + w).coerceAtMost(image.width)) image.setRGB(px, py, argb)
    }

    private fun rect(image: BufferedImage, x: Int, y: Int, w: Int, h: Int, argb: Int) {
        line(image, x, y, x + w - 1, y, argb)
        line(image, x, y + h - 1, x + w - 1, y + h - 1, argb)
        vline(image, x, y, y + h - 1, argb)
        vline(image, x + w - 1, y, y + h - 1, argb)
    }

    private fun line(image: BufferedImage, x0: Int, y: Int, x1: Int, @Suppress("UNUSED_PARAMETER") y1: Int, argb: Int) {
        if (y !in 0 until image.height) return
        for (x in x0.coerceAtLeast(0)..x1.coerceAtMost(image.width - 1)) image.setRGB(x, y, argb)
    }

    private fun vline(image: BufferedImage, x: Int, y0: Int, y1: Int, argb: Int) {
        if (x !in 0 until image.width) return
        for (y in y0.coerceAtLeast(0)..y1.coerceAtMost(image.height - 1)) image.setRGB(x, y, argb)
    }

    companion object {

        private fun argb(rgb: Int) = rgb or (0xff shl 24)

        val PANEL = argb(0x262938)
        val PANEL_LIGHT = argb(0x3a3e54)
        val PANEL_DARK = argb(0x181a25)
        val BORDER = argb(0x0d0e15)
        val TITLE_BAND = argb(0x1e2030)
        val RECESS = argb(0x14151f)
        val RECESS_EDGE = argb(0x0d0e15)
        val TEXT = argb(0xffffff)
        val SHADOW = argb(0x0d0e15)

        /** 기본 버튼의 판 색 — 기능마다 알아보기 쉬운 색. 모르는 이름은 이름에서 색을 뽑는다(같은 이름이면 늘 같은 색). */
        private val COLORS = mapOf(
            "spawn" to 0x3f8f4f, "rtp" to 0x2f7f9f, "shop" to 0x2e8f60, "auction" to 0xa8802b, "backpack" to 0x8a5a34,
            "fishing" to 0x2f62a8, "contents" to 0x6f4fa0, "attendance" to 0xb8652b, "settings" to 0x4f5466, "title" to 0x8f4f8a,
            "achievements" to 0xa8862b, "money" to 0xa8802b, "equipment" to 0x3f6fa8, "sell" to 0x3f8f4f, "close" to 0x9a3a3a,
            "back" to 0x4f5466, "numbergame" to 0x6f4fa0, "drops" to 0x4f8f3f, "craft" to 0x8a5a34, "chestshop" to 0x2f7f9f,
        )

        fun colorOf(name: String): Int = COLORS[name] ?: run {
            val hue = (name.hashCode() and 0x7fffffff) % 360 / 360f
            Color.HSBtoRGB(hue, 0.45f, 0.55f) and 0xffffff
        }
    }
}
