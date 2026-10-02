package com.inmc.menu.verify

import com.inmc.menu.Hub
import com.inmc.menu.attend.AttendanceService
import com.inmc.menu.attend.Board
import com.inmc.menu.attend.Claim
import com.inmc.menu.gui.MenuView
import com.inmc.menu.listener.SpawnListener
import com.inmc.menu.settings.MenuSettings
import com.inmc.menu.spawn.SpawnConfig
import com.inmc.menu.spawn.SpawnPoint
import com.inmc.menu.spawn.Source
import com.inmc.menu.spawn.Step
import com.inmc.menu.util.Ph
import kr.inmc.core.integration.PlayerSettings
import org.bukkit.Bukkit
import org.bukkit.WeatherType
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * `/메뉴 검증` — 서버 안에서 **진짜 사건을 쏘아** 배선을 확인한다(드랍·상점 검증기와 같은 방식). 순수 계산(부활 순서·출석·고리 뽑기 …)은
 * 단위 시험이 보고, 여기는 리스너·설정 창구·다른 플러그인과의 연결을 본다.
 *
 * - 검증하는 사람을 대상으로 한다. 바꾼 개인 설정·웅크림·설정(숨기기 월드·스폰)은 **메모리에서만** 바꿨다가 그 틱에 되돌린다(파일에 안 쓴다).
 * - Shift+F 사건은 진짜라 다른 플러그인도 받는다(낚싯대를 들고 돌리면 낚시가 가져가 실패로 보인다 — 빈손으로).
 * - 랜덤 TP 는 찾기를 시작했다가 곧바로 취소한다(이동·비용·쿨타임 없음). 그 사이 청크 하나를 읽을 수는 있다.
 */
class Verifier(private val hub: Hub) {

    data class Result(val name: String, val failure: String?)

    private class Check(val name: String, val run: (Player) -> String?)

    fun run(player: Player) {
        val results = CHECKS.map { check ->
            val failure = try {
                check.run(player)
            } catch (t: Throwable) {
                "검증기 오류: " + t.javaClass.simpleName + (t.message?.let { ": $it" } ?: "")
            }
            Result(check.name, failure)
        }
        val failures = results.filter { it.failure != null }
        hub.messages.send(player, "verify-done", Ph.of().amount((results.size - failures.size).toString()).count(failures.size))
        for (failure in failures) hub.messages.send(player, "verify-failure", Ph.of().value("${failure.name} — ${failure.failure}"))

        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = hub.io.file("verify", "menu-$stamp.txt")
        val text = buildString {
            appendLine("# inmc-menu 검증 - ${LocalDateTime.now()} - ${player.name}")
            for (r in results) appendLine((if (r.failure == null) "PASS " else "FAIL ") + r.name + (r.failure?.let { " — $it" } ?: ""))
        }
        hub.io.asyncRun {
            file.parentFile.mkdirs()
            file.writeText(text, Charsets.UTF_8)
        }
    }

    // --- 검사 ---------------------------------------------------------------------------------

