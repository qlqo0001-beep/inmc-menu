package com.inmc.menu

import com.google.gson.JsonParser
import com.inmc.menu.pack.Glyphs
import com.inmc.menu.pack.MenuArt
import com.inmc.menu.pack.PackExport
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 팩에 쓰는 글꼴 json·파일, 메뉴마다 글자 번호. */
class PackExportTest {

    private fun bg(index: Int, rows: Int, file: String) =
        PackExport.Background(Glyphs.background(index), file, BufferedImage(Glyphs.WIDTH, Glyphs.height(rows), BufferedImage.TYPE_INT_ARGB))

    @Test
    fun `글꼴 json — 공백 둘과 배경마다 비트맵, ascent 는 높이를 넘지 않는다`() {
        val json = JsonParser.parseString(PackExport.fontJson(listOf(bg(0, 1, "grid1"), bg(16, 6, "m16")))).asJsonObject
        val providers = json.getAsJsonArray("providers").map { it.asJsonObject }
        assertEquals(3, providers.size)

        val space = providers[0]
        assertEquals("space", space.get("type").asString)
        val advances = space.getAsJsonObject("advances")
        assertEquals(-8, advances.get("").asInt)
        assertEquals(-169, advances.get("").asInt)

        val one = providers[1]
        assertEquals("bitmap", one.get("type").asString)
        assertEquals("inmc:gui/menu/bg/grid1.png", one.get("file").asString)
        assertEquals(Glyphs.height(1), one.get("height").asInt)
        assertEquals("", one.getAsJsonArray("chars")[0].asString)
        val six = providers[2]
        assertEquals(Glyphs.height(6), six.get("height").asInt)
        assertEquals("", six.getAsJsonArray("chars")[0].asString)
        // 클라이언트는 ascent 가 height 보다 크면 글꼴을 통째로 버린다.
        for (p in providers.drop(1)) assertTrue(p.get("ascent").asInt <= p.get("height").asInt)
    }

    @Test
    fun `쓰기 — 배경 폴더를 비우고 다시 쓴다`() {
        val root = Files.createTempDirectory("menu-pack").toFile()
        try {
            val stale = root.resolve("assets/inmc/textures/gui/menu/bg/m99.png").apply { parentFile.mkdirs(); writeText("old") }
            PackExport.write(root, listOf(bg(5, 6, "grid6"), bg(17, 4, "m17")))
            assertFalse(stale.exists(), "지운 메뉴의 그림이 남았다")
            val png = ImageIO.read(root.resolve("assets/inmc/textures/gui/menu/bg/m17.png"))
            assertEquals(Glyphs.WIDTH, png.width)
            assertEquals(Glyphs.height(4), png.height)
            assertTrue(root.resolve("assets/inmc/font/menu.json").isFile)
            assertEquals(PackExport.BLANK_JSON, root.resolve("assets/inmc/items/gui/menu/blank.json").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `칸 조각 — 아이템 자리 16x16 을 그대로 잘라 모델·아이템 정의와 함께 쓴다`() {
        val image = BufferedImage(Glyphs.WIDTH, Glyphs.height(2), BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) image.setRGB(x, y, (0xff shl 24) or (x shl 8) or y)
        val slot = 9 + 4 // 둘째 줄 다섯째 칸
        val cell = PackExport.cellImage(image, slot)
        assertEquals(16, cell.width)
        for ((dx, dy) in listOf(0 to 0, 15 to 15, 3 to 9)) {
            assertEquals(image.getRGB(Glyphs.itemX(4) + dx, Glyphs.itemY(1) + dy), cell.getRGB(dx, dy), "($dx,$dy)")
        }
        val root = Files.createTempDirectory("menu-cells").toFile()
        try {
            val stale = root.resolve("assets/inmc/textures/item/gui/menu/cell/m99_0.png").apply { parentFile.mkdirs(); writeText("old") }
            PackExport.write(root, listOf(PackExport.Background(Glyphs.background(16), "m16", image, listOf(slot))))
            val name = PackExport.cellName("m16", slot)
            assertTrue(root.resolve("assets/inmc/textures/item/gui/menu/cell/$name.png").isFile)
            assertFalse(stale.exists(), "지운 칸의 조각이 남았다")
            val model = JsonParser.parseString(root.resolve("assets/inmc/models/gui/menu/cell/$name.json").readText()).asJsonObject
            assertEquals("inmc:item/gui/menu/cell/$name", model.getAsJsonObject("textures").get("layer0").asString, "텍스처는 아이템 아틀라스(textures/item) 안")
            val item = JsonParser.parseString(root.resolve("assets/inmc/items/gui/menu/cell/$name.json").readText()).asJsonObject
            assertEquals("inmc:gui/menu/cell/$name", item.getAsJsonObject("model").get("model").asString)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `글자 번호 — 정한 것은 그대로, 새 메뉴는 빈 번호, 격자 자리는 안 쓴다`() {
        val first = MenuArt.assign(listOf("main", "contents"), emptyMap())
        assertEquals(mapOf("contents" to 16, "main" to 17), first)
        // 메뉴가 늘어도 원래 번호는 그대로.
        val second = MenuArt.assign(listOf("main", "contents", "a-new"), first)
        assertEquals(16, second["contents"])
        assertEquals(17, second["main"])
        assertEquals(18, second["a-new"])
        // 지운 메뉴의 번호는 다음 새 메뉴가 쓴다.
        val third = MenuArt.assign(listOf("main", "b"), second)
        assertEquals(17, third["main"])
        assertEquals(16, third["b"])
        // 격자 자리(0~15)에 있던 옛 번호는 버리고 새로 받는다.
        assertTrue(MenuArt.assign(listOf("x"), mapOf("x" to 3)).getValue("x") >= MenuArt.FIRST_MENU)
        assertEquals(MenuArt.grid(1), Glyphs.background(0))
        assertEquals(MenuArt.grid(6), Glyphs.background(5))
    }
}
