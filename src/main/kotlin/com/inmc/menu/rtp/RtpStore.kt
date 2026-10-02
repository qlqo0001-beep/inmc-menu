package com.inmc.menu.rtp

import com.inmc.menu.Hub
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration

/**
 * `rtp-worlds.yml` — 랜덤 TP 월드 목록. 켤 때 그 자리에서 읽고, 고치면 워커에서 통째로 쓴다(몇 줄뿐).
 * 파일이 없으면(처음) 기본 월드와 그 네더·엔드를 넣고 오버월드만 켠다.
 */
class RtpStore(private val hub: Hub) {

    @Volatile
    var worlds: List<RtpWorld> = emptyList()
        private set

    private val file get() = hub.io.file(FILE)

    fun loadNow() {
        if (!file.isFile) {
            worlds = firstDefaults()
            save()
            return
        }
        val yaml = YamlConfiguration.loadConfiguration(file)
        worlds = yaml.getMapList("worlds").mapNotNull(RtpWorld::load).distinctBy { it.world }
    }

    fun get(world: String): RtpWorld? = worlds.firstOrNull { it.world == world }

    /** 고르기 화면에 나오는 것 — 켜졌고 서버에 열려 있는 월드. */
    fun usable(): List<RtpWorld> = worlds.filter { it.enabled && Bukkit.getWorld(it.world) != null }

    /** 같은 월드는 제자리에서 바꾸고, 새 월드는 끝에. */
    fun put(world: RtpWorld) {
        val clean = world.sanitized()
        worlds = if (worlds.any { it.world == clean.world }) worlds.map { if (it.world == clean.world) clean else it } else worlds + clean
        save()
    }

    /** 이 월드의 설정 — 없으면 환경에 맞는 기본값(꺼짐). */
    fun orDefault(world: World): RtpWorld = get(world.name) ?: RtpWorld.defaultsFor(world.name, world.environment, enabled = false)

    private fun save() {
        val yaml = YamlConfiguration()
        yaml.options().setHeader(HEADER.lines())
        yaml.set("worlds", worlds.map { it.save() })
        hub.io.asyncRun { hub.io.save(file, yaml) }
    }

    private fun firstDefaults(): List<RtpWorld> {
        val main = Bukkit.getWorlds().firstOrNull() ?: return emptyList()
        return listOfNotNull(main, Bukkit.getWorld(main.name + "_nether"), Bukkit.getWorld(main.name + "_the_end"))
            .map { RtpWorld.defaultsFor(it.name, it.environment, enabled = it.environment == World.Environment.NORMAL) }
    }

    companion object {
        const val FILE = "rtp-worlds.yml"

        val HEADER = """
            랜덤 TP(/야생) 월드 목록. 게임 안 /야생 관리 에서 고치는 것이 편합니다(고치면 이 파일을 다시 씁니다).
            순서가 고르기 화면의 순서입니다. 서버 전체 한도(동시 찾기 수 …)는 config.yml 의 rtp.
        """.trimIndent()
    }
}
