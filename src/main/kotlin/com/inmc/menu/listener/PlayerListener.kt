package com.inmc.menu.listener

import com.inmc.menu.Hub
import com.inmc.menu.menu.Actions
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerResourcePackStatusEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/** 여는 길(Shift+F)·접속할 때 개인 설정 걸기·숨기기·리소스팩 상태. */
class PlayerListener(private val hub: Hub) : Listener {

    /**
     * 웅크리고 F. HIGH·`ignoreCancelled` — 낚시가 **낚싯대를 든 채** Shift+F 를 LOWEST 에서 가져가 취소하므로 그때는 여기 오지 않는다
     * (`inmc-fishing/…/listener/FishingListener.kt` `onSneakSwap`).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!hub.ready || !player.isSneaking || !hub.config.hotkey) return
        if (!player.hasPermission(Hub.USE) || !hub.settings.hotkeyOn(player)) return
        event.isCancelled = true
        Actions.openMain(hub, player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        hub.settings.apply(player)
        hub.visibility.refreshViewer(player)
        hub.visibility.refreshTarget(player)
        hub.attendance.onJoin(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorld(event: PlayerChangedWorldEvent) {
        hub.visibility.refreshViewer(event.player)
        hub.visibility.refreshTarget(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        hub.packLoaded.remove(event.player.uniqueId)
        hub.attendance.onQuit(event.player)
    }

    /** 다 받았으면 리소스팩 화면, 거절·실패면 일반 화면. 받는 중(ACCEPTED·DOWNLOADED)은 그대로 둔다. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPack(event: PlayerResourcePackStatusEvent) {
        when (event.status) {
            PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED -> hub.packLoaded[event.player.uniqueId] = System.currentTimeMillis()
            PlayerResourcePackStatusEvent.Status.DECLINED,
            PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD,
            PlayerResourcePackStatusEvent.Status.FAILED_RELOAD,
            PlayerResourcePackStatusEvent.Status.INVALID_URL,
            PlayerResourcePackStatusEvent.Status.DISCARDED,
            -> hub.packLoaded.remove(event.player.uniqueId)
            else -> Unit
        }
    }
}
