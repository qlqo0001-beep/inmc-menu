package com.inmc.menu.settings

import com.inmc.menu.Hub
import kr.inmc.core.integration.PlayerSettings
import org.bukkit.Material
import org.bukkit.WeatherType
import org.bukkit.entity.Player

/**
 * 메뉴가 core 개인 설정 창구([PlayerSettings])에 올리는 것과 그것을 사람에게 거는 일.
 *
 * 개인 시간·날씨는 **그 사람 화면에만** 바뀐다(`setPlayerTime`·`setPlayerWeather` — 서버의 낮밤·몹 스폰과 무관). 접속할 때마다 다시 건다 —
 * 서버가 기억하지 않는다.
 */
class MenuSettings(private val hub: Hub) {

    fun register() {
        PlayerSettings.register(
            PlayerSettings.Setting(
                HOTKEY, OWNER, "Shift+F 메뉴", Material.COMPASS,
                listOf("웅크리고 F 를 누르면 이 메뉴가 열립니다.", "끄면 손 바꾸기 그대로 — /메뉴 는 늘 됩니다."),
                PlayerSettings.Toggle(true),
            ),
        )
        PlayerSettings.register(
            PlayerSettings.Setting(
                TIME, OWNER, "개인 시간", Material.CLOCK,
                listOf("내 화면의 하늘만 바뀝니다(서버의 낮밤·몹과 무관)."),
                PlayerSettings.Choice(TIMES.map { it.id to it.label }, SERVER),
                permission = "inmcmenu.setting.time",
            ),
        )
        PlayerSettings.register(
            PlayerSettings.Setting(
                WEATHER, OWNER, "개인 날씨", Material.WATER_BUCKET,
                listOf("내 화면의 날씨만 바뀝니다."),
                PlayerSettings.Choice(listOf(SERVER to "서버 따름", "clear" to "맑음", "rain" to "비"), SERVER),
                permission = "inmcmenu.setting.weather",
            ),
        )
        PlayerSettings.register(
            PlayerSettings.Setting(
                HIDE_PLAYERS, OWNER, "다른 플레이어 숨기기", Material.ENDER_EYE,
                listOf("스폰처럼 붐비는 곳에서 다른 플레이어를 안 보이게 합니다.", "관리자가 정한 월드에서만 동작합니다."),
                PlayerSettings.Toggle(false),
                permission = "inmcmenu.setting.hide-players",
            ),
        )
        PlayerSettings.listen(OWNER) { player, key ->
            when (key) {
                TIME, WEATHER -> apply(player)
                HIDE_PLAYERS -> hub.visibility.refreshViewer(player)
            }
        }
    }

    fun unregister() {
        PlayerSettings.unlisten(OWNER)
        PlayerSettings.unregisterAll(OWNER)
    }

    /** 시간·날씨를 건다 — 접속할 때와 바꿀 때. */
    fun apply(player: Player) {
        val time = TIMES.firstOrNull { it.id == PlayerSettings.choice(player, TIME, SERVER) }
        if (time?.ticks == null) player.resetPlayerTime() else player.setPlayerTime(time.ticks, false)
        when (PlayerSettings.choice(player, WEATHER, SERVER)) {
            "clear" -> player.setPlayerWeather(WeatherType.CLEAR)
            "rain" -> player.setPlayerWeather(WeatherType.DOWNFALL)
            else -> player.resetPlayerWeather()
        }
    }

    fun hotkeyOn(player: Player): Boolean = PlayerSettings.enabled(player, HOTKEY)

    /** 개인 시간의 고르기. [ticks] 가 null 이면 서버 따름. 고정(`relative=false`) — 그 시각에 멈춘다. */
    data class TimeChoice(val id: String, val label: String, val ticks: Long?)

    companion object {

        const val OWNER = "플레이어 메뉴"

        const val HOTKEY = "menu.hotkey"
        const val TIME = "menu.time"
        const val WEATHER = "menu.weather"
        const val HIDE_PLAYERS = "menu.hide-players"

        const val SERVER = "server"

        val TIMES = listOf(
            TimeChoice(SERVER, "서버 따름", null),
            TimeChoice("morning", "아침", 1_000),
            TimeChoice("day", "낮", 6_000),
            TimeChoice("evening", "저녁", 12_500),
            TimeChoice("night", "밤", 18_000),
        )
    }
}
