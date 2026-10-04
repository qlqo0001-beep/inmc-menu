package com.inmc.menu.gui

import com.inmc.menu.Hub
import kr.inmc.core.gui.Icon
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * `/메뉴 어드민` — 각 inmc 플러그인의 설정 화면으로 가는 허브.
 *
 * 연동은 버튼의 플레이어 명령어 실행으로만 한다(메뉴 설계 그대로 — 타 플러그인 import 없음).
 * 각 플러그인의 설정 화면에서 돌아올 때는 그 화면의 "어드민 메뉴로" 버튼을 누른다.
 * 명령어가 바뀌면 여기 버튼도 같이 바뀐다 — 컴파일러가 못 잡으므로 `/메뉴 검증`...이 아니라
 * 눈으로 확인할 것. 버튼마다 실제 열리는 것을 적은 주석을 둔다.
 */
class AdminHubMenu(hub: Hub, viewer: Player) : Menu(hub, viewer, SIZE, "<dark_gray>어드민 메뉴</dark_gray>") {

    override fun draw() {
        clear()
        for ((slot, entry) in ENTRIES.withIndex()) {
            set(slot, Icon.of(entry.material, entry.name, listOf(entry.hint, "", "<yellow>▶ 클릭: 열기</yellow>"))) {
                viewer.performCommand(entry.command)
            }
        }
        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private data class Entry(val material: Material, val name: String, val hint: String, val command: String)

    private companion object {
        const val SIZE = 27
        const val SLOT_CLOSE = 26

        val ENTRIES = listOf(
            Entry(Material.FISHING_ROD, "<aqua>낚시 설정</aqua>", "<gray>/낚시 관리 화면</gray>", "낚시 관리"),
            Entry(Material.BARREL, "<yellow>인벤키퍼 설정</yellow>", "<gray>/인벤키퍼 관리 화면</gray>", "인벤키퍼 관리"),
            Entry(Material.ROTTEN_FLESH, "<green>드랍 설정</green>", "<gray>/드랍 메인 화면(관리자)</gray>", "드랍"),
            Entry(Material.EMERALD, "<green>상점 설정</green>", "<gray>/상점 관리 화면</gray>", "상점 관리"),
            Entry(Material.COMPASS, "<gold>메뉴 설정</gold>", "<gray>/메뉴 관리 화면</gray>", "메뉴 관리"),
            Entry(Material.NAME_TAG, "<light_purple>칭호 설정</light_purple>", "<gray>/it 메뉴 화면</gray>", "it menu"),
            Entry(Material.GOLDEN_APPLE, "<gold>업적 설정</gold>", "<gray>/업적 관리 화면</gray>", "업적 관리"),
            Entry(Material.GOLD_NUGGET, "<gold>돈 설정</gold>", "<gray>/돈 관리 화면</gray>", "돈 관리"),
            Entry(Material.CRAFTING_TABLE, "<gold>커스텀아이템 설정</gold>", "<gray>/커스텀아이템 관리 화면</gray>", "커스텀아이템 관리"),
            Entry(Material.ENCHANTING_TABLE, "<aqua>인첸트 설정</aqua>", "<gray>/인첸트 관리 화면</gray>", "인첸트 관리"),
            Entry(Material.CHEST, "<yellow>랜덤박스</yellow>", "<gray>/urb 상자 목록(관리자)</gray>", "urb"),
            Entry(Material.PAPER, "<yellow>숫자게임 설정</yellow>", "<gray>/숫자게임 관리 화면</gray>", "숫자게임 관리"),
            Entry(Material.ZOMBIE_HEAD, "<red>몬스터 설정</red>", "<gray>/몹 gui 화면</gray>", "몹 gui"),
        )
    }
}
