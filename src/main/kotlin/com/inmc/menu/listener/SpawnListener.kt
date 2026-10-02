package com.inmc.menu.listener

import com.inmc.menu.Hub
import com.inmc.menu.spawn.Source
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent

/**
 * 접속할 때 서버 스폰으로 · 죽었을 때 부활 순서.
 *
 * - 접속: `AsyncPlayerSpawnLocationEvent` — 들어오기 **전에** 자리를 정한다(들어온 뒤 순간이동이 보이지 않게). 비동기 사건이라 설정을 읽기만 한다.
 * - 부활: HIGH 에서 순서대로 처음 되는 곳. 침대·앵커는 그 부활이 침대·앵커일 때 그대로 둔다. **엔드 귀환(END_PORTAL)은 건드리지 않는다.**
 *   다른 플러그인(CMI …)이 HIGHEST 에서 또 고치면 그쪽이 이긴다 — 그 플러그인의 부활 처리를 끄도록 안내한다.
 */
class SpawnListener(private val hub: Hub) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onSpawnLocation(event: AsyncPlayerSpawnLocationEvent) {
        val c = hub.spawn.config
        if (!c.everyJoin && !(c.firstJoin && event.isNewPlayer)) return
        val target = c.point?.location() ?: return
        event.spawnLocation = target
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onRespawn(event: PlayerRespawnEvent) {
        if (event.respawnReason == PlayerRespawnEvent.RespawnReason.END_PORTAL) return
        val c = hub.spawn.config
        val server = c.point?.location()
        when (c.choose(bed = event.isBedSpawn, anchor = event.isAnchorSpawn, hasServerSpawn = server != null)) {
            Source.BED, Source.ANCHOR, null -> Unit
            Source.SERVER -> event.respawnLocation = server!!
            Source.WORLD -> event.respawnLocation = hub.spawn.worldSpawn(event.player.world)
        }
    }
}
