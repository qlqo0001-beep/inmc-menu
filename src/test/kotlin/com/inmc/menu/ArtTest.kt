package com.inmc.menu

import com.inmc.menu.menu.DefaultMenus
import com.inmc.menu.menu.Special
import com.inmc.menu.pack.ArtRenderer
import com.inmc.menu.pack.BdfFont
import com.inmc.menu.pack.Glyphs
import com.inmc.menu.pack.Icons
import kr.inmc.core.util.Text
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 배경 그림 — **버튼 판이 칸 경계와 정확히 맞는가**를 픽셀로 본다. 미리보기(3배, 칸 자리 빨간 선)를 `build/art-preview/` 에 남긴다.
 */
class ArtTest {

    private val font = BdfFont.bundled()
    private val renderer = ArtRenderer(font)
    private val out = File("build/art-preview").apply { mkdirs() }

    @Test
    fun `글꼴이 jar 에 들어 있고 한글을 그린다`() {
        assertNotNull(font, "font/Galmuri11-Bold.bdf")
        for (ch in "스폰랜덤상점경매장가방낚시콘텐츠출석TP") assertTrue(font.has(ch.code), "글자 $ch")
        assertEquals(12 + font.width("점"), font.width("상점"), "한글은 12씩 나아가고, 끝 글자는 그림 폭만 센다")
        assertEquals(12 - 1 + font.width("점"), font.width("상점", spacing = -1), "간격을 1 줄이면 1 줄어든다")
    }

    @Test
    fun `기본 메뉴 — 크기, 판 모서리가 칸 경계, 이름이 들어간다`() {
        for (def in DefaultMenus.all()) {
            val image = renderer.menu(def)
            assertEquals(Glyphs.WIDTH, image.width)
            assertEquals(Glyphs.height(def.rows), image.height)
            for (b in def.buttons) {
                if (b.special == Special.PROFILE) continue
                val x0 = Glyphs.cellX(b.col)
                val y0 = Glyphs.cellY(b.row)
                val x1 = x0 + 18 * b.width - 1
                val y1 = y0 + 18 * b.height - 1
                for ((x, y) in listOf(x0 to y0, x1 to y0, x0 to y1, x1 to y1)) {
                    assertEquals(ArtRenderer.BORDER, image.getRGB(x, y), "${def.id}.${b.id} 의 모서리 ($x,$y)")
                }
                // 판 안쪽 가운데는 테두리가 아니다(판이 칸 묶음을 덮었다).
                assertTrue(image.getRGB((x0 + x1) / 2, (y0 + y1) / 2) != ArtRenderer.PANEL, "${def.id}.${b.id} 안쪽이 비었다")
                if (b.width >= 3) {
                    val label = Text.plain(b.name)
                    val room = 18 * b.width - (if (b.height >= 2) 0 else 18) - 4
                    assertTrue(font!!.width(label, -1) <= room, "${def.id}.${b.id} 이름 '$label' 이 판에 안 들어간다")
                }
            }
            ImageIO.write(image, "png", File(out, "${def.id}.png"))
            ImageIO.write(renderer.preview(image, def.rows), "png", File(out, "${def.id}-preview.png"))
        }
        val grid = renderer.grid(6)
        ImageIO.write(grid, "png", File(out, "grid-6.png"))
        ImageIO.write(renderer.preview(grid, 6), "png", File(out, "grid-6-preview.png"))
    }

    @Test
    fun `기본 버튼마다 아이콘이 있다`() {
        for (def in DefaultMenus.all()) for (b in def.buttons) {
            if (b.special != null) continue
            assertTrue(b.art in Icons.names(), "${def.id}.${b.id} 의 그림 '${b.art}' 이 없다")
            val icon = Icons.draw(b.art)!!
            assertEquals(Icons.SIZE, icon.width)
            assertTrue((0 until 16).any { x -> (0 until 16).any { y -> icon.getRGB(x, y) ushr 24 != 0 } }, "${b.art} 빈 그림")
        }
    }
}
