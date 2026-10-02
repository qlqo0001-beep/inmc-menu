package com.inmc.menu.rtp

import org.bukkit.HeightMap
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import java.util.EnumSet
import kotlin.random.Random

/**
 * 읽어 온 청크 안에서 설 자리 — **블록 몇 개만** 본다(메인 스레드, 청크가 이미 올라와 있다).
 *
 * - 오버월드·엔드: 높이맵(`MOTION_BLOCKING_NO_LEAVES` — 물도 막는 것으로 친다)으로 지면 y 를 바로 얻고 발밑·두 칸만.
 * - 네더: 천장(기반암) 아래 기둥을 위아래로 훑어 설 수 있는 높이들 가운데 하나(용암 바다 32 위부터).
 * 청크 안 기둥 [COLUMNS] 개(뽑은 자리 먼저)를 본다 — 한 청크에 물이 조금 있다고 버리지 않게.
 */
object Spots {

    const val COLUMNS = 4

    /** 발밑으로도, 몸이 들어갈 칸으로도 안 되는 것. */
    private val DANGER: Set<Material> = EnumSet.of(
        Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE, Material.SOUL_FIRE,
        Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW, Material.POINTED_DRIPSTONE, Material.WITHER_ROSE, Material.COBWEB, Material.BEDROCK,
    )

    /** 네더 용암 바다(31) 위부터. */
    private const val NETHER_FLOOR = 32

    fun find(world: World, chunkX: Int, chunkZ: Int, first: Pair<Int, Int>, random: Random): Location? {
        val columns = ArrayList<Pair<Int, Int>>(COLUMNS)
        columns += first
        repeat(COLUMNS - 1) { columns += ((chunkX shl 4) + random.nextInt(16)) to ((chunkZ shl 4) + random.nextInt(16)) }
        for ((x, z) in columns) {
            val y = standY(world, x, z, random) ?: continue
            return Location(world, x + 0.5, y.toDouble(), z + 0.5)
        }
        return null
    }

    /** 이동하기 직전에 한 번 더 — 카운트다운 동안 누가 블록을 바꿨을 수 있다. */
    fun stillSafe(location: Location): Boolean {
        val world = location.world ?: return false
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        return ground(world.getBlockAt(x, y - 1, z)) && room(world.getBlockAt(x, y, z)) && room(world.getBlockAt(x, y + 1, z))
    }

    /** 발이 놓일 y(땅 블록 + 1). 없으면 null. */
    private fun standY(world: World, x: Int, z: Int, random: Random): Int? {
        if (world.environment == World.Environment.NETHER) {
            val top = minOf(world.logicalHeight, world.maxHeight) - 3
            val options = (maxOf(NETHER_FLOOR, world.minHeight + 1)..top).filter { y ->
                ground(world.getBlockAt(x, y, z)) && room(world.getBlockAt(x, y + 1, z)) && room(world.getBlockAt(x, y + 2, z))
            }
            return if (options.isEmpty()) null else options[random.nextInt(options.size)] + 1
        }
        val y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES)
        if (y <= world.minHeight || y + 2 >= world.maxHeight) return null
        return if (ground(world.getBlockAt(x, y, z)) && room(world.getBlockAt(x, y + 1, z)) && room(world.getBlockAt(x, y + 2, z))) y + 1 else null
    }

    private fun ground(block: Block): Boolean = block.type.isSolid && !block.isLiquid && block.type !in DANGER

    private fun room(block: Block): Boolean = block.isPassable && !block.isLiquid && block.type !in DANGER
}
