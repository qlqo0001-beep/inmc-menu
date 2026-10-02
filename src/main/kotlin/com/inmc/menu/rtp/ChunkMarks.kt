package com.inmc.menu.rtp

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * **실패 구역** — 가 봤더니 설 자리가 없던 청크(바다·용암 바다·위험 지형)를 청크 하나에 1비트로 적는다. 다음 뽑기는 청크를 읽기 전에 여기서 거른다
 * ("가 본 곳만 간다"가 아니라 "못 가는 곳을 뺀다").
 *
 * 32×32 청크 묶음(지역 파일과 같은 크기)마다 1024비트(`LongArray(16)`) — 실패는 바다처럼 뭉쳐 나오므로 성기게 담는다.
 * [limit] 묶음을 넘으면 더 적지 않는다(아주 넓은 범위에서 메모리가 끝없이 늘지 않게).
 *
 * 찾기는 워커(거르기)와 메인(적기)이 같이 만지므로 전부 `@Synchronized`.
 */
class ChunkMarks(private val limit: Int = DEFAULT_LIMIT) {

    private val regions = HashMap<Long, LongArray>()

    @Volatile
    var dirty = false
        private set

    @Synchronized
    fun has(cx: Int, cz: Int): Boolean {
        val bits = regions[key(cx, cz)] ?: return false
        val i = index(cx, cz)
        return bits[i ushr 6] and (1L shl (i and 63)) != 0L
    }

    /** 적었으면 true. 이미 있거나 한도에 닿았으면 false. */
    @Synchronized
    fun mark(cx: Int, cz: Int): Boolean {
        val key = key(cx, cz)
        val bits = regions[key] ?: if (regions.size >= limit) return false else LongArray(16).also { regions[key] = it }
        val i = index(cx, cz)
        val bit = 1L shl (i and 63)
        if (bits[i ushr 6] and bit != 0L) return false
        bits[i ushr 6] = bits[i ushr 6] or bit
        dirty = true
        return true
    }

    @Synchronized
    fun count(): Int = regions.values.sumOf { bits -> bits.sumOf { java.lang.Long.bitCount(it) } }

    @Synchronized
    fun clear() {
        if (regions.isEmpty()) return
        regions.clear()
        dirty = true
    }

    /** 파일 머리에 월드 씨앗을 적는다 — 월드를 새로 만들면(씨앗이 바뀌면) 옛 기록은 버린다. */
    @Synchronized
    fun write(file: File, seed: Long) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(temp.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeLong(seed)
            out.writeInt(regions.size)
            for ((key, bits) in regions) {
                out.writeLong(key)
                for (word in bits) out.writeLong(word)
            }
        }
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        dirty = false
    }

    companion object {

        const val DEFAULT_LIMIT = 50_000
        private const val MAGIC = 0x49525450 // "IRTP"
        private const val VERSION = 1

        private fun key(cx: Int, cz: Int): Long = ((cx shr 5).toLong() shl 32) or ((cz shr 5).toLong() and 0xffffffffL)

        private fun index(cx: Int, cz: Int): Int = ((cz and 31) shl 5) or (cx and 31)

        /** 읽기. 파일이 없거나 형식이 다르거나 씨앗이 다르면 빈 기록. */
        fun read(file: File, seed: Long, limit: Int = DEFAULT_LIMIT): ChunkMarks {
            val marks = ChunkMarks(limit)
            if (!file.isFile) return marks
            runCatching {
                DataInputStream(file.inputStream().buffered()).use { input ->
                    if (input.readInt() != MAGIC || input.readInt() != VERSION || input.readLong() != seed) return marks
                    repeat(input.readInt().coerceAtMost(limit)) {
                        val key = input.readLong()
                        marks.regions[key] = LongArray(16) { input.readLong() }
                    }
                }
            }.onFailure { marks.regions.clear() }
            return marks
        }
    }
}
