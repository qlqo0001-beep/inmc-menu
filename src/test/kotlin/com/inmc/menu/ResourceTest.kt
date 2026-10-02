package com.inmc.menu

import com.inmc.menu.config.MenuConfig
import com.inmc.menu.config.Messages
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 배포 파일. */
class ResourceTest {

    private fun yaml(path: String): YamlConfiguration =
        javaClass.classLoader.getResourceAsStream(path)!!.use { YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8)) }

    private val code: String by lazy {
        File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
    }

    @Test
    fun `배포 메시지와 기본값 표의 키가 정확히 같다`() {
        assertEquals(Messages.DEFAULTS.keys, yaml("messages.yml").getKeys(false))
    }

    @Test
    fun `코드가 부르는 메시지 키가 전부 있다`() {
        // 없는 키는 오류 없이 빈 줄이 된다.
        val used = Regex("""send\([^,()]+, (?:if \([^)]*\) )?"([a-z-]+)"(?: else "([a-z-]+)")?""").findAll(code)
            .flatMap { listOf(it.groupValues[1], it.groupValues[2]) }.filter { it.isNotEmpty() }.toSet()
        assertTrue(used.size > 10, "키를 못 읽었다: $used")
        assertEquals(emptySet(), used - Messages.DEFAULTS.keys)
    }

    @Test
    fun `배포 설정은 코드의 기본값과 같다`() {
        assertEquals(MenuConfig(), MenuConfig.from(yaml("config.yml")))
    }

    @Test
    fun `코드가 쓰는 권한은 전부 선언돼 있다`() {
        // 선언하지 않은 권한은 op 기본(지뢰 11) — 메뉴가 일반 플레이어에게 안 열린다.
        val yml = File("src/main/resources/paper-plugin.yml").readText()
        val used = Regex(""""(inmcmenu\.[a-z.-]+)"""").findAll(code).map { it.groupValues[1] }.toSet()
        assertTrue(used.size >= 5, "권한을 못 읽었다: $used")
        for (node in used) assertTrue(Regex("""(?m)^  ${Regex.escape(node)}:""").containsMatchIn(yml), "선언 안 된 권한 $node")
        for (open in listOf(Hub.USE, Hub.SPAWN, Hub.RTP, Hub.ATTENDANCE)) {
            assertTrue(Regex("""(?ms)^  ${Regex.escape(open)}:\s*\n\s+description:[^\n]*\n\s+default: true""").containsMatchIn(yml), "$open 은 누구나")
        }
    }
}
