package com.inmc.menu

import com.inmc.menu.gui.ActionListMenu
import com.inmc.menu.gui.ButtonEditor
import com.inmc.menu.gui.ManageMenu
import com.inmc.menu.gui.SettingsMenu
import kr.inmc.core.gui.Paging
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 슬롯 상수 — 겹치면 나중에 그린 버튼만 보이고 **안 보이는 버튼의 클릭이 남는다**. 범위 밖은 `Menu.set` 이 조용히 버린다(지뢰 2·12).
 */
class MenuLayoutTest {

    private fun slots(type: Class<*>): Map<String, Int> =
        type.declaredFields.filter { Modifier.isStatic(it.modifiers) && it.name.startsWith("SLOT_") && it.type == Int::class.javaPrimitiveType }
            .associate { it.isAccessible = true; it.name to it.getInt(null) }

    private fun check(type: Class<*>, size: Int, paging: Boolean = false, contentEnd: Int = 0) {
        val map = slots(type)
        assertTrue(map.isNotEmpty(), type.simpleName)
        for ((name, slot) in map) {
            assertTrue(slot in 0 until size, "${type.simpleName}.$name = $slot 은 $size 칸 밖")
            assertTrue(slot >= contentEnd, "${type.simpleName}.$name = $slot 이 목록 칸을 덮는다")
        }
        val all = map.entries.map { it.key to it.value } + if (paging) listOf(
            "Paging.SLOT_PREV" to Paging.SLOT_PREV, "Paging.SLOT_NEXT" to Paging.SLOT_NEXT, "Paging.SLOT_CLOSE" to Paging.SLOT_CLOSE,
        ) else emptyList()
        val clashes = all.groupBy { it.second }.filter { it.value.size > 1 }
        assertTrue(clashes.isEmpty(), "${type.simpleName} 겹침: $clashes")
    }

    @Test
    fun `화면마다 슬롯이 범위 안이고 겹치지 않는다`() {
        check(ButtonEditor::class.java, ButtonEditor.SIZE)
        check(ActionListMenu::class.java, ActionListMenu.SIZE, contentEnd = ActionListMenu.MAX)
        check(ManageMenu::class.java, ManageMenu.SIZE, paging = true, contentEnd = ManageMenu.PER_PAGE)
        // "설정 없음"(22)은 목록이 비었을 때만 그린다.
        check(SettingsMenu::class.java, SettingsMenu.SIZE, paging = true)
    }
}
