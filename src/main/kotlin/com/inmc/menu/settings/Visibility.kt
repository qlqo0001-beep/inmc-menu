package com.inmc.menu.settings

import com.inmc.menu.Hub
import kr.inmc.core.integration.PlayerSettings
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * "다른 플레이어 숨기기" — **관리자가 정한 월드(`hide-players-worlds`)에 있는 동안만** 다른 플레이어를 안 보이게 한다(`hidePlayer` — 탭 목록에서도).
 *
 * 맞출 때: 그 사람이 들어왔을 때·월드를 옮겼을 때·설정을 바꿨을 때([refreshViewer]), 그리고 **다른 사람이** 들어오거나 옮겼을 때 —
 * 숨기는 사람들이 새로 온 그 사람을 숨기도록([refreshTarget]).
 */
class Visibility(private val hub: Hub) {

    /** [viewer] 가 지금 남을 숨겨야 하는가. */
    fun hides(viewer: Player): Boolean =
        viewer.world.name in hub.config.hideWorlds && PlayerSettings.enabled(viewer, MenuSettings.HIDE_PLAYERS, false)

    /** [viewer] 의 눈에서 모두를 맞춘다. */
    fun refreshViewer(viewer: Player) {
        val hide = hides(viewer)
        for (other in Bukkit.getOnlinePlayers()) {
            if (other == viewer) continue
            if (hide) viewer.hidePlayer(hub.plugin, other) else viewer.showPlayer(hub.plugin, other)
        }
    }

    /** 모두의 눈에서 [target] 을 맞춘다. */
    fun refreshTarget(target: Player) {
        for (viewer in Bukkit.getOnlinePlayers()) {
            if (viewer == target) continue
            if (hides(viewer)) viewer.hidePlayer(hub.plugin, target) else viewer.showPlayer(hub.plugin, target)
        }
    }

    /** 숨기는 월드 목록이 바뀌었을 때 — 모두. */
    fun refreshAll() {
        for (player in Bukkit.getOnlinePlayers()) refreshViewer(player)
    }

    /** 플러그인이 내려갈 때 — 우리가 숨긴 것을 다 되돌린다(안 그러면 다시 켜질 때까지 안 보인다). */
    fun showAll() {
        val players = Bukkit.getOnlinePlayers()
        for (viewer in players) for (other in players) if (other != viewer) viewer.showPlayer(hub.plugin, other)
    }
}
