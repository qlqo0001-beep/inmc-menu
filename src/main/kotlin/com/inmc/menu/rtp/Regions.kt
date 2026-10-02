package com.inmc.menu.rtp

import kr.inmc.core.integration.PluginClasses
import org.bukkit.Location
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.util.logging.Logger

/**
 * 랜덤 TP 가 피하는 곳 — WorldGuard 지역·Lands 땅. **청크를 읽지 않는 조회**만 쓴다(지역 목록·땅 표를 본다).
 *
 * WorldGuard 는 컴파일 의존(오래 안정된 API), Lands 는 리플렉션(판마다 이름이 바뀌었다 — 랜덤박스 `RegionHook`·상점 `LandsHook` 과 같은 길).
 * 조회가 한 번 실패하면 그 연동은 끄고 경고를 남긴다(찾기마다 예외가 쌓이지 않게).
 */
class Regions(private val logger: Logger) {

    @Volatile
    private var worldGuard = false

    @Volatile
    private var lands: Any? = null

    @Volatile
    private var landsGetArea: Method? = null

    fun setup(plugin: Plugin) {
        worldGuard = false
        lands = null
        landsGetArea = null
        if (PluginClasses.isEnabled("WorldGuard")) {
            worldGuard = runCatching {
                PluginClasses.require("WorldGuard", "com.sk89q.worldguard.WorldGuard")
                true
            }.getOrElse {
                logger.warning("WorldGuard 연동 실패 — 랜덤 TP 가 지역을 피하지 않습니다: ${it.message}")
                false
            }
        }
        if (PluginClasses.isEnabled("Lands")) {
            runCatching {
                val type = PluginClasses.require("Lands", "me.angeschossen.lands.api.LandsIntegration")
                val api = type.methods.first { it.name == "of" && it.parameterCount == 1 }.invoke(null, plugin)
                landsGetArea = type.methods.first { it.name == "getArea" && it.parameterTypes.contentEquals(arrayOf(Location::class.java)) }
                lands = api
            }.onFailure { logger.warning("Lands 연동 실패 — 랜덤 TP 가 땅을 피하지 않습니다: ${it.message}") }
        }
    }

    /** 누군가의 지역·땅 안인가. */
    fun claimed(location: Location): Boolean = inWorldGuard(location) || inLands(location)

    private fun inWorldGuard(location: Location): Boolean {
        if (!worldGuard) return false
        return try {
            val world = location.world ?: return false
            val manager = com.sk89q.worldguard.WorldGuard.getInstance().platform.regionContainer
                .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world)) ?: return false
            val point = com.sk89q.worldedit.math.BlockVector3.at(location.blockX, location.blockY, location.blockZ)
            manager.getApplicableRegions(point).regions.any { it.id != GLOBAL }
        } catch (t: Throwable) {
            worldGuard = false
            logger.warning("WorldGuard 지역 조회 실패 — 랜덤 TP 가 지역을 피하지 않습니다: ${t.javaClass.simpleName} ${t.message}")
            false
        }
    }

    private fun inLands(location: Location): Boolean {
        val api = lands ?: return false
        return try {
            landsGetArea?.invoke(api, location) != null
        } catch (t: Throwable) {
            lands = null
            logger.warning("Lands 땅 조회 실패 — 랜덤 TP 가 땅을 피하지 않습니다: ${t.javaClass.simpleName} ${t.message}")
            false
        }
    }

    private companion object {
        const val GLOBAL = "__global__"
    }
}
