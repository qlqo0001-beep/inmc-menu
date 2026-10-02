package com.inmc.menu.spawn

import com.inmc.menu.Hub
import com.inmc.menu.util.Ph
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import kr.inmc.core.CorePlugin
import kr.inmc.core.util.Durations
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import java.time.Duration
import java.util.UUID

/**
 * 스폰 — 서버 스폰 자리, `/스폰`(이동 대기 — 움직이거나 맞으면 취소, 쿨타임), 접속할 때 보내기, 부활 순서.
 * 설정은 메모리 한 벌(`@Volatile` — 접속 사건은 비동기라 읽기만 한다), 고치면 워커에서 파일로.
 */
class SpawnService(private val hub: Hub) {

    @Volatile
    var config: SpawnConfig = SpawnConfig()
        private set

    private val file get() = hub.io.file(FILE)

    /** `/스폰` 대기 중인 사람 → 남은 초와 그 작업. */
    private val waiting = HashMap<UUID, Pair<IntArray, ScheduledTask>>()

    fun loadNow() {
        config = if (file.isFile) SpawnConfig.from(hub.io.load(file)) else SpawnConfig().also { save(it) }
    }

    /** 검증기 — 파일에 쓰지 않고 잠깐 바꿨다가 되돌린다. */
    fun swap(value: SpawnConfig) {
        config = value
    }

    fun update(change: (SpawnConfig) -> SpawnConfig) {
        config = change(config)
        save(config)
    }

    private fun save(value: SpawnConfig) {
        val yaml = value.toYaml()
        hub.io.asyncRun { hub.io.save(file, yaml) }
    }

    /** 서버 스폰 — 정하지 않았거나 그 월드가 없으면 기본 월드의 스폰. */
    fun target(): Location = config.point?.location() ?: worldSpawn(Bukkit.getWorlds().first())

    /** 월드 스폰 — 블록 가운데. 네더·엔드면 기본 월드의 것. */
    fun worldSpawn(world: World): Location {
        val base = if (world.environment == World.Environment.NORMAL) world else Bukkit.getWorlds().first()
        return base.spawnLocation.toCenterLocation().apply { y = base.spawnLocation.y }
    }

    // --- /스폰 --------------------------------------------------------------------------------

    fun waiting(player: UUID): Boolean = player in waiting

    fun request(player: Player) {
        if (!player.hasPermission(Hub.SPAWN)) return hub.messages.send(player, "no-permission")
        if (player.uniqueId in waiting) return
        val c = config
        val left = cooldownLeft(player.uniqueId, c)
        if (left > 0) return hub.messages.send(player, "spawn-cooldown", Ph.of().time(Durations.format(left)))
        player.closeInventory()
        if (c.wait <= 0) return go(player)
        val remaining = intArrayOf(c.wait)
        val task = player.scheduler.runAtFixedRate(hub.plugin, { task ->
            val online = Bukkit.getPlayer(player.uniqueId)
            if (online == null || waiting[player.uniqueId]?.second !== task) {
                task.cancel()
                return@runAtFixedRate
            }
            if (remaining[0] <= 0) {
                waiting.remove(player.uniqueId)
                task.cancel()
                go(online)
                return@runAtFixedRate
            }
            online.showTitle(Title.title(hub.messages.component("spawn-title", Ph.of().count(remaining[0]), online),
                hub.messages.component("spawn-subtitle", null, online), TIMES))
            online.playSound(online.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.0f)
            remaining[0]--
        }, { waiting.remove(player.uniqueId) }, 1L, 20L) ?: return
        waiting[player.uniqueId] = remaining to task
    }

    fun cancel(player: UUID, reason: String?) {
        val (_, task) = waiting.remove(player) ?: return
        task.cancel()
        if (reason != null) Bukkit.getPlayer(player)?.let { hub.messages.send(it, reason) }
    }

    private fun go(player: Player) {
        player.teleportAsync(target(), PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept { ok ->
            if (!ok) return@thenAccept
            CorePlugin.get().players.set(player.uniqueId, Hub.STORE, COOLDOWN_KEY, System.currentTimeMillis())
            hub.messages.send(player, "spawn-done")
        }
    }

    private fun cooldownLeft(player: UUID, c: SpawnConfig): Long {
        if (c.cooldown <= 0) return 0
        val last = CorePlugin.get().players.getLong(player, Hub.STORE, COOLDOWN_KEY)
        return ((last + c.cooldown * 1000L - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    }

    fun shutdown() {
        for ((_, task) in waiting.values) task.cancel()
        waiting.clear()
    }

    companion object {
        const val FILE = "spawn.yml"
        const val COOLDOWN_KEY = "spawn"
        private val TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(250))
    }
}
