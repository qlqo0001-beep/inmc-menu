package com.inmc.menu.rtp

import org.bukkit.Material
import org.bukkit.World

/**
 * 랜덤 TP 를 할 수 있는 월드 하나의 설정(`rtp-worlds.yml` 의 목록 한 줄 — 월드 이름에 점이 들 수 있어 열쇠로 쓰지 않는다, 지뢰 1).
 * 목록 순서가 고르기 화면의 순서.
 */
data class RtpWorld(
    val world: String,
    val enabled: Boolean = false,
    /** 고르기 화면의 이름(MiniMessage). */
    val name: String = world,
    val icon: Material = Material.GRASS_BLOCK,
    val centerX: Int = 0,
    val centerZ: Int = 0,
    /** 중심에서 최소·최대 거리(블록). */
    val min: Int = 500,
    val max: Int = 5000,
    val shape: Shape = Shape.CIRCLE,
    /** 이동한 뒤 다시 쓰기까지(초). */
    val cooldown: Int = 60,
    /** 비용의 화폐 id(빈 칸 = 기본 화폐) · 금액(0 = 무료). */
    val currency: String = "",
    val cost: Long = 0,
    /** 비우면 누구나(`inmcmenu.rtp` 만). */
    val permission: String = "",
    /** 3·2·1 — 그동안 자리를 찾는다(0~5초). */
    val countdown: Int = 3,
    /** 피할 생물군계(`minecraft:ocean` …) — 청크를 읽기 전에 계산으로 거른다. */
    val avoidBiomes: List<String> = emptyList(),
) {

    /** 저장할 때 값의 범위를 맞춘다. */
    fun sanitized(): RtpWorld {
        val outer = max.coerceIn(MIN_RANGE, MAX_RANGE)
        return copy(
            max = outer,
            min = min.coerceIn(0, outer - MIN_RANGE),
            cooldown = cooldown.coerceIn(0, 86_400),
            cost = cost.coerceAtLeast(0),
            countdown = countdown.coerceIn(0, 5),
            avoidBiomes = avoidBiomes.map { it.trim().lowercase() }.filter(String::isNotEmpty).map { if (':' in it) it else "minecraft:$it" }.distinct(),
        )
    }

    fun save(): Map<String, Any> = linkedMapOf(
        "world" to world,
        "enabled" to enabled,
        "name" to name,
        "icon" to icon.name,
        "center-x" to centerX,
        "center-z" to centerZ,
        "min" to min,
        "max" to max,
        "shape" to shape.name.lowercase(),
        "cooldown" to cooldown,
        "currency" to currency,
        "cost" to cost,
        "permission" to permission,
        "countdown" to countdown,
        "avoid-biomes" to avoidBiomes,
    )

    companion object {

        const val MIN_RANGE = 16
        const val MAX_RANGE = 1_000_000

        fun load(map: Map<*, *>): RtpWorld? {
            val world = map["world"]?.toString()?.trim().orEmpty().ifEmpty { return null }
            fun int(key: String, fallback: Int) = (map[key] as? Number)?.toInt() ?: map[key]?.toString()?.toIntOrNull() ?: fallback
            val base = RtpWorld(world)
            return RtpWorld(
                world = world,
                enabled = map["enabled"] as? Boolean ?: base.enabled,
                name = map["name"]?.toString() ?: base.name,
                icon = map["icon"]?.toString()?.let { Material.matchMaterial(it) } ?: base.icon,
                centerX = int("center-x", 0),
                centerZ = int("center-z", 0),
                min = int("min", base.min),
                max = int("max", base.max),
                shape = Shape.entries.firstOrNull { it.name.equals(map["shape"]?.toString(), ignoreCase = true) } ?: Shape.CIRCLE,
                cooldown = int("cooldown", base.cooldown),
                currency = map["currency"]?.toString().orEmpty(),
                cost = (map["cost"] as? Number)?.toLong() ?: map["cost"]?.toString()?.toLongOrNull() ?: 0,
                permission = map["permission"]?.toString().orEmpty(),
                countdown = int("countdown", base.countdown),
                avoidBiomes = (map["avoid-biomes"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            ).sanitized()
        }

        /** 처음 보는 월드의 기본값 — 환경마다 그림·피할 생물군계. 오버월드 하나만 켠다(처음 깔 때). */
        fun defaultsFor(world: String, environment: World.Environment, enabled: Boolean): RtpWorld = when (environment) {
            World.Environment.NETHER -> RtpWorld(world, enabled, "<red>네더</red>", Material.NETHERRACK, min = 300, max = 3000, countdown = 3)
            World.Environment.THE_END -> RtpWorld(world, enabled, "<light_purple>엔드</light_purple>", Material.END_STONE, min = 1000, max = 5000,
                avoidBiomes = listOf("minecraft:the_end", "minecraft:small_end_islands", "minecraft:the_void"))
            else -> RtpWorld(world, enabled, "<green>야생</green>", Material.GRASS_BLOCK, avoidBiomes = OCEANS)
        }

        val OCEANS = listOf(
            "minecraft:ocean", "minecraft:deep_ocean", "minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean",
            "minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
            "minecraft:river", "minecraft:frozen_river", "minecraft:the_void",
        )
    }
}
