package com.inmc.menu.listener

import com.inmc.menu.Hub
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent

/**
 * 이동 대기(랜덤 TP 카운트다운 · `/스폰` 대기) 중 — 움직이거나(블록이 바뀌게) 맞으면 취소하고 알린다. 다른 이동·사망·나가기는 조용히 거둔다.
 * 우리 이동(랜덤 TP 의 `TELEPORTING`)은 서비스가 무시한다. 기다리는 사람이 없으면 이동 사건은 맵 두 번 보고 끝난다.
 */
class WarmupListener(private val hub: Hub) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return
        val id = event.player.uniqueId
        if (hub.rtp.inProgress(id)) hub.rtp.cancel(id, "rtp-cancelled-move")
        if (hub.spawn.waiting(id)) hub.spawn.cancel(id, "spawn-cancelled-move")
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val id = (event.entity as? Player ?: return).uniqueId
        if (hub.rtp.inProgress(id)) hub.rtp.cancel(id, "rtp-cancelled-damage")
        if (hub.spawn.waiting(id)) hub.spawn.cancel(id, "spawn-cancelled-damage")
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) = quietly(event.player.uniqueId)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorld(event: PlayerChangedWorldEvent) = quietly(event.player.uniqueId)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: PlayerDeathEvent) = quietly(event.player.uniqueId)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = quietly(event.player.uniqueId)

    private fun quietly(id: java.util.UUID) {
        hub.rtp.cancel(id, null)
        hub.spawn.cancel(id, null)
    }
}
