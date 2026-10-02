package com.inmc.menu.pack

import com.inmc.menu.menu.MenuDef
import kr.inmc.core.util.Text

/**
 * 그림에 영향을 주는 것만의 지문 — 줄 수, 버튼마다 칸 묶음·그림 이름·이름(판에 글씨로 쓴다). 설명·동작·권한은 그림이 아니라
 * 툴팁·클릭이라 빠진다(그것을 고쳐도 다시 그릴 필요가 없다).
 */
object LayoutPrint {

    fun of(def: MenuDef): Int =
        (listOf(def.rows.toString()) + def.buttons.sortedBy { it.id }.map { "${it.id}@${it.col},${it.row},${it.width},${it.height}:${it.art}:${Text.plain(it.name).trim()}" }).hashCode()
}
