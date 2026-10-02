package com.inmc.menu

import com.inmc.menu.spawn.Source
import com.inmc.menu.spawn.SpawnConfig
import com.inmc.menu.spawn.SpawnPoint
import com.inmc.menu.spawn.Step
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

/** 부활 순서와 스폰 설정 파일. */
class SpawnTest {

    @Test
    fun `부활 — 순서대로 처음 되는 곳`() {
        val c = SpawnConfig()
        assertEquals(Source.BED, c.choose(bed = true, anchor = false, hasServerSpawn = true))
        assertEquals(Source.ANCHOR, c.choose(bed = false, anchor = true, hasServerSpawn = true))
        assertEquals(Source.SERVER, c.choose(bed = false, anchor = false, hasServerSpawn = true))
        assertEquals(Source.WORLD, c.choose(bed = false, anchor = false, hasServerSpawn = false))

        // 서버 스폰을 맨 앞에 두면 침대가 있어도 서버 스폰.
        val serverFirst = c.move(2, -1).move(1, -1)
        assertEquals(listOf(Source.SERVER, Source.BED, Source.ANCHOR, Source.WORLD), serverFirst.respawn.map { it.source })
        assertEquals(Source.SERVER, serverFirst.choose(bed = true, anchor = true, hasServerSpawn = true))
        assertEquals(Source.BED, serverFirst.choose(bed = true, anchor = false, hasServerSpawn = false))

        // 끈 것은 건너뛰고, 다 안 되면 바닐라 그대로(null).
        val noWorld = c.toggle(3)
        assertEquals(null, noWorld.choose(bed = false, anchor = false, hasServerSpawn = false))
        assertEquals(Source.ANCHOR, c.toggle(0).choose(bed = true, anchor = true, hasServerSpawn = true))
    }

    @Test
    fun `옮기기는 끝에서 멈춘다`() {
        val c = SpawnConfig()
        assertEquals(c, c.move(0, -1))
        assertEquals(c, c.move(3, +1))
        assertEquals(c, c.move(9, +1))
    }

    @Test
    fun `순서 읽기 — 모르는 것은 버리고, 빠진 것은 끈 채로 끝에`() {
        val order = SpawnConfig.parseOrder(listOf("server", "-bed", "moon", "server", " World "))
        assertEquals(
            listOf(Step(Source.SERVER), Step(Source.BED, enabled = false), Step(Source.WORLD), Step(Source.ANCHOR, enabled = false)),
            order,
        )
        assertEquals(SpawnConfig.DEFAULT_ORDER, SpawnConfig.parseOrder(emptyList()))
    }

    @Test
    fun `파일 왕복`() {
        val c = SpawnConfig(
            point = SpawnPoint("spawn.world", 10.5, 64.0, -3.5, 90f, 0f),
            firstJoin = false, everyJoin = true, respawn = SpawnConfig().toggle(1).move(3, -2).respawn, wait = 5, cooldown = 30,
        )
        val back = SpawnConfig.from(YamlConfiguration().apply { loadFromString(c.toYaml().saveToString()) })
        assertEquals(c, back)
        assertEquals(SpawnConfig(), SpawnConfig.from(YamlConfiguration()))
    }
}
