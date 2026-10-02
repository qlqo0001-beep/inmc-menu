package com.inmc.menu

import com.inmc.menu.command.MenuCommand
import com.inmc.menu.config.MenuConfig
import com.inmc.menu.config.Messages
import com.inmc.menu.listener.PlayerListener
import com.inmc.menu.scheduler.Ticker
import kr.inmc.core.util.Text
import org.bukkit.plugin.java.JavaPlugin

/**
 * 켜질 때 메뉴를 **그 자리에서** 읽는다 — 첫 Shift+F 전에 있어야 한다. 서버가 틱을 돌기 전이라 괜찮다.
 */
class MenuPlugin : JavaPlugin() {

    private lateinit var hub: Hub
    private lateinit var ticker: Ticker

    /** 우리가 core `Text` 에 건 PlaceholderAPI 연결 — 내려갈 때 그것이 우리 것일 때만 뗀다(다른 플러그인이 건 것은 두고). */
    private var papi: ((org.bukkit.entity.Player?, String) -> String)? = null

    override fun onEnable() {
        hub = Hub(this)
        for (name in RESOURCES) hub.io.copyDefault(name, hub.io.file(name))
        hub.config = MenuConfig.from(hub.io.load(hub.io.file("config.yml")))
        hub.messages = Messages.from(hub.io.load(hub.io.file("messages.yml")))
        hub.mmoItems.setup()
        hub.customItems.setup()
        hub.economy.setup()
        hub.menus.loadNow()
        hub.rtpWorlds.loadNow()
        hub.spawn.loadNow()
        hub.attendance.loadBoards()
        hub.art.load()
        hub.settings.register()
        hookPlaceholders()

        val manager = server.pluginManager
        manager.registerEvents(PlayerListener(hub), this)
        manager.registerEvents(com.inmc.menu.listener.WarmupListener(hub), this)
        manager.registerEvents(com.inmc.menu.listener.SpawnListener(hub), this)
        manager.registerEvents(kr.inmc.core.listener.MenuListener(hub), this)
        MenuCommand(hub, this).register(this)
        com.inmc.menu.command.RtpCommand(hub).register(this)
        com.inmc.menu.command.SpawnCommand(hub).register(this)
        com.inmc.menu.command.AttendanceCommand(hub).register(this)
        // 지역 플러그인(WorldGuard·Lands)은 우리보다 늦게 켜질 수 있다 — 서버가 다 켜진 뒤 첫 틱에 잇는다.
        server.globalRegionScheduler.run(this) { hub.rtp.regions.setup(this) }

        ticker = Ticker(hub)
        hub.markReady()
        ticker.start()
        // /reload 뒤에는 이미 접속해 있는 사람이 있다.
        for (player in server.onlinePlayers) hub.settings.apply(player)
        hub.attendance.loadOnline()
        hub.visibility.refreshAll()
        // 처음 깔렸을 때 — 그림을 그려 팩에 넣는다(그 뒤로는 관리 화면의 "그림 다시 만들기").
        if (hub.config.packEnabled && (!hub.art.hasManifest || hub.art.outdated)) hub.art.refresh(server.consoleSender)
        logger.info("inmc-menu 활성화 완료 - 메뉴 ${hub.menus.all().size}개" + if (hub.artReady) " · 리소스팩 화면 준비됨" else "")
    }

    override fun onDisable() {
        if (!::hub.isInitialized) return
        if (::ticker.isInitialized) ticker.stop()
        hub.rtp.shutdown()
        hub.spawn.shutdown()
        hub.attendance.shutdown()
        hub.settings.unregister()
        hub.visibility.showAll()
        if (papi != null && Text.papiResolver === papi) Text.papiResolver = null
        hub.menus.flushBlocking()
        hub.io.shutdown()
    }

    /** 버튼 이름·설명의 PlaceholderAPI. 다른 플러그인(몬스터·랜덤박스 …)이 이미 걸었으면 그대로 둔다. */
    private fun hookPlaceholders() {
        if (!server.pluginManager.isPluginEnabled("PlaceholderAPI") || Text.papiResolver != null) return
        val resolver: (org.bukkit.entity.Player?, String) -> String = { player, text -> me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text) }
        Text.papiResolver = resolver
        papi = resolver
        logger.info("PlaceholderAPI 연동 활성화")
    }

    /** `/메뉴 리로드`. 고친 메뉴를 먼저 쓰고, 파일은 워커에서 읽고 반영은 메인에서. */
    fun reload(then: () -> Unit) {
        hub.closeMenus()
        hub.menus.flushBlocking()
        hub.io.async({ hub.io.load(hub.io.file("config.yml")) to hub.io.load(hub.io.file("messages.yml")) }) { (config, messages) ->
            hub.config = MenuConfig.from(config)
            hub.messages = Messages.from(messages)
            hub.menus.loadNow()
            hub.rtpWorlds.loadNow()
            hub.spawn.loadNow()
            hub.attendance.loadBoards()
            hub.visibility.refreshAll()
            then()
        }
    }

    private companion object {
        val RESOURCES = listOf("config.yml", "messages.yml")
    }
}
