package com.inmc.menu.pack

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * 그린 것을 **커스텀아이템의 팩 소스 폴더**(`plugins/inmc-customitems/pack/sources/40-inmc-menu/`)에 쓴다 — 커스텀아이템의 병합기가 합친다.
 * 모두 이름공간 `inmc` 의 `gui/menu/` 아래·글꼴 `inmc:menu` 라 다른 팩의 파일과 겹치지 않는다.
 *
 * - `assets/inmc/font/menu.json` — 공백 글자 둘 + 배경마다 비트맵 글자(`ascent` 13, 높이 = 그림 높이)
 * - `assets/inmc/textures/gui/menu/bg/<이름>.png` — 배경
 * - `assets/inmc/items/gui/menu/blank.json` — 투명 아이템(`minecraft:empty`)
 * - `assets/inmc/textures/item/gui/menu/cell/<배경>_<칸>.png` + 모델 + 아이템 정의 — 버튼 칸마다 **그 자리 그림 조각**(16×16). 칸의 아이템이
 *   배경과 똑같은 그림을 불투명하게 그려, 마우스를 올렸을 때 바닐라가 아이템 **뒤에** 까는 흰 칸(37%)을 가린다(테섭 2026-10-02 "칸이 하얗게 된다").
 *   아이템 **앞의** 옅은 덮개(12%)는 서버가 못 가린다. 텍스처는 아이템 아틀라스에 들어가야 해서 `textures/item/` 아래.
 *
 * 순수 함수 [fontJson] 은 테스트가 본다. 파일 쓰기는 워커에서.
 */
object PackExport {

    /** 배경 하나 — 글자, 파일 이름(영문 — 리소스 경로는 `[a-z0-9_./-]` 만 받는다, 지뢰 16), 그림, 그림 조각을 낼 칸들. */
    class Background(val glyph: Char, val file: String, val image: BufferedImage, val cells: Collection<Int> = emptyList())

    /** 칸 조각의 이름 — 아이템 모델 `inmc:gui/menu/cell/<이것>`. */
    fun cellName(file: String, slot: Int): String = "${file}_$slot"

    /** 칸 [slot] 의 아이템 자리(16×16) 그림. */
    fun cellImage(image: BufferedImage, slot: Int): BufferedImage =
        image.getSubimage(Glyphs.itemX(slot % 9), Glyphs.itemY(slot / 9), 16, 16).let { sub ->
            BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).also { it.createGraphics().apply { drawImage(sub, 0, 0, null); dispose() } }
        }

    fun cellModelJson(name: String): String =
        "{\"parent\": \"minecraft:item/generated\", \"textures\": {\"layer0\": \"inmc:item/gui/menu/cell/$name\"}}\n"

    fun cellItemJson(name: String): String =
        "{\"model\": {\"type\": \"minecraft:model\", \"model\": \"inmc:gui/menu/cell/$name\"}}\n"

    fun fontJson(backgrounds: List<Background>): String = buildString {
        append("{\n  \"providers\": [\n")
        append("    {\"type\": \"space\", \"advances\": {")
        append(Glyphs.ADVANCES.entries.joinToString(", ") { (ch, advance) -> "\"${escape(ch)}\": $advance" })
        append("}}")
        for (bg in backgrounds) {
            append(",\n    {\"type\": \"bitmap\", \"file\": \"inmc:gui/menu/bg/${bg.file}.png\", \"ascent\": ${Glyphs.ASCENT}, ")
            append("\"height\": ${bg.image.height}, \"chars\": [\"${escape(bg.glyph)}\"]}")
        }
        append("\n  ]\n}\n")
    }

    const val BLANK_JSON = "{\"model\": {\"type\": \"minecraft:empty\"}}\n"

    /** [root] 아래에 쓴다. 우리 배경 폴더는 비우고 다시 쓴다(지운 메뉴의 그림이 남지 않게). */
    fun write(root: File, backgrounds: List<Background>) {
        val bgDir = File(root, "assets/inmc/textures/gui/menu/bg")
        bgDir.deleteRecursively()
        bgDir.mkdirs()
        for (bg in backgrounds) ImageIO.write(bg.image, "png", File(bgDir, bg.file + ".png"))
        File(root, "assets/inmc/font").apply { mkdirs() }.resolve("menu.json").writeText(fontJson(backgrounds), Charsets.UTF_8)
        File(root, "assets/inmc/items/gui/menu").apply { mkdirs() }.resolve("blank.json").writeText(BLANK_JSON, Charsets.UTF_8)

        val textures = File(root, "assets/inmc/textures/item/gui/menu/cell")
        val models = File(root, "assets/inmc/models/gui/menu/cell")
        val items = File(root, "assets/inmc/items/gui/menu/cell")
        for (dir in listOf(textures, models, items)) {
            dir.deleteRecursively()
            dir.mkdirs()
        }
        for (bg in backgrounds) for (slot in bg.cells) {
            val name = cellName(bg.file, slot)
            ImageIO.write(cellImage(bg.image, slot), "png", File(textures, "$name.png"))
            File(models, "$name.json").writeText(cellModelJson(name), Charsets.UTF_8)
            File(items, "$name.json").writeText(cellItemJson(name), Charsets.UTF_8)
        }
    }

    private fun escape(ch: Char): String = "\\u%04X".format(ch.code)
}