    private val CHECKS: List<Check> = listOf(
        Check("Shift+F 가 메뉴를 연다") { p -> withHotkey(p, true) { swap(p, sneaking = true) }.let { (cancelled, opened) ->
            when {
                !hub.config.hotkey -> "서버 전체 Shift+F 가 꺼져 있습니다(/메뉴 관리)"
                !cancelled -> "손 바꾸기가 취소되지 않았습니다 — 다른 플러그인이 먼저 가져갔거나 리스너가 없습니다"
                !opened -> "사건은 가져갔는데 메뉴 화면이 열리지 않았습니다"
                else -> null
            }
        } },
        Check("Shift+F 를 끈 사람에게는 손 바꾸기 그대로") { p ->
            val (cancelled, _) = withHotkey(p, false) { swap(p, sneaking = true) }
            if (cancelled) "개인 설정을 껐는데 가져갔습니다" else null
        },
        Check("웅크리지 않은 F 는 손 바꾸기 그대로") { p ->
            val (cancelled, _) = withHotkey(p, true) { swap(p, sneaking = false) }
            if (cancelled) "웅크리지 않았는데 가져갔습니다" else null
        },
        Check("개인 시간이 걸린다") { p ->
            if (!PlayerSettings.allowed(p, PlayerSettings.get(MenuSettings.TIME) ?: return@Check "개인 시간 설정이 올라와 있지 않습니다")) return@Check null
            val before = PlayerSettings.choice(p, MenuSettings.TIME, "server")
            try {
                PlayerSettings.set(p, MenuSettings.TIME, "night")
                when {
                    p.isPlayerTimeRelative -> "밤으로 바꿨는데 시간이 고정되지 않았습니다"
                    p.playerTime % 24000 != 18000L -> "밤(18000)이 아니라 ${p.playerTime % 24000} 입니다"
                    else -> null
                }
            } finally {
                PlayerSettings.set(p, MenuSettings.TIME, before)
            }
        },
        Check("개인 날씨가 걸린다") { p ->
            if (!PlayerSettings.allowed(p, PlayerSettings.get(MenuSettings.WEATHER) ?: return@Check "개인 날씨 설정이 올라와 있지 않습니다")) return@Check null
            val before = PlayerSettings.choice(p, MenuSettings.WEATHER, "server")
            try {
                PlayerSettings.set(p, MenuSettings.WEATHER, "rain")
                if (p.playerWeather != WeatherType.DOWNFALL) "비로 바꿨는데 ${p.playerWeather} 입니다" else null
            } finally {
                PlayerSettings.set(p, MenuSettings.WEATHER, before)
            }
        },
        Check("다른 플레이어 숨기기는 정한 월드에서만") { p ->
            val config = hub.config
            val before = PlayerSettings.enabled(p, MenuSettings.HIDE_PLAYERS, false)
            try {
                PlayerSettings.set(p, MenuSettings.HIDE_PLAYERS, true)
                hub.config = config.copy(hideWorlds = config.hideWorlds - p.world.name)
                val outside = hub.visibility.hides(p)
                hub.config = config.copy(hideWorlds = config.hideWorlds + p.world.name)
                val inside = hub.visibility.hides(p) || !PlayerSettings.allowed(p, PlayerSettings.get(MenuSettings.HIDE_PLAYERS)!!)
                when {
                    outside -> "정하지 않은 월드에서 숨깁니다"
                    !inside -> "정한 월드에서 숨기지 않습니다"
                    else -> null
                }
            } finally {
                hub.config = config
                PlayerSettings.set(p, MenuSettings.HIDE_PLAYERS, before)
                hub.visibility.refreshViewer(p)
            }
        },
        Check("다른 플러그인의 개인 설정이 올라와 있다") { _ ->
            val missing = buildList {
                if (PlayerSettings.get(PlayerSettings.RARE_ANNOUNCE) == null) add("희귀 드랍 공지(core)")
                if (Bukkit.getPluginManager().isPluginEnabled("inmc-enchants")) {
                    if (PlayerSettings.get("enchants.area-mining") == null) add("광역 채굴(인첸트)")
                    if (PlayerSettings.get("enchants.tree-felling") == null) add("나무 통째 베기(인첸트)")
                }
                if (Bukkit.getPluginManager().isPluginEnabled("inmc-customitems") && PlayerSettings.get("customitems.auto-pickup") == null) add("배낭 자동 수납(커스텀아이템)")
            }
            if (missing.isEmpty()) null else "없음: " + missing.joinToString(", ") + " — 그 플러그인이 새 판인지 보세요"
        },
        Check("부활 순서 — 서버 스폰이 앞이면 침대보다 먼저") { p ->
            withSpawn(SpawnConfig(point = SpawnPoint.of(p.location), respawn = listOf(Step(Source.SERVER), Step(Source.BED), Step(Source.ANCHOR), Step(Source.WORLD)))) {
                val elsewhere = p.location.clone().add(0.0, 50.0, 0.0)
                val event = PlayerRespawnEvent(p, elsewhere, true, false, false, PlayerRespawnEvent.RespawnReason.DEATH)
                SpawnListener(hub).onRespawn(event)
                if (event.respawnLocation.distanceSquared(p.location) > 0.01) "서버 스폰이 아니라 ${event.respawnLocation.toVector()} 입니다" else null
            }
        },
        Check("부활 순서 — 침대가 앞이면 침대 그대로, 엔드 귀환은 안 건드린다") { p ->
            withSpawn(SpawnConfig(point = SpawnPoint.of(p.location))) {
                val bed = p.location.clone().add(0.0, 50.0, 0.0)
                val event = PlayerRespawnEvent(p, bed, true, false, false, PlayerRespawnEvent.RespawnReason.DEATH)
                SpawnListener(hub).onRespawn(event)
                val portal = PlayerRespawnEvent(p, bed, false, false, false, PlayerRespawnEvent.RespawnReason.END_PORTAL)
                SpawnListener(hub).onRespawn(portal)
                when {
                    event.respawnLocation != bed -> "침대 부활을 바꿨습니다"
                    portal.respawnLocation != bed -> "엔드 귀환을 바꿨습니다"
                    else -> null
                }
            }
        },
        Check("출석 기록이 읽혀 있다") { p ->
            when {
                hub.attendance.enabled().isEmpty() -> null
                hub.attendance.data(p.uniqueId) == null -> "들어올 때 읽은 기록이 없습니다 — 접속 리스너를 보세요"
                else -> null
            }
        },
        Check("출석 — 사용할 달이 아닌 판은 화면에도 없고 출석도 안 된다") { p ->
            if (hub.attendance.data(p.uniqueId) == null) return@Check null
            val probe = Board("zz_verify", month = YearMonth.from(hub.attendance.today()).plusMonths(1), claim = Claim.MANUAL)
            val before = hub.attendance.boards
            try {
                hub.attendance.swap(listOf(probe))
                when {
                    hub.attendance.enabled().isNotEmpty() -> "다음 달 판이 이번 달에 열려 있습니다"
                    hub.attendance.attend(p, probe) != AttendanceService.Outcome.CLOSED -> "다음 달 판에 출석이 됩니다"
                    else -> null
                }
            } finally {
                hub.attendance.swap(before)
            }
        },
        Check("랜덤 TP — 누르면 찾기, 연타는 하나, 취소하면 끝") { p ->
            val world = hub.rtpWorlds.usable().firstOrNull { it.permission.isBlank() || p.hasPermission(it.permission) } ?: return@Check null
            if (hub.rtp.inProgress(p.uniqueId)) return@Check "이미 이동을 준비하고 있습니다 — 끝난 뒤 다시"
            if (!p.hasPermission(Hub.RTP_BYPASS) && (hub.rtp.cooldownLeft(p.uniqueId, world) > 0 || world.cost > 0)) return@Check null
            val before = hub.rtp.searching + hub.rtp.waiting
            hub.rtp.request(p, world)
            val started = hub.rtp.inProgress(p.uniqueId)
            hub.rtp.request(p, world)
            val after = hub.rtp.searching + hub.rtp.waiting
            hub.rtp.cancel(p.uniqueId, null)
            when {
                !started -> "찾기가 시작되지 않았습니다(바쁨·대기열 가득 · 월드 설정을 보세요)"
                after - before > 1 -> "연타로 찾기가 ${after - before}개 생겼습니다"
                hub.rtp.inProgress(p.uniqueId) -> "취소했는데 아직 준비 중입니다"
                else -> null
            }
        },
        Check("메뉴 파일에 버려진 버튼이 없다") { _ ->
            val rejected = hub.menus.all().filter { it.rejected.isNotEmpty() }.map { "${it.id}(${it.rejected.keys.joinToString(",")})" }
            if (rejected.isEmpty()) null else "칸 밖이거나 겹친 버튼: " + rejected.joinToString(" ")
        },
        Check("리소스팩 화면 준비") { _ ->
            when {
                !hub.config.packEnabled -> null
                hub.art.busy -> "지금 그리는 중입니다"
                !hub.artReady -> "팩에 메뉴 그림이 없습니다 — /메뉴 관리 → 그림 다시 만들기"
                hub.art.stale().isNotEmpty() -> "고친 뒤 안 그린 메뉴: " + hub.art.stale().joinToString(", ")
                else -> null
            }
        },
    )

