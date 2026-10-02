package com.inmc.menu

import com.inmc.menu.config.MenuConfig
import com.inmc.menu.config.Messages
import com.inmc.menu.menu.MenuStore
import com.inmc.menu.settings.MenuSettings
import com.inmc.menu.settings.Visibility
import com.inmc.menu.util.Ph
import kr.inmc.core.InmcHost
import kr.inmc.core.config.ConfigService
import kr.inmc.core.integration.CustomItemHook
import kr.inmc.core.integration.MMOItemsHook
import kr.inmc.core.item.ItemResolver
import kr.inmc.core.util.Placeholders
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/**
 * 플러그인을 엮는 서비스 로케이터.
 *
 * 다른 inmc 플러그인을 모른다 — 버튼은 **명령어로** 그 기능을 열고(상점·배낭·낚시 …), 개인 설정은 core `PlayerSettings` 로만 만난다.
 */
class Hub(override val plugin: JavaPlugin) : InmcHost {

    val logger: java.util.logging.Logger = plugin.logger

    override val io = ConfigService(plugin)

    override fun tell(target: CommandSender, key: String, ph: Placeholders?) = messages.send(target, key, ph as? Ph)

    @Volatile
    var config: MenuConfig = MenuConfig()

    @Volatile
    var messages: Messages = Messages.from(YamlConfiguration())

    val customItems = CustomItemHook(logger)

    /** 랜덤 TP 비용 — 기본 화폐(없으면 Vault). 다른 화폐는 core `Currencies` 로 바로. */
    val economy = kr.inmc.core.integration.EconomyHook(logger)

    val mmoItems = MMOItemsHook(logger)

    /** 버튼 아이콘(손에 든 것 — 커스텀아이템·MMOItems 그대로). */
    val resolver = ItemResolver(mmoItems, customItems, logger)

    val menus = MenuStore(this)

    val settings = MenuSettings(this)

    val visibility = Visibility(this)

    /** 랜덤 TP 월드 목록(`rtp-worlds.yml`) · 찾기. */
    val rtpWorlds = com.inmc.menu.rtp.RtpStore(this)

    val rtp = com.inmc.menu.rtp.RtpService(this)

    /** 서버 스폰 · `/스폰` · 접속·부활 자리(`spawn.yml`). */
    val spawn = com.inmc.menu.spawn.SpawnService(this)

    /** 출석판(`attendance/`) · 사람마다의 기록(`attendance-data/<uuid>.yml`). */
    val attendance = com.inmc.menu.attend.AttendanceService(this)

    /** 리소스팩 화면의 그림(메뉴마다 배경 글자). */
    val art = com.inmc.menu.pack.MenuArt(this)

    /**
     * 리소스팩을 다 받은 사람 → 받은 때(밀리초). 이 사람에게만 그린 화면을 보인다 — 안 받은 사람이 보라·검정 상자를 보지 않게.
     * 팩이 새로 만들어지면 그보다 먼저 받은 사람은 빠진다(옛 팩에는 새 그림이 없다).
     */
    val packLoaded: MutableMap<UUID, Long> = java.util.concurrent.ConcurrentHashMap()

    /** 지금 리소스팩에 우리 그림이 들어 있는가(커스텀아이템이 만든 `pack.zip` 으로 확인 — `MenuArt`). */
    @Volatile
    var artReady = false

    /** 이 사람에게 리소스팩 화면을 보일까. */
    fun usesPack(player: org.bukkit.entity.Player): Boolean = config.packEnabled && artReady && player.uniqueId in packLoaded

    @Volatile
    var ready = false
        private set

    fun markReady() {
        ready = true
    }

    /** 화면에서 고친 설정을 메모리와 파일에. */
    fun updateConfig(change: (MenuConfig) -> MenuConfig) {
        config = change(config)
        val yaml = config.toYaml()
        io.asyncRun { io.save(io.file("config.yml"), yaml) }
    }

    /** 리로드 때 — 버려질 정의를 계속 고치지 않게 열린 우리 화면을 닫는다. */
    fun closeMenus() {
        for (player in Bukkit.getOnlinePlayers()) {
            val holder = player.openInventory.topInventory.holder
            if (holder is kr.inmc.core.gui.Menu && holder.owner === this) player.closeInventory()
        }
    }

    companion object {

        /** core PlayerStore 네임스페이스 — 쿨타임(`rtp-<월드>` · `spawn`). ARCHITECTURE 의 표에 있다. */
        const val STORE = "menu"

        const val USE = "inmcmenu.use"
        const val ADMIN = "inmcmenu.admin"
        const val SPAWN = "inmcmenu.spawn"
        const val RTP = "inmcmenu.rtp"
        const val RTP_BYPASS = "inmcmenu.rtp.bypass"
        const val ATTENDANCE = "inmcmenu.attendance"
    }
}
