package com.inmc.menu.rtp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 무작위 자리를 뽑는 모양. */
enum class Shape(val label: String) { CIRCLE("원"), SQUARE("네모") }

/**
 * 중심에서 [min]~[max] 블록 고리 안의 무작위 좌표 — **넓이에 고르게**(바깥쪽이 넓으니 더 자주). 순수 함수(`RtpTest`).
 *
 * - 원: 반지름 r = √(u·(max²−min²) + min²), 각도 고르게 — r 을 고르게 뽑으면 가운데로 몰린다.
 * - 네모: 큰 네모 안에서 고르게 뽑고 작은 네모 안이면 다시(바깥 띠만 남김). 띠가 얇아도 몇 번이면 된다 — 32번 넘으면 가장자리로.
 */
object Ring {

    fun pick(shape: Shape, centerX: Int, centerZ: Int, min: Int, max: Int, random: Random): Pair<Int, Int> {
        val outer = max(max, 1)
        val inner = min.coerceIn(0, outer - 1)
        return when (shape) {
            Shape.CIRCLE -> {
                val r = sqrt(random.nextDouble() * (outer.toDouble() * outer - inner.toDouble() * inner) + inner.toDouble() * inner)
                val angle = random.nextDouble() * 2 * PI
                (centerX + (r * cos(angle)).roundToInt()) to (centerZ + (r * sin(angle)).roundToInt())
            }
            Shape.SQUARE -> {
                repeat(32) {
                    val dx = random.nextInt(-outer, outer + 1)
                    val dz = random.nextInt(-outer, outer + 1)
                    if (max(abs(dx), abs(dz)) >= inner) return (centerX + dx) to (centerZ + dz)
                }
                val along = random.nextInt(-outer, outer + 1)
                if (random.nextBoolean()) (centerX + along) to (centerZ + if (random.nextBoolean()) outer else -outer)
                else (centerX + if (random.nextBoolean()) outer else -outer) to (centerZ + along)
            }
        }
    }
}
