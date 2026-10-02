package com.inmc.menu

import com.inmc.menu.menu.Action
import com.inmc.menu.menu.ButtonDef
import com.inmc.menu.menu.DefaultMenus
import com.inmc.menu.menu.MenuDef
import com.inmc.menu.menu.Screen
import com.inmc.menu.menu.Special
import com.inmc.menu.pack.LayoutPrint
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {

    private val paper = StoredItem(ItemRef.parse("minecraft:paper"), Material.PAPER)

    private fun button(id: String, col: Int, row: Int, w: Int = 1, h: Int = 1) = ButtonDef(id, col, row, w, h, name = id, icon = paper)

    private fun reload(yaml: YamlConfiguration) = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }

    @Test
    fun `동작은 한 줄로 쓰고 읽는다`() {
        val actions = listOf(
            Action.PlayerCommand("상점"), Action.ConsoleCommand("give {플레이어} diamond 1"), Action.OpenMenu("contents"),
            Action.OpenScreen(Screen.RTP), Action.Message("<green>안녕: 반가워</green>"), Action.Close,
        )
        for (action in actions) assertEquals(action, Action.decode(action.encode()), action.encode())
        assertEquals(Action.PlayerCommand("배낭"), Action.decode("player: /배낭"), "앞의 / 는 뗀다")
        assertEquals(Action.Message("a: b: c"), Action.decode("message: a: b: c"), "값 안의 쌍점은 그대로")
        assertNull(Action.decode("nope: 1"))
        assertNull(Action.decode("screen: nowhere"))
        assertNull(Action.decode("player:   "))
    }

    @Test
    fun `버튼의 칸 묶음 — 칸 번호·범위·겹침`() {
        val big = button("big", 2, 1, 2, 2)
        assertEquals(listOf(11, 12, 20, 21), big.slots())
        assertEquals(11, big.anchor)
        assertTrue(big.fits(3))
        assertFalse(big.fits(2), "줄 밖")
        assertFalse(button("edge", 8, 0, 2, 1).fits(6), "열 밖")
        assertTrue(big.overlaps(button("x", 3, 2)))
        assertFalse(big.overlaps(button("y", 4, 1)), "옆 칸은 안 겹친다")
        assertFalse(big.overlaps(button("z", 2, 3)), "아래 칸은 안 겹친다")
    }

    @Test
    fun `메뉴는 겹치는 자리에 못 놓고, 자기 자신은 옮길 수 있다`() {
        val def = MenuDef("m", "t", 3, listOf(button("a", 0, 0, 2, 2), button("b", 4, 0)))
        assertFalse(def.canPlace(button("c", 1, 1)))
        assertTrue(def.canPlace(button("a", 0, 1, 2, 2)), "a 가 자기 자리와 겹치는 것은 괜찮다")
        assertFalse(def.canPlace(button("a", 3, 0, 2, 2)), "b 와 겹친다")
        assertEquals("b", def.buttonAt(4)?.id)
        assertNull(def.buttonAt(8))
    }

    @Test
    fun `저장했다 읽어도 그대로다`() {
        val def = MenuDef(
            "main", "<white>서버 메뉴</white>", 6,
            listOf(
                ButtonDef("spawn", 0, 1, 2, 2, "<green>스폰</green>", listOf("한 줄", "두 줄"), paper, "spawn", "x.y", true,
                    listOf(Action.OpenScreen(Screen.SPAWN)), listOf(Action.PlayerCommand("스폰")), null),
                ButtonDef("profile", 4, 0, name = "{플레이어}", icon = paper, special = Special.PROFILE),
            ),
        )
        val again = MenuDef.load("main", reload(def.save()))
        assertEquals(def.copy(rejected = emptyMap()), again.copy(rejected = emptyMap()))
        assertTrue(again.rejected.isEmpty())
    }

    @Test
    fun `손으로 고쳐 겹치거나 밖으로 나간 버튼은 빼되 다음 저장에서 사라지지 않는다`() {
        val yaml = MenuDef("m", "t", 2, listOf(button("a", 0, 0, 2, 2))).save()
        yaml.set("buttons.clash.at", listOf(1, 1))
        yaml.set("buttons.clash.name", "겹침")
        yaml.set("buttons.out.at", listOf(0, 5))
        yaml.set("buttons.out.name", "밖")
        val loaded = MenuDef.load("m", reload(yaml))
        assertEquals(listOf("a"), loaded.buttons.map { it.id })
        assertEquals(setOf("clash", "out"), loaded.rejected.keys)
        // 다른 버튼을 고쳐 저장해도 뺀 버튼의 원문은 남는다.
        val saved = reload(loaded.with(button("b", 2, 0)).save())
        assertEquals("겹침", saved.getString("buttons.clash.name"))
        assertEquals(listOf(0, 5), saved.getIntegerList("buttons.out.at"))
    }

    @Test
    fun `기본 메뉴 — 범위 안·안 겹침·id 규칙·가리키는 메뉴가 있다`() {
        val menus = DefaultMenus.all()
        val ids = menus.map { it.id }.toSet()
        for (def in menus) {
            for ((i, b) in def.buttons.withIndex()) {
                assertTrue(b.fits(def.rows), "${def.id}.${b.id} 범위")
                assertTrue(ButtonDef.ID.matches(b.id), "${def.id}.${b.id} id")
                for (other in def.buttons.drop(i + 1)) assertFalse(b.overlaps(other), "${def.id}: ${b.id} 와 ${other.id} 겹침")
                for (action in b.left + b.right) if (action is Action.OpenMenu) assertTrue(action.menu in ids, "${b.id} → 없는 메뉴 ${action.menu}")
            }
            assertEquals(def, MenuDef.load(def.id, reload(def.save())).copy(rejected = emptyMap()), "${def.id} 왕복")
        }
        assertTrue(DefaultMenus.MAIN in ids)
    }

    @Test
    fun `배치 지문은 그림에 영향을 주는 것만 본다`() {
        val def = DefaultMenus.main()
        val retold = def.copy(buttons = def.buttons.map { it.copy(lore = listOf("x"), left = emptyList(), permission = "x.y") })
        assertEquals(LayoutPrint.of(def), LayoutPrint.of(retold), "설명·동작·권한은 그림이 아니다")
        // 이름은 판에 글씨로 쓴다 — 색만 바꾼 것은 같은 글씨.
        val recolored = def.copy(buttons = def.buttons.map { it.copy(name = "<red>" + kr.inmc.core.util.Text.plain(it.name) + "</red>") })
        assertEquals(LayoutPrint.of(def), LayoutPrint.of(recolored))
        assertNotEquals(LayoutPrint.of(def), LayoutPrint.of(def.copy(buttons = def.buttons.map { it.copy(name = "다른 이름") })))
        val moved = def.with(def.buttons.first { it.id == "close" }.copy(col = 5))
        assertNotEquals(LayoutPrint.of(def), LayoutPrint.of(moved))
        assertNotEquals(LayoutPrint.of(def), LayoutPrint.of(def.copy(rows = 5)))
    }
}
