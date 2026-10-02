package com.inmc.menu

import com.inmc.menu.gui.RtpMenu
import com.inmc.menu.rtp.ChunkMarks
import com.inmc.menu.rtp.Ring
import com.inmc.menu.rtp.RtpWorld
import com.inmc.menu.rtp.Shape
import com.inmc.menu.rtp.Slots
import org.bukkit.Material
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 랜덤 TP 의 순수 부분 — 고리 뽑기·실패 구역·동시 찾기 상한·설정. */
class RtpTest {

    @Test
    fun `원 — 고리 안에만, 넓이에 고르게`() {
        val random = Random(1)
        val (min, max) = 500 to 5000
        var inner = 0
        // 넓이를 반으로 나누는 반지름 — 고르게 뽑았으면 절반쯤이 그 안.
        val half = sqrt((min.toDouble() * min + max.toDouble() * max) / 2)
        repeat(20_000) {
            val (x, z) = Ring.pick(Shape.CIRCLE, 100, -200, min, max, random)
            val d = hypot((x - 100).toDouble(), (z + 200).toDouble())
            assertTrue(d >= min - 1 && d <= max + 1, "거리 $d")
            if (d < half) inner++
        }
        assertTrue(abs(inner - 10_000) < 400, "안쪽 절반 넓이에 $inner / 20000 — 가운데로 몰리면 안 된다")
    }

    @Test
    fun `네모 — 바깥 띠에만`() {
        val random = Random(2)
        repeat(20_000) {
            val (x, z) = Ring.pick(Shape.SQUARE, 0, 0, 1000, 1200, random)
            val d = max(abs(x), abs(z))
            assertTrue(d in 1000..1200, "체비쇼프 거리 $d")
        }
        // 최소가 최대 이상이어도 멈추지 않는다.
        Ring.pick(Shape.CIRCLE, 0, 0, 5000, 5000, random)
        Ring.pick(Shape.SQUARE, 0, 0, 5000, 5000, random)
    }

    @Test
    fun `실패 구역 — 적고, 음수 좌표, 묶음 경계, 파일 왕복, 씨앗이 다르면 버림`() {
        val marks = ChunkMarks()
        val spots = listOf(0 to 0, -1 to -1, 31 to 31, 32 to 0, -33 to 64, 100_000 to -100_000)
        for ((x, z) in spots) assertTrue(marks.mark(x, z))
        assertFalse(marks.mark(0, 0), "두 번 적지 않는다")
        for ((x, z) in spots) assertTrue(marks.has(x, z), "$x,$z")
        assertFalse(marks.has(1, 0))
        assertFalse(marks.has(-1, 0))
        assertFalse(marks.has(0, 32))
        assertEquals(spots.size, marks.count())

        val file = Files.createTempFile("marks", ".bad").toFile()
        try {
            marks.write(file, seed = 42)
            assertFalse(marks.dirty)
            val back = ChunkMarks.read(file, seed = 42)
            for ((x, z) in spots) assertTrue(back.has(x, z))
            assertEquals(spots.size, back.count())
            assertEquals(0, ChunkMarks.read(file, seed = 43).count(), "월드를 새로 만들면 옛 기록은 버린다")
        } finally {
            file.delete()
        }

        val tiny = ChunkMarks(limit = 1)
        assertTrue(tiny.mark(0, 0))
        assertTrue(tiny.mark(5, 5), "같은 묶음은 한도와 상관없이")
        assertFalse(tiny.mark(1000, 1000), "새 묶음은 한도에서 멈춘다")
    }

    @Test
    fun `동시 찾기 상한과 대기열`() {
        val slots = Slots<String>(limit = 2, queueLimit = 2)
        assertEquals(Slots.Offer.Run, slots.offer("a"))
        assertEquals(Slots.Offer.Run, slots.offer("b"))
        assertEquals(Slots.Offer.Queued(1), slots.offer("c"))
        assertEquals(Slots.Offer.Queued(2), slots.offer("d"))
        assertEquals(Slots.Offer.Full, slots.offer("e"))
        assertEquals(2, slots.position("d"))

        // 대기 중인 사람이 그만두면 뒤가 당겨지고, 아무도 찾기를 시작하지 않는다.
        assertEquals(emptyList(), slots.release("c"))
        assertEquals(1, slots.position("d"))
        // 찾던 사람이 끝나면 맨 앞이 들어간다.
        assertEquals(listOf("d"), slots.release("a"))
        assertEquals(0, slots.position("d"))
        assertEquals(2, slots.runningCount)
        assertEquals(0, slots.waitingCount)
        assertEquals(emptyList(), slots.release("a"), "두 번 비워도 아무 일 없다")
        assertEquals(-1, slots.position("a"))

        // 상한을 늘리면 다음 비울 때 여럿이 들어간다.
        slots.offer("x")
        slots.offer("y")
        slots.limit = 4
        assertEquals(listOf("x", "y"), slots.release("b"))
    }

    @Test
    fun `월드 설정 — 저장 왕복, 범위 맞춤, 생물군계 이름`() {
        val world = RtpWorld("world.with.dots", enabled = true, name = "<green>야생</green>", icon = Material.OAK_SAPLING, centerX = 10, centerZ = -20,
            min = 100, max = 2000, shape = Shape.SQUARE, cooldown = 90, currency = "gold", cost = 500, permission = "x.y", countdown = 2,
            avoidBiomes = listOf("minecraft:ocean", "minecraft:river"))
        assertEquals(world, RtpWorld.load(world.save()))

        val wild = RtpWorld("w", min = 9000, max = 5, countdown = 9, cost = -3, avoidBiomes = listOf(" Ocean ", "custom:place", "", "ocean")).sanitized()
        assertEquals(RtpWorld.MIN_RANGE, wild.max)
        assertEquals(0, wild.min)
        assertEquals(5, wild.countdown)
        assertEquals(0, wild.cost)
        assertEquals(listOf("minecraft:ocean", "custom:place"), wild.avoidBiomes)
        assertEquals(null, RtpWorld.load(mapOf("enabled" to true)), "월드 이름이 없으면 버린다")
    }

    @Test
    fun `고르기 화면 — 가운데 줄에 가운데 맞춤`() {
        assertEquals(listOf(13), RtpMenu.slots(1))
        assertEquals(listOf(12, 14), RtpMenu.slots(2))
        assertEquals(listOf(9, 11, 13, 15, 17), RtpMenu.slots(5))
        assertEquals((10..15).toList(), RtpMenu.slots(6))
        assertEquals((9..17).toList(), RtpMenu.slots(12))
        for (n in 1..9) assertTrue(RtpMenu.slots(n).all { it in 9..17 })
    }
}