    /** 웅크림·Shift+F 개인 설정을 잠깐 바꿔 손 바꾸기를 쏜다 → (취소됐나, 메뉴가 열렸나). */
    private fun withHotkey(p: Player, on: Boolean, fire: () -> Boolean): Pair<Boolean, Boolean> {
        val before = PlayerSettings.enabled(p, MenuSettings.HOTKEY)
        val sneaking = p.isSneaking
        try {
            PlayerSettings.set(p, MenuSettings.HOTKEY, on)
            val cancelled = fire()
            val opened = p.openInventory.topInventory.holder is MenuView
            return cancelled to opened
        } finally {
            if (p.openInventory.topInventory.holder is MenuView) p.closeInventory()
            PlayerSettings.set(p, MenuSettings.HOTKEY, before)
            p.isSneaking = sneaking
        }
    }

    private fun swap(p: Player, sneaking: Boolean): Boolean {
        p.isSneaking = sneaking
        val event = PlayerSwapHandItemsEvent(p, p.inventory.itemInOffHand.clone(), p.inventory.itemInMainHand.clone())
        Bukkit.getPluginManager().callEvent(event)
        return event.isCancelled
    }

    private fun withSpawn(config: SpawnConfig, check: () -> String?): String? {
        val before = hub.spawn.config
        try {
            hub.spawn.swap(config)
            return check()
        } finally {
            hub.spawn.swap(before)
        }
    }
}
