package com.inmc.menu.pack

import java.awt.image.BufferedImage
import java.io.InputStream

/**
 * BDF 비트맵 글꼴을 읽어 픽셀 그대로 찍는다 — 갈무리 11 굵은체(SIL OFL 1.1, `font/Galmuri-OFL.md`).
 *
 * 자바 글꼴 엔진(java.awt.Font)을 쓰지 않는다 — 일부 리눅스 서버는 글꼴 라이브러리가 없어 글씨를 그리다 던지고, 픽셀 글꼴도 엔진을 거치면
 * 번지거나 반 픽셀 밀린다. BDF 는 글자마다 픽셀이 16진수로 적혀 있어 그대로 옮기면 된다.
 */
class BdfFont private constructor(private val glyphs: Map<Int, Glyph>, val ascent: Int) {

    /** [advance] 다음 글자까지, [width]·[height] 그림 크기, [xOff]·[yOff] 기준선에서의 위치(BDF 의 BBX). */
    class Glyph(val advance: Int, val width: Int, val height: Int, val xOff: Int, val yOff: Int, val rows: IntArray)

    fun has(codePoint: Int): Boolean = glyphs.containsKey(codePoint)

    /** 글줄의 너비(픽셀). [spacing] 은 글자마다 더하거나 뺄 간격. */
    fun width(text: String, spacing: Int = 0): Int {
        val points = text.codePoints().toArray()
        if (points.isEmpty()) return 0
        var total = 0
        for ((i, cp) in points.withIndex()) {
            val glyph = glyphs[cp] ?: glyphs['?'.code] ?: continue
            total += if (i == points.lastIndex) glyph.xOff + glyph.width else glyph.advance + spacing
        }
        return total
    }

    /** [x]·[baseline] 에서 글줄을 찍는다. 없는 글자는 '?'. */
    fun draw(image: BufferedImage, text: String, x: Int, baseline: Int, argb: Int, spacing: Int = 0) {
        var cursor = x
        text.codePoints().forEach { cp ->
            val glyph = glyphs[cp] ?: glyphs['?'.code] ?: return@forEach
            val top = baseline - glyph.yOff - glyph.height
            for (row in 0 until glyph.height) {
                val bits = glyph.rows[row]
                for (col in 0 until glyph.width) {
                    // 한 줄의 픽셀은 왼쪽부터 가장 높은 비트 — 바이트 단위로 채워져 있다.
                    val bytes = (glyph.width + 7) / 8
                    if (bits shr (bytes * 8 - 1 - col) and 1 == 0) continue
                    val px = cursor + glyph.xOff + col
                    val py = top + row
                    if (px in 0 until image.width && py in 0 until image.height) image.setRGB(px, py, argb)
                }
            }
            cursor += glyph.advance + spacing
        }
    }

    companion object {

        /** jar 안의 글꼴. 못 읽으면 null — 글씨 없이 그린다. */
        fun bundled(): BdfFont? = runCatching {
            BdfFont::class.java.classLoader.getResourceAsStream("font/Galmuri11-Bold.bdf")?.use(::parse)
        }.getOrNull()

        fun parse(input: InputStream): BdfFont {
            val glyphs = HashMap<Int, Glyph>(16_384)
            var ascent = 0
            var encoding = -1
            var advance = 0
            var bbx = IntArray(4)
            var rows: MutableList<Int>? = null
            input.bufferedReader(Charsets.UTF_8).forEachLine { raw ->
                val line = raw.trim()
                when {
                    rows != null && line == "ENDCHAR" -> {
                        if (encoding >= 0) glyphs[encoding] = Glyph(advance, bbx[0], bbx[1], bbx[2], bbx[3], rows!!.toIntArray())
                        rows = null
                    }
                    rows != null -> rows!!.add(line.toInt(16))
                    line.startsWith("FONT_ASCENT ") -> ascent = line.substringAfter(' ').trim().toInt()
                    line.startsWith("ENCODING ") -> encoding = line.substringAfter(' ').trim().toInt()
                    line.startsWith("DWIDTH ") -> advance = line.split(' ')[1].toInt()
                    line.startsWith("BBX ") -> bbx = line.split(' ').drop(1).map(String::toInt).toIntArray()
                    line == "BITMAP" -> rows = ArrayList()
                }
            }
            return BdfFont(glyphs, ascent)
        }
    }
}
