package com.inmc.menu.menu

import com.inmc.menu.Hub
import com.inmc.menu.gui.MenuView
import com.inmc.menu.gui.SettingsMenu
import com.inmc.menu.util.Ph
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/** 버튼의 동작을 돌리고 메뉴·내장 화면을 연다 — 명령어·화면·편집기가 같이 쓴다. */
object Actions {

    /**
     * 차례로 돌린다. 화면을 여는 동작은 **다음 틱에** — 클릭 사건 안에서 다른 창을 열면 그 클릭이 새 창에 새어 든다.
     * 명령어도 같다(그 명령어가 자기 화면을 연다).
     */
    fun run(hub: Hub, player: Player, actions: List<Action>) {
        if (actions.isEmpty()) return
        player.scheduler.run(hub.plugin, { _ ->
            for (action in actions) {
                when (action) {
                    is Action.PlayerCommand -> player.performCommand(substitute(action.command, player))
                    is Action.ConsoleCommand -> runCatching { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), substitute(action.command, player)) }
                        .onFailure { hub.logger.warning("메뉴 명령어 실행 실패 (${action.command}): ${it.message}") }
                    is Action.OpenMenu -> openMenu(hub, player, action.menu)
                    is Action.OpenScreen -> openScreen(hub, player, action.screen)
                    is Action.Message -> player.sendMessage(Text.render(action.text, Ph.of().player(kr.inmc.core.integration.TitleForgeNames.displayName(player.uniqueId, player.name)), player))
                    Action.Close -> player.closeInventory()
                }
            }
        }, null)
    }

    /** 명령어는 MiniMessage 를 거치지 않는다 — 꺾쇠와 `&` 가 사라진다. 자리표시만(core `RewardService.runCommand` 와 같은 이유). */
    private fun substitute(command: String, player: Player): String =
        Text.substituteOnly(command, Ph.of().player(player.name), player).trim().removePrefix("/")

    fun openMenu(hub: Hub, player: Player, id: String): Boolean {
        val def = hub.menus.get(id)
        if (def == null) {
            hub.messages.send(player, "menu-missing", Ph.of().value(id))
            return false
        }
        MenuView(hub, player, def).show()
        return true
    }

    fun openMain(hub: Hub, player: Player): Boolean = openMenu(hub, player, hub.config.mainMenu)

    fun openScreen(hub: Hub, player: Player, screen: Screen) {
        when (screen) {
            Screen.SETTINGS -> SettingsMenu(hub, player).show()
            Screen.RTP -> com.inmc.menu.gui.RtpMenu(hub, player).show()
            Screen.SPAWN -> hub.spawn.request(player)
            Screen.ATTENDANCE -> com.inmc.menu.gui.AttendanceScreens.open(hub, player)
        }
    }
}
