package com.inmc.menu.spawn

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration

/** 부활 위치 후보. */
enum class Source(val label: String, val description: String) {
    BED("침대", "그 사람이 잔 침대가 남아 있으면"),
    ANCHOR("리스폰 앵커", "충전된 앵커가 남아 있으면"),
    SERVER("서버 스폰", "/스폰 설정 으로 정한 곳"),
    WORLD("월드 스폰", "죽은 월드의 스폰(네더·엔드면 기본 월드)"),
}

/** 부활 순서의 한 칸 — 끈 것은 건너뛴다. */
data class Step(val source: Source, val enabled: Boolean = true)

/** 서버 스폰 자리 — 월드는 이름으로(쓸 때 찾는다 — 월드가 늦게 열려도). */
data class SpawnPoint(val world: String, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) {

    fun location(): Location? = Bukkit.getWorld(world)?.let { Location(it, x, y, z, yaw, pitch) }

    fun describe(): String = "$world ${x.toInt()}, ${y.toInt()}, ${z.toInt()}"

    companion object {
        fun of(location: Location): SpawnPoint =
            SpawnPoint(location.world.name, location.x, location.y, location.z, location.yaw, location.pitch)
    }
}

/**
 * `spawn.yml` — 서버 스폰·접속 때 보내기·부활 순서·`/스폰` 대기와 쿨타임. 화면에서 고치면 통째로 다시 쓴다.
 */
data class SpawnConfig(
    val point: SpawnPoint? = null,
    /** 처음 들어온 사람을 서버 스폰으로. */
    val firstJoin: Boolean = true,
    /** 들어올 때마다 서버 스폰으로. */
    val everyJoin: Boolean = false,
    val respawn: List<Step> = DEFAULT_ORDER,
    /** `/스폰` 이동 대기(초) — 그동안 움직이거나 맞으면 취소. */
    val wait: Int = 3,
    /** `/스폰` 쿨타임(초). */
    val cooldown: Int = 0,
) {

    /** 순서대로 처음 되는 것. 다 안 되면 null(바닐라 그대로). 순수 함수(`SpawnTest`). */
    fun choose(bed: Boolean, anchor: Boolean, hasServerSpawn: Boolean): Source? =
        respawn.filter { it.enabled }.firstOrNull { step ->
            when (step.source) {
                Source.BED -> bed
                Source.ANCHOR -> anchor
                Source.SERVER -> hasServerSpawn
                Source.WORLD -> true
            }
        }?.source

    /** [index] 칸을 [delta] 만큼 옮긴다(끝에서는 그대로). */
    fun move(index: Int, delta: Int): SpawnConfig {
        val target = index + delta
        if (index !in respawn.indices || target !in respawn.indices) return this
        val list = respawn.toMutableList()
        list.add(target, list.removeAt(index))
        return copy(respawn = list)
    }

    fun toggle(index: Int): SpawnConfig =
        copy(respawn = respawn.mapIndexed { i, step -> if (i == index) step.copy(enabled = !step.enabled) else step })

    fun toYaml(): YamlConfiguration = YamlConfiguration().also { y ->
        y.options().setHeader(HEADER.lines())
        point?.let { p ->
            y.set("spawn.world", p.world)
            y.set("spawn.x", p.x)
            y.set("spawn.y", p.y)
            y.set("spawn.z", p.z)
            y.set("spawn.yaw", p.yaw.toDouble())
            y.set("spawn.pitch", p.pitch.toDouble())
        }
        y.set("first-join", firstJoin)
        y.set("every-join", everyJoin)
        y.set("respawn-order", respawn.map { (if (it.enabled) "" else "-") + it.source.name.lowercase() })
        y.set("wait", wait)
        y.set("cooldown", cooldown)
    }

    companion object {

        val DEFAULT_ORDER = Source.entries.map { Step(it) }

        val HEADER = """
            inmc-menu 스폰. 게임 안 /스폰 관리 에서 고치는 것이 편합니다(고치면 이 파일을 다시 씁니다).
            respawn-order: 죽었을 때 위에서부터 처음 되는 곳으로. 앞에 - 를 붙이면 끈 것. 다 안 되면 바닐라 그대로.
              bed(침대) · anchor(리스폰 앵커) · server(서버 스폰) · world(죽은 월드의 스폰 — 네더·엔드면 기본 월드)
            CMI 같은 다른 플러그인의 스폰·부활 처리는 꺼 두세요 — 둘이 같은 사건을 고칩니다.
        """.trimIndent()

        fun from(y: YamlConfiguration): SpawnConfig {
            val point = y.getString("spawn.world")?.takeIf { it.isNotBlank() }?.let {
                SpawnPoint(it, y.getDouble("spawn.x"), y.getDouble("spawn.y"), y.getDouble("spawn.z"),
                    y.getDouble("spawn.yaw").toFloat(), y.getDouble("spawn.pitch").toFloat())
            }
            return SpawnConfig(
                point = point,
                firstJoin = y.getBoolean("first-join", true),
                everyJoin = y.getBoolean("every-join", false),
                respawn = parseOrder(y.getStringList("respawn-order")),
                wait = y.getInt("wait", 3).coerceIn(0, 10),
                cooldown = y.getInt("cooldown", 0).coerceIn(0, 86_400),
            )
        }

        /** 모르는 이름은 버리고, 빠진 것은 끈 채로 끝에 — 넷이 늘 다 있다. */
        fun parseOrder(raw: List<String>): List<Step> {
            if (raw.isEmpty()) return DEFAULT_ORDER
            val steps = raw.mapNotNull { entry ->
                val off = entry.trim().startsWith("-")
                Source.entries.firstOrNull { it.name.equals(entry.trim().removePrefix("-").trim(), ignoreCase = true) }?.let { Step(it, !off) }
            }.distinctBy { it.source }
            return steps + Source.entries.filter { s -> steps.none { it.source == s } }.map { Step(it, enabled = false) }
        }
    }
}
