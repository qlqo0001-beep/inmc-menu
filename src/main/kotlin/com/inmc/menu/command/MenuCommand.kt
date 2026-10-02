package com.inmc.menu.command

import com.inmc.menu.Hub
import com.inmc.menu.MenuPlugin
import com.inmc.menu.gui.EditorMenu
import com.inmc.menu.gui.ManageMenu
import com.inmc.menu.gui.SettingsMenu
import com.inmc.menu.menu.Actions
import com.inmc.menu.util.Ph
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * `/메뉴` · `/설정`. 메뉴 이름은 줄 끝까지 받는다 — Brigadier 의 `word()` 는 한글 첫 글자에서 멈춘다(화폐 명령어와 같은 이유).
 */
class MenuCommand(private val hub: Hub, private val plugin: MenuPlugin) {

    fun register(owner: JavaPlugin) {
        owner.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(menuTree().build(), "INMC 플레이어 메뉴", listOf("menu"))
            event.registrar().register(settingsTree().build(), "개인 설정", listOf("개인설정"))
        }
    }

    private val menuNames = SuggestionProvider<CommandSourceStack> { _, builder ->
        hub.menus.all().map { it.id }.filter { it.startsWith(builder.remaining) }.forEach(builder::suggest)
        builder.buildFuture()
    }

    private fun sender(ctx: CommandContext<CommandSourceStack>): CommandSender = ctx.source.sender

    private fun player(ctx: CommandContext<CommandSourceStack>): Player? =
        (ctx.source.executor as? Player ?: ctx.source.sender as? Player) ?: null.also { hub.messages.send(sender(ctx), "player-only") }

    private fun isAdmin(source: CommandSourceStack): Boolean = source.sender.hasPermission(Hub.ADMIN)

    private fun canUse(source: CommandSourceStack): Boolean = source.sender.hasPermission(Hub.USE)

    private fun menuTree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("메뉴").requires(::canUse)
            .executes { ctx -> player(ctx)?.let { Actions.openMain(hub, it) }; 1 }
            .then(Commands.literal("도움말").executes { ctx -> hub.messages.send(sender(ctx), "help"); 1 })
            .then(
                Commands.literal("편집").requires(::isAdmin)
                    .executes { ctx -> player(ctx)?.let { edit(it, hub.config.mainMenu) }; 1 }
                    .then(Commands.argument("메뉴", StringArgumentType.greedyString()).suggests(menuNames).executes { ctx ->
                        player(ctx)?.let { edit(it, StringArgumentType.getString(ctx, "메뉴").trim()) }
                        1
                    }),
            )
            .then(Commands.literal("관리").requires(::isAdmin).executes { ctx -> player(ctx)?.let { ManageMenu(hub, it).show() }; 1 })
            .then(Commands.literal("검증").requires(::isAdmin).executes { ctx -> player(ctx)?.let { com.inmc.menu.verify.Verifier(hub).run(it) }; 1 })
            .then(Commands.literal("리로드").requires(::isAdmin).executes { ctx ->
                val sender = sender(ctx)
                plugin.reload { hub.messages.send(sender, "reloaded", Ph.of().count(hub.menus.all().size)) }
                1
            })

    private fun settingsTree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("설정").requires(::canUse).executes { ctx -> player(ctx)?.let { SettingsMenu(hub, it, back = null).show() }; 1 }

    private fun edit(player: Player, id: String) {
        if (hub.menus.get(id) == null) {
            hub.messages.send(player, "menu-missing", Ph.of().value(id))
            return
        }
        EditorMenu(hub, player, id).show()
    }
}
