package com.inmc.menu.rtp

import com.inmc.menu.Hub
import com.inmc.menu.util.Ph
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import kr.inmc.core.CorePlugin
import kr.inmc.core.economy.Currencies
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 랜덤 TP — **누르는 순간 새 무작위 자리를 정하고, 찾는 일은 3·2·1 카운트다운 뒤에 숨긴다.** 미리 찾아 둔 자리는 쓰지 않는다
 * (사용자 검토 2026-10-02: 이미 밝혀진 청크만 가는 것은 랜덤 TP 가 아니다). 아무도 안 누르면 아무 일도 안 한다.
 *
 * 한 번의 찾기:
 * 1. **거르기(워커)** — 고리 안 무작위 좌표 → 월드 경계 → 실패 구역([ChunkMarks]) → 생물군계(청크 없이 계산 — `vanillaBiomeProvider`).
 *    통과한 후보 몇 개를 메인으로.
 * 2. **지역(메인)** — WorldGuard·Lands([Regions]) — 청크를 읽지 않는 조회.
 * 3. **청크(비동기)** — `getChunkAtAsync(gen, urgent)`. 메인 스레드 청크 I/O·생성 없음.
 * 4. **확인(메인)** — 블록 몇 개([Spots]). 안 되면 그 청크를 실패 구역에 적고 다음 후보. [MenuConfig.Rtp.chunkTries] 번 넘으면 실패.
 * 5. **붙잡기** — 찾은 청크에 표를 붙여 카운트다운 동안 내려가지 않게. 0 이 되면 그 틱에 `teleportAsync`(이미 올라와 있다).
 *
 * 연타·부하: 사람마다 찾기는 하나(그동안 누르면 "이동 준비 중"). **서버 전체 동시 찾기 수 상한**([Slots]) — 넘으면 줄. 취소한 찾기도
 * 읽던 청크가 다 올라올 때까지 자리를 잡고 있다(움직였다 다시 누르기로 상한을 넘지 못하게). 평균 틱이 무거우면 새 찾기를 받지 않는다.
 * 비용은 이동하는 순간 받고(못 움직이면 돌려준다) 쿨타임은 이동한 뒤에만 — 실패·취소는 아무것도 안 쓴다.
 */
class RtpService(private val hub: Hub) {

    class Search(val player: UUID, val settings: RtpWorld, val world: World, val bypass: Boolean) {

        enum class State { WAITING, SEARCHING, FOUND, TELEPORTING, DONE }

        var state = State.WAITING
        var holdsSlot = false

        /** 워커 거르기나 청크 읽기가 아직 돌아오지 않았다. */
        var inFlight = false
        val createdAt = System.nanoTime()
        var startedAt = 0L
        val pending = ArrayDeque<IntArray>()
        var sampled = 0
        var chunkLoads = 0
        var spot: Location? = null
        var ticket: IntArray? = null
        var remaining = settings.countdown
        var countdownDone = false
        var task: ScheduledTask? = null

        val done: Boolean get() = state == State.DONE
    }

