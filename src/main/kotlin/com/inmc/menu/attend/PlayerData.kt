package com.inmc.menu.attend

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

/**
 * 한 사람의 출석 — 출석판마다 [Record], 가방이 차서 **못 받은 보상**(아이템), 오늘 접속한 분.
 * `attendance-data/<uuid>.yml` 하나. 출석·보상 받기마다 **그 자리에서 원자적으로** 쓴다 — 30초 저장 창 사이에 서버가 꺼지면
 * 또 받는 일이 없게(업적 `claims/<uuid>.yml` 과 같은 이유). 접속 분은 나갈 때만 쓴다(잃어도 몇 분).
 */
class PlayerData(val id: UUID) {

    val records = HashMap<String, Record>()
    val pending = ArrayList<ItemStack>()
    var playedDate: LocalDate? = null
    var playedMinutes = 0

    /** 그 판의 기록 — 판이 초기화된 뒤의 옛 기록(회차가 다름)은 없는 것으로. */
    fun record(board: Board): Record = records[board.id]?.takeIf { it.round == board.round } ?: Record(round = board.round)

    /** 오늘 접속한 분 — 날이 바뀌었으면 0 부터. */
    fun minutes(today: LocalDate): Int = if (playedDate == today) playedMinutes else 0

    fun addMinute(today: LocalDate) {
        if (playedDate != today) {
            playedDate = today
            playedMinutes = 0
        }
        playedMinutes++
    }

    fun toYaml(): YamlConfiguration = YamlConfiguration().also { y ->
        playedDate?.let {
            y.set("played.date", it.toString())
            y.set("played.minutes", playedMinutes)
        }
        for ((board, record) in records) record.save(y.createSection("boards.$board"))
        if (pending.isNotEmpty()) y.set("pending", pending.mapNotNull { stack -> runCatching { Base64.getEncoder().encodeToString(stack.serializeAsBytes()) }.getOrNull() })
    }

    /** 임시 파일에 쓰고 바꿔 끼운다 — 쓰다 꺼져도 옛 파일이 남는다. */
    fun write(file: File) = writeAtomically(file, toYaml().saveToString())

    companion object {

        private fun writeAtomically(file: File, text: String) {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(text, Charsets.UTF_8)
            runCatching { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
                .recoverCatching { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                .getOrThrow()
        }

        /**
         * 접속 안 한 사람의 파일에서 판 하나의 기록을 지운다(워커) — 글자 단위로만 고치고 못 받은 보상(아이템)은 풀지 않는다.
         * 지운 것이 있었으면 true.
         */
        fun forget(file: File, board: String): Boolean {
            if (!file.isFile) return false
            val y = YamlConfiguration.loadConfiguration(file)
            if (!y.contains("boards.$board")) return false
            y.set("boards.$board", null)
            writeAtomically(file, y.saveToString())
            return true
        }

        fun read(id: UUID, file: File): PlayerData {
            val data = PlayerData(id)
            if (!file.isFile) return data
            val y = YamlConfiguration.loadConfiguration(file)
            data.playedDate = y.getString("played.date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            data.playedMinutes = y.getInt("played.minutes")
            y.getConfigurationSection("boards")?.let { boards ->
                for (key in boards.getKeys(false)) data.records[key] = Record.load(boards.getConfigurationSection(key))
            }
            for (raw in y.getStringList("pending")) {
                runCatching { ItemStack.deserializeBytes(Base64.getDecoder().decode(raw)) }.getOrNull()?.let { data.pending += it }
            }
            return data
        }
    }
}
