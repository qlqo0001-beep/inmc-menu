package com.inmc.menu.config

import com.inmc.menu.util.Ph
import kr.inmc.core.config.MessageCatalog
import org.bukkit.configuration.file.YamlConfiguration

/**
 * `messages.yml` 한 벌. 읽고 보내는 부분은 core 의 [MessageCatalog] 가 갖고 있고 여기는 기본값 표뿐이다.
 * `ResourceTest` 가 배포 파일과 이 표의 키가 정확히 같은지, 코드가 부르는 키가 전부 있는지 지킨다.
 */
class Messages(values: Map<String, String>) : MessageCatalog<Ph>(values, DEFAULTS) {

    companion object {

        fun from(config: YamlConfiguration): Messages = Messages(merge(DEFAULTS, config))

        val DEFAULTS: Map<String, String> = linkedMapOf(
            PREFIX to "<gradient:#7f7fd5:#86a8e7>[ 메뉴 ]</gradient> ",

            // --- 공통 ---------------------------------------------------------------
            "player-only" to "<red>플레이어만 쓸 수 있습니다.</red>",
            "no-permission" to "<red>권한이 없습니다.</red>",
            "reloaded" to "<green>다시 불러왔습니다. 메뉴 {count}개.</green>",
            "invalid-id" to "<red>이름은 소문자 영문·숫자·한글·_- 만, 32자까지입니다: {value}</red>",
            "already-exists" to "<red>'{value}' 은(는) 이미 있습니다.</red>",
            "hand-empty" to "<red>손에 아이템을 들고 누르세요.</red>",

            // --- 메뉴 ---------------------------------------------------------------
            "menu-missing" to "<red>'{value}' 메뉴가 없습니다. /메뉴 편집 으로 만드세요.</red>",
            "locked" to "<red>이 버튼을 쓸 권한이 없습니다.</red>",

            // --- 개인 설정 -----------------------------------------------------------
            "setting-locked" to "<red>이 설정을 바꿀 권한이 없습니다.</red>",

            // --- 편집기 -------------------------------------------------------------
            "editor-blocked" to "<red>그 자리에는 다른 버튼이 있거나 메뉴 밖입니다.</red>",
            "button-deleted" to "<yellow>버튼을 지웠습니다.</yellow>",
            "menu-created" to "<green>'{value}' 메뉴를 만들었습니다.</green>",
            "menu-deleted" to "<yellow>'{value}' 메뉴를 지웠습니다.</yellow>",
            "main-undeletable" to "<red>첫 메뉴(Shift+F 가 여는 것)는 지울 수 없습니다. 설정에서 다른 메뉴를 첫 메뉴로 정한 뒤에.</red>",
            "rows-blocked" to "<red>줄을 줄이면 밖으로 나가는 버튼이 있습니다. 먼저 옮기거나 지우세요.</red>",

            // --- 그림(리소스팩 화면) ---------------------------------------------------
            "art-busy" to "<yellow>이미 그리는 중입니다.</yellow>",
            "art-failed" to "<red>그림을 쓰지 못했습니다: {value}</red>",
            "art-drawn" to "<gray>메뉴 {count}개를 그렸습니다 — 리소스팩을 다시 만듭니다…</gray>",
            "art-ready" to "<green>새 리소스팩에 메뉴 그림이 들어갔습니다.</green> <gray>팩을 다시 받은 사람부터 그림 화면입니다.</gray>",
            "art-timeout" to "<red>새 리소스팩에서 메뉴 그림을 찾지 못했습니다(2분).</red> <gray>/커스텀아이템 리팩 빌드 결과를 보세요.</gray>",
            "art-no-pack-tool" to "<yellow>커스텀아이템이 없습니다</yellow> <gray>— {value} 를 쓰는 팩에 직접 합치세요.</gray>",

            // --- 랜덤 TP -------------------------------------------------------------
            "rtp-disabled" to "<red>이 월드로는 랜덤 이동을 할 수 없습니다.</red>",
            "rtp-no-world" to "<red>'{world}' 은(는) 랜덤 이동 월드가 아니거나 열려 있지 않습니다.</red>",
            "rtp-in-progress" to "<yellow>이미 이동을 준비하고 있습니다 — 움직이지 말고 기다려 주세요.</yellow>",
            "rtp-cooldown" to "<red>{time} 뒤에 다시 쓸 수 있습니다.</red>",
            "rtp-no-money" to "<red>돈이 모자랍니다. 필요: {amount}</red>",
            "rtp-no-currency" to "<red>비용을 받을 화폐를 찾지 못했습니다. 관리자에게 알려 주세요.</red>",
            "rtp-busy" to "<yellow>서버가 바쁩니다 — 잠시 뒤에 다시 눌러 주세요.</yellow>",
            "rtp-queue-full" to "<yellow>지금 이동하려는 사람이 많습니다 — 잠시 뒤에 다시 눌러 주세요.</yellow>",
            "rtp-cancelled-move" to "<red>움직여서 이동을 취소했습니다.</red>",
            "rtp-cancelled-damage" to "<red>공격받아 이동을 취소했습니다.</red>",
            "rtp-failed" to "<red>이번에는 안전한 자리를 찾지 못했습니다. 다시 눌러 주세요.</red> <gray>(비용·쿨타임은 쓰지 않았습니다)</gray>",
            "rtp-done" to "<green>무작위 위치로 이동했습니다.</green> <gray>({value})</gray>",
            "rtp-title" to "<gold><bold>{count}</bold></gold>",
            "rtp-subtitle" to "<gray>무작위 위치로 이동합니다 — 움직이지 마세요</gray>",
            "rtp-subtitle-queued" to "<gray>대기 {count}번째 — 움직이지 마세요</gray>",
            "rtp-searching" to "<yellow>자리를 찾는 중…</yellow>",
            "rtp-go-title" to "<green><bold>이동!</bold></green>",
            "rtp-marks-cleared" to "<green>{world} 의 실패 구역 기록을 지웠습니다.</green>",
            "rtp-saved" to "<green>{world} 랜덤 TP 설정을 저장했습니다.</green>",

            // --- 스폰 ---------------------------------------------------------------
            "spawn-title" to "<aqua><bold>{count}</bold></aqua>",
            "spawn-subtitle" to "<gray>스폰으로 이동합니다 — 움직이지 마세요</gray>",
            "spawn-done" to "<green>스폰으로 이동했습니다.</green>",
            "spawn-cooldown" to "<red>{time} 뒤에 다시 쓸 수 있습니다.</red>",
            "spawn-cancelled-move" to "<red>움직여서 이동을 취소했습니다.</red>",
            "spawn-cancelled-damage" to "<red>공격받아 이동을 취소했습니다.</red>",
            "spawn-set" to "<green>서버 스폰을 정했습니다: {value}</green>",

            // --- 출석 ---------------------------------------------------------------
            "attend-none" to "<gray>지금 열린 출석판이 없습니다.</gray>",
            "attend-loading" to "<gray>출석 기록을 읽는 중입니다 — 잠시 뒤에 다시 눌러 주세요.</gray>",
            "attend-wait" to "<gray>오늘 {count}분 더 접속하면 출석할 수 있습니다.</gray>",
            "attend-already" to "<yellow>오늘은 이미 출석했습니다.</yellow>",
            "attend-remind" to "<yellow>오늘 아직</yellow> {value} <yellow>출석을 하지 않았습니다.</yellow> <gray>/출석</gray>",
            "attend-done-calendar" to "{value} <green>출석 완료!</green> <gray>(이달 {count}일째)</gray>",
            "attend-done-streak" to "{value} <green>출석 완료!</green> <gray>(연속 {count}일째)</gray>",
            "attend-done-total" to "{value} <green>출석 완료!</green> <gray>(총 {count}번째)</gray>",
            "attend-bonus" to "{value} <gold>— 이달 {count}일 출석 보상!</gold>",
            "attend-pending" to "<yellow>가방이 가득 차 보상 {count}개를 보관했습니다 — /출석 에서 받으세요.</yellow>",
            "attend-claimed" to "<green>보관된 보상 {count}개를 받았습니다.</green>",
            "attend-claim-left" to "<yellow>가방이 가득 차 {count}개가 남았습니다.</yellow>",
            "attend-board-created" to "<green>'{value}' 출석판을 만들었습니다.</green>",
            "attend-board-deleted" to "<yellow>'{value}' 출석판을 지웠습니다.</yellow>",
            "attend-closed" to "<gray>{value}<gray> 출석판은 지금 열려 있지 않습니다.</gray>",
            "attend-board-copied" to "<green>'{value}' 출석판으로 복사했습니다.</green>",
            "attend-board-reset" to "<yellow>'{value}' 출석판을 초기화했습니다 — 모든 사람의 기록이 처음부터입니다.</yellow>",
            "attend-player-reset" to "<yellow>{player} 의 '{value}' 출석 기록을 초기화했습니다.</yellow>",
            "attend-player-reset-none" to "<gray>{player} 은(는) '{value}' 출석 기록이 없습니다.</gray>",
            "attend-no-board" to "<red>'{value}' 출석판이 없습니다.</red>",
            "attend-no-player" to "<red>'{player}' 은(는) 이 서버에 온 적이 없는 플레이어입니다.</red>",
            "attend-month-invalid" to "<red>달은 2026-11 처럼 적으세요(비우면 늘): {value}</red>",

            // --- 검증 ---------------------------------------------------------------
            "verify-done" to "<green>검증 끝 — 통과 {amount} · 실패 {count}</green> <gray>(plugins/inmc-menu/verify/ 에 기록)</gray>",
            "verify-failure" to "<red>✖</red> <gray>{value}</gray>",

            // --- 도움말 -------------------------------------------------------------
            "help" to listOf(
                "<gold>/메뉴</gold> <gray>· </gray><gold>Shift+F</gold> <gray>- 서버 메뉴</gray>",
                "<gold>/설정</gold> <gray>- 개인 설정</gray>",
                "<gold>/출석</gold> <gray>- 출석</gray> <gray>· </gray><red>/출석 관리</red> <gray>· </gray><red>/출석 초기화 <플레이어> <판></red>",
                "<gold>/스폰</gold> <gray>- 서버 스폰으로</gray> <gray>· </gray><red>/스폰 설정</red> <gray>· </gray><red>/스폰 관리</red>",
                "<gold>/야생</gold> <gray>- 랜덤 이동(월드 고르기)</gray> <gray>· </gray><red>/야생 관리</red> <gray>· </gray><red>/야생 상태</red>",
                "<red>/메뉴 편집 [메뉴]</red> <gray>· </gray><red>/메뉴 관리</red> <gray>· </gray><red>/메뉴 어드민</red> <gray>· </gray><red>/메뉴 리로드</red> <gray>· </gray><red>/메뉴 검증</red>",
            ).joinToString("\n"),
        )
    }
}
