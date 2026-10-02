package com.inmc.menu.menu

import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material

/**
 * 처음 한 번 까는 메뉴 둘 — 관리자가 편집기로 바꾼다.
 *
 * 큰 기능은 **3×2**(54×36 픽셀 — 위에 아이콘, 아래에 이름 네 글자까지), 긴 띠는 4×1(아이콘 옆에 이름), 작은 것은 1칸(아이콘만).
 * 2×2(36 픽셀)에는 한글 두 글자까지만 들어가 "랜덤 TP"·"경매장" 이 넘친다(갈무리 11 — 한글 폭 12).
 *
 * ```
 * main(6줄)  0  설정 칭호 업적 · 내정보 · 돈 장비 판매
 *            1-2  [ 스폰 ]   [ 랜덤 TP ]  [ 상점 ]
 *            3-4  [ 경매장 ]  [ 가방 ]    [ 낚시 ]
 *            5  [ 콘텐츠    ] 닫기 [ 출석      ]
 * ```
 * 그림 이름(`art`)은 버튼 id 와 같다 — 기본 그림이 그 이름으로 있다(`pack/Icons`).
 */
object DefaultMenus {

    const val MAIN = "main"
    const val CONTENTS = "contents"

    private fun icon(material: Material) = StoredItem(ItemRef.parse("minecraft:" + material.key.key), material)

    private fun button(
        id: String, col: Int, row: Int, width: Int, height: Int, material: Material, name: String, lore: List<String>,
        vararg left: Action, special: Special? = null,
    ) = ButtonDef(
        id = id, col = col, row = row, width = width, height = height, name = name, lore = lore, icon = icon(material),
        art = id, left = left.toList(), special = special,
    )

    private fun cmd(command: String) = Action.PlayerCommand(command)

    fun main(): MenuDef = MenuDef(
        MAIN, "서버 메뉴", 6,
        listOf(
            button("settings", 0, 0, 1, 1, Material.COMPARATOR, "<yellow>개인 설정</yellow>", listOf("<gray>광역 채굴·개인 시간·날씨·숨기기 …</gray>"), Action.OpenScreen(Screen.SETTINGS)),
            button("title", 1, 0, 1, 1, Material.NAME_TAG, "<light_purple>칭호</light_purple>", listOf("<gray>칭호 · 인장 · 닉네임</gray>"), cmd("칭호")),
            button("achievements", 2, 0, 1, 1, Material.GOLDEN_APPLE, "<gold>업적</gold>", listOf("<gray>진행도와 보상</gray>"), cmd("업적")),
            button(
                "profile", 4, 0, 1, 1, Material.PLAYER_HEAD, "<gold>{플레이어}</gold>",
                listOf("<gray>잔고: <white>%inmceco_formatted%</white></gray>", "<gray>칭호: </gray>%titleforge_title_display_mini%", "<gray>플레이 시간: <white>{플레이타임}</white></gray>"),
                special = Special.PROFILE,
            ),
            button("money", 6, 0, 1, 1, Material.GOLD_NUGGET, "<gold>돈</gold>", listOf("<gray>잔고 · 송금 · 순위</gray>"), cmd("돈")),
            button("equipment", 7, 0, 1, 1, Material.DIAMOND_CHESTPLATE, "<aqua>장비</aqua>", listOf("<gray>장신구 · 부적 · 유물 칸</gray>"), cmd("장비")),
            button("sell", 8, 0, 1, 1, Material.HOPPER, "<green>판매</green>", listOf("<gray>가방의 물건을 팝니다.</gray>"), cmd("판매")),
            button("spawn", 0, 1, 3, 2, Material.RED_BED, "<green>스폰</green>", listOf("<gray>서버 스폰으로 이동합니다.</gray>"), Action.OpenScreen(Screen.SPAWN)),
            button("rtp", 3, 1, 3, 2, Material.ENDER_PEARL, "<aqua>랜덤 TP</aqua>", listOf("<gray>월드를 골라 아무도 안 가 본 곳으로.</gray>"), Action.OpenScreen(Screen.RTP)),
            button("shop", 6, 1, 3, 2, Material.EMERALD, "<green>상점</green>", listOf("<gray>서버 상점에서 사고팝니다.</gray>"), cmd("상점")),
            button("auction", 0, 3, 3, 2, Material.GOLD_INGOT, "<yellow>경매장</yellow>", listOf("<gray>플레이어끼리 사고팝니다.</gray>"), cmd("경매장")),
            button("backpack", 3, 3, 3, 2, Material.BUNDLE, "<gold>가방</gold>", listOf("<gray>장착한 배낭을 엽니다.</gray>"), cmd("배낭")),
            button("fishing", 6, 3, 3, 2, Material.FISHING_ROD, "<aqua>낚시</aqua>", listOf("<gray>도감 · 어망 · 대회</gray>"), cmd("낚시")),
            button("contents", 0, 5, 4, 1, Material.CHEST, "<yellow>콘텐츠</yellow>", listOf("<gray>미니게임 · 드랍 정보 · 제작 …</gray>"), Action.OpenMenu(CONTENTS)),
            button("close", 4, 5, 1, 1, Material.BARRIER, "<red>닫기</red>", emptyList(), Action.Close),
            button("attendance", 5, 5, 4, 1, Material.CLOCK, "<gold>출석</gold>", listOf("<gray>오늘의 출석 보상</gray>"), Action.OpenScreen(Screen.ATTENDANCE)),
        ),
    )

    fun contents(): MenuDef = MenuDef(
        CONTENTS, "콘텐츠", 4,
        listOf(
            button("back", 0, 0, 1, 1, Material.ARROW, "<gray>◀ 메인 메뉴</gray>", emptyList(), Action.OpenMenu(MAIN)),
            button("numbergame", 0, 1, 3, 2, Material.PAPER, "<yellow>숫자게임</yellow>", listOf("<gray>숫자를 맞혀 보상을.</gray>"), cmd("숫자게임")),
            button("drops", 3, 1, 3, 2, Material.ROTTEN_FLESH, "<green>드랍 정보</green>", listOf("<gray>바라보는 몹·블록에서 무엇이 나오나</gray>"), cmd("드랍 정보")),
            button("craft", 6, 1, 3, 2, Material.CRAFTING_TABLE, "<gold>제작대</gold>", listOf("<gray>커스텀 아이템 제작</gray>"), cmd("제작")),
            button("chestshop", 0, 3, 4, 1, Material.BARREL, "<aqua>상자상점</aqua>", listOf("<gray>플레이어 상점 둘러보기</gray>"), cmd("상자상점 둘러보기")),
            button("close", 4, 3, 1, 1, Material.BARRIER, "<red>닫기</red>", emptyList(), Action.Close),
        ),
    )

    fun all(): List<MenuDef> = listOf(main(), contents())
}