    private val active = HashMap<UUID, Search>()
    private val slots = Slots<Search>(2, 10)
    val stats = RtpStats()
    val regions = Regions(hub.logger)
    private val marks = HashMap<String, ChunkMarks>()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "inmc-menu-rtp").apply { isDaemon = true } }

    /** 끄는 중 — 꺼진 플러그인은 일을 예약할 수 없다(표는 [shutdown] 이 한꺼번에 뗀다). */
    private var closing = false

    @Volatile
    private var biomeBroken = false

    fun inProgress(player: UUID): Boolean = player in active

    val searching: Int get() = slots.runningCount
    val waiting: Int get() = slots.waitingCount

    // --- 누르기 -------------------------------------------------------------------------------

    fun request(player: Player, settings: RtpWorld) {
        if (!settings.enabled) return hub.messages.send(player, "rtp-disabled")
        val world = Bukkit.getWorld(settings.world) ?: return hub.messages.send(player, "rtp-no-world", Ph.of().world(settings.world))
        if (!player.hasPermission(Hub.RTP) || (settings.permission.isNotBlank() && !player.hasPermission(settings.permission))) {
            return hub.messages.send(player, "no-permission")
        }
        if (player.uniqueId in active) return hub.messages.send(player, "rtp-in-progress")
        val bypass = player.hasPermission(Hub.RTP_BYPASS)
        if (!bypass) {
            val left = cooldownLeft(player.uniqueId, settings)
            if (left > 0) return hub.messages.send(player, "rtp-cooldown", Ph.of().time(Durations.format(left)))
            if (settings.cost > 0) {
                if (!costAvailable(settings)) return hub.messages.send(player, "rtp-no-currency")
                if (!hasCost(player, settings)) return hub.messages.send(player, "rtp-no-money", Ph.of().amount(formatCost(settings)))
            }
        }
        val busy = hub.config.rtp.busyMspt
        if (busy > 0 && Bukkit.getAverageTickTime() > busy) return hub.messages.send(player, "rtp-busy")

        val search = Search(player.uniqueId, settings, world, bypass)
        slots.limit = hub.config.rtp.maxSearches
        slots.queueLimit = hub.config.rtp.queueSize
        when (slots.offer(search)) {
            Slots.Offer.Full -> return hub.messages.send(player, "rtp-queue-full")
            Slots.Offer.Run -> search.holdsSlot = true
            is Slots.Offer.Queued -> Unit
        }
        active[player.uniqueId] = search
        stats.of(settings.world).requests++
        player.closeInventory()
        search.task = player.scheduler.runAtFixedRate(hub.plugin, { countdown(search) }, { abandon(search) }, 1L, 20L)
        if (search.holdsSlot) begin(search)
    }

    // --- 카운트다운 ---------------------------------------------------------------------------

    private fun countdown(search: Search) {
        val player = Bukkit.getPlayer(search.player)
        if (search.done || player == null) {
            search.task?.cancel()
            if (!search.done) abandon(search)
            return
        }
        if (System.nanoTime() - search.createdAt > TimeUnit.SECONDS.toNanos((search.settings.countdown + TIMEOUT_SECONDS).toLong())) {
            fail(search)
            return
        }
        if (search.remaining > 0) {
            val position = slots.position(search)
            val ph = Ph.of().count(search.remaining)
            val subtitle = if (position > 0) hub.messages.component("rtp-subtitle-queued", Ph.of().count(position), player)
            else hub.messages.component("rtp-subtitle", ph, player)
            player.showTitle(Title.title(hub.messages.component("rtp-title", ph, player), subtitle, TIMES))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 0.8f + 0.2f * (3 - search.remaining.coerceAtMost(3)))
            search.remaining--
            return
        }
        search.countdownDone = true
        if (search.state == Search.State.FOUND) teleport(search)
        else player.sendActionBar(hub.messages.component("rtp-searching", null, player))
    }

    // --- 찾기 ---------------------------------------------------------------------------------

    private fun begin(search: Search) {
        if (search.done) return
        search.state = Search.State.SEARCHING
        search.startedAt = System.nanoTime()
        filter(search)
    }

    /** 1. 거르기 — 워커에서. 경계·생물군계 계산에 필요한 것은 메인에서 떠 간다. */
    private fun filter(search: Search) {
        val settings = search.settings
        val world = search.world
        val border = world.worldBorder
        val borderX = border.center.x
        val borderZ = border.center.z
        val borderHalf = border.size / 2 - 16
        val biomes = settings.avoidBiomes.toSet()
        // 직접 만든 생성기(공허·스카이블록 …)는 바닐라 생물군계와 맞지 않는다 — 생물군계 거르기를 건너뛴다.
        val provider = if (biomes.isNotEmpty() && world.generator == null && !biomeBroken) runCatching { world.vanillaBiomeProvider() }.getOrNull() else null
        val probeY = if (world.environment == World.Environment.NORMAL) world.seaLevel else 64
        val failed = marksOf(world)
        search.inFlight = true
        worker.execute {
            val picked = ArrayList<IntArray>(CANDIDATES)
            var tries = 0
            val result = runCatching {
                while (picked.size < CANDIDATES && tries < SAMPLES_PER_ROUND) {
                    tries++
                    val (x, z) = Ring.pick(settings.shape, settings.centerX, settings.centerZ, settings.min, settings.max, Random.Default)
                    if (kotlin.math.abs(x - borderX) > borderHalf || kotlin.math.abs(z - borderZ) > borderHalf) continue
                    if (failed.has(x shr 4, z shr 4)) continue
                    if (provider != null && !biomeBroken && avoided(provider, world, x, probeY, z, biomes)) continue
                    picked += intArrayOf(x, z)
                }
                picked
            }
            Bukkit.getGlobalRegionScheduler().run(hub.plugin) {
                search.inFlight = false
                search.sampled += tries
                if (search.done) return@run releaseSlot(search)
                result.exceptionOrNull()?.let { hub.logger.warning("랜덤 TP 거르기 실패: ${it.javaClass.simpleName} ${it.message}") }
                // 2. 지역 — 메인에서(바다 높이로 먼저 — 정확한 높이는 찾은 뒤 한 번 더).
                for (xz in result.getOrDefault(emptyList())) {
                    if (!regions.claimed(Location(world, xz[0].toDouble(), probeY.toDouble(), xz[1].toDouble()))) search.pending.addLast(xz)
                }
                next(search)
            }
        }
    }

    /** 생물군계 계산 — 워커에서. 한 번이라도 던지면 끈다(그 뒤로는 청크를 읽어 보고 판단 — 느려질 뿐 틀리지 않는다). */
    private fun avoided(provider: org.bukkit.generator.BiomeProvider, world: World, x: Int, y: Int, z: Int, biomes: Set<String>): Boolean =
        try {
            provider.getBiome(world, x, y, z).key().asString() in biomes
        } catch (t: Throwable) {
            biomeBroken = true
            hub.logger.warning("생물군계를 청크 없이 계산하지 못했습니다 — 랜덤 TP 가 생물군계 거르기를 끕니다: ${t.javaClass.simpleName} ${t.message}")
            false
        }

    /** 3. 다음 후보의 청크를 읽는다. 후보가 없으면 다시 거르고, 너무 많이 돌았으면 실패. */
    private fun next(search: Search) {
        if (search.done) return releaseSlot(search)
        if (search.chunkLoads >= hub.config.rtp.chunkTries || search.sampled >= MAX_SAMPLES) return fail(search)
        val xz = search.pending.removeFirstOrNull() ?: return filter(search)
        val cx = xz[0] shr 4
        val cz = xz[1] shr 4
        search.chunkLoads++
        stats.of(search.settings.world).chunkLoads++
        search.inFlight = true
        search.world.getChunkAtAsync(cx, cz, true, true) { _ ->
            search.inFlight = false
            if (search.done) return@getChunkAtAsync releaseSlot(search)
            // 4. 확인 — 블록 몇 개.
            val spot = Spots.find(search.world, cx, cz, xz[0] to xz[1], Random.Default)
            when {
                spot == null -> {
                    marksOf(search.world).mark(cx, cz)
                    next(search)
                }
                regions.claimed(spot) -> next(search)
                else -> found(search, spot, cx, cz)
            }
        }
    }

    /** 5. 찾았다 — 청크를 붙잡고 자리를 다음 사람에게. 카운트다운이 끝났으면 바로 이동. */
    private fun found(search: Search, spot: Location, cx: Int, cz: Int) {
        search.world.addPluginChunkTicket(cx, cz, hub.plugin)
        search.ticket = intArrayOf(cx, cz)
        search.spot = spot
        search.state = Search.State.FOUND
        stats.found(search.settings.world, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - search.startedAt))
        releaseSlot(search)
        if (search.countdownDone) teleport(search)
    }

    private fun teleport(search: Search) {
        val player = Bukkit.getPlayer(search.player) ?: return abandon(search)
        val spot = search.spot ?: return fail(search)
        if (!Spots.stillSafe(spot)) return fail(search)
        val charged = !search.bypass && search.settings.cost > 0
        if (charged && !takeCost(player, search.settings)) {
            finish(search)
            return hub.messages.send(player, "rtp-no-money", Ph.of().amount(formatCost(search.settings)))
        }
        search.state = Search.State.TELEPORTING
        val target = spot.clone().apply {
            yaw = player.location.yaw
            pitch = 0f
        }
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept { ok ->
            Bukkit.getGlobalRegionScheduler().run(hub.plugin) {
                val online = Bukkit.getPlayer(search.player)
                if (ok) {
                    if (!search.bypass) CorePlugin.get().players.set(search.player, Hub.STORE, cooldownKey(search.settings), System.currentTimeMillis())
                    stats.of(search.settings.world).success++
                    online?.let {
                        it.showTitle(Title.title(hub.messages.component("rtp-go-title", null, it), net.kyori.adventure.text.Component.empty(), TIMES))
                        it.playSound(it.location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f)
                        hub.messages.send(it, "rtp-done", Ph.of().world(search.world.name).value("${spot.blockX}, ${spot.blockY}, ${spot.blockZ}"))
                    }
                } else {
                    if (charged && online != null) refundCost(online, search.settings)
                    stats.of(search.settings.world).failed++
                    online?.let { hub.messages.send(it, "rtp-failed") }
                }
                finish(search)
            }
        }
    }

    // --- 끝내기 -------------------------------------------------------------------------------

    /** 움직였다·맞았다·나갔다 … — [reason] 이 있으면 알린다. */
    fun cancel(player: UUID, reason: String?) {
        val search = active[player] ?: return
        if (search.state == Search.State.TELEPORTING) return
        stats.of(search.settings.world).cancelled++
        finish(search)
        if (reason != null) Bukkit.getPlayer(player)?.let { hub.messages.send(it, reason) }
    }

    private fun abandon(search: Search) {
        if (search.done) return
        stats.of(search.settings.world).cancelled++
        finish(search)
    }

    private fun fail(search: Search) {
        if (search.done) return
        stats.of(search.settings.world).failed++
        finish(search)
        Bukkit.getPlayer(search.player)?.let { hub.messages.send(it, "rtp-failed") }
    }

    private fun finish(search: Search) {
        if (search.done) return
        search.state = Search.State.DONE
        if (active[search.player] === search) active.remove(search.player)
        search.task?.cancel()
        search.ticket?.takeUnless { closing }?.let { (cx, cz) ->
            search.ticket = null
            // 이동한 사람이 이제 그 청크를 붙잡고 있다 — 한 틱 뒤에 뗀다.
            Bukkit.getGlobalRegionScheduler().runDelayed(hub.plugin, { search.world.removePluginChunkTicket(cx, cz, hub.plugin) }, 2L)
        }
        // 읽던 청크가 아직 오는 중이면 그때 자리를 비운다(취소로 상한을 넘지 못하게).
        if (!search.inFlight) releaseSlot(search)
    }

    private fun releaseSlot(search: Search) {
        val wasHolding = search.holdsSlot
        search.holdsSlot = false
        val promoted = slots.release(search)
        if (closing || (!wasHolding && promoted.isEmpty())) return
        for (next in promoted) {
            next.holdsSlot = true
            begin(next)
        }
    }

    // --- 쿨타임 · 비용 ----------------------------------------------------------------------------

    fun cooldownLeft(player: UUID, settings: RtpWorld): Long {
        if (settings.cooldown <= 0) return 0
        val last = CorePlugin.get().players.getLong(player, Hub.STORE, cooldownKey(settings))
        return ((last + settings.cooldown * 1000L - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    }

    private fun cooldownKey(settings: RtpWorld): String = "rtp-" + settings.world.replace('.', '_')

    /** 빈 화폐 id = 기본 화폐(없으면 Vault). */
    private fun costAvailable(settings: RtpWorld): Boolean =
        if (settings.currency.isBlank()) hub.economy.isEnabled else Currencies.get(settings.currency) != null

    private fun hasCost(player: Player, settings: RtpWorld): Boolean =
        if (settings.currency.isBlank()) hub.economy.has(player, settings.cost.toDouble())
        else Currencies.get(settings.currency)?.has(player, settings.cost) == true

    private fun takeCost(player: Player, settings: RtpWorld): Boolean =
        if (settings.currency.isBlank()) hub.economy.withdraw(player, settings.cost.toDouble())
        else Currencies.get(settings.currency)?.withdraw(player, settings.cost, REASON) == true

    private fun refundCost(player: Player, settings: RtpWorld) {
        if (settings.currency.isBlank()) hub.economy.deposit(player, settings.cost.toDouble())
        else Currencies.get(settings.currency)?.deposit(player, settings.cost, "$REASON-refund")
    }

    fun formatCost(settings: RtpWorld): String =
        if (settings.currency.isBlank()) hub.economy.format(settings.cost.toDouble())
        else Currencies.get(settings.currency)?.format(settings.cost) ?: settings.cost.toString()

    // --- 실패 구역 ----------------------------------------------------------------------------

    private fun marksOf(world: World): ChunkMarks =
        marks.getOrPut(world.name) { ChunkMarks.read(marksFile(world.name), world.seed) }

    fun marksCount(world: String): Int = Bukkit.getWorld(world)?.let { marksOf(it).count() } ?: 0

    fun clearMarks(world: String) {
        val loaded = Bukkit.getWorld(world)
        if (loaded != null) marksOf(loaded).clear() else marksFile(world).delete()
        saveMarks()
    }

    private fun marksFile(world: String) = hub.io.file("rtp", world.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".bad")

    /** 바뀐 실패 구역만 워커에서 쓴다(틱마다 부르지 않는다 — Ticker 가 몇 분마다). */
    fun saveMarks() {
        for ((name, map) in marks) {
            if (!map.dirty) continue
            val seed = Bukkit.getWorld(name)?.seed ?: continue
            val file = marksFile(name)
            hub.io.asyncRun { map.write(file, seed) }
        }
    }

    /** 끌 때 — 찾던 것을 모두 거두고 표를 떼고 실패 구역을 그 자리에서 쓴다. */
    fun shutdown() {
        closing = true
        for (search in active.values.toList()) finish(search)
        worker.shutdownNow()
        for (world in Bukkit.getWorlds()) world.removePluginChunkTickets(hub.plugin)
        for ((name, map) in marks) {
            if (!map.dirty) continue
            val seed = Bukkit.getWorld(name)?.seed ?: continue
            runCatching { map.write(marksFile(name), seed) }.onFailure { hub.logger.warning("실패 구역을 쓰지 못했습니다($name): ${it.message}") }
        }
    }

    companion object {
        private const val REASON = "inmc-menu:rtp"

        /** 거르기 한 번에 넘길 후보 수 · 그 한 번에 뽑아 볼 좌표 수 · 찾기 하나에 뽑아 볼 좌표 수. */
        private const val CANDIDATES = 6
        private const val SAMPLES_PER_ROUND = 256
        private const val MAX_SAMPLES = 4096

        /** 카운트다운 뒤에도 이만큼(초) 못 찾으면 실패. */
        private const val TIMEOUT_SECONDS = 20

        private val TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(250))
    }
}
