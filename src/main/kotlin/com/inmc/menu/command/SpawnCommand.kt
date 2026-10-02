package com.inmc.menu.command

import com.inmc.menu.Hub
import com.inmc.menu.gui.SpawnAdminMenu
import com.inmc.menu.spawn.SpawnPoint
import com.inmc.menu.util.Ph
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/** `/스폰` — 서버 스폰으로(대기) · `/스폰 설정`(선 곳을 서버 스폰으로) · `/스폰 관리`(관리자). */
class SpawnCommand(private val hub: Hub) {

    fun register(owner: JavaPlugin) {
        owner.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(tree().build(), "서버 스폰으로")
        }
    }

    private fun player(ctx: CommandContext<CommandSourceStack>): Player? =
        (ctx.source.executor as? Player ?: ctx.source.sender as? Player) ?: null.also { hub.messages.send(ctx.source.sender, "player-only") }

    private fun isAdmin(source: CommandSourceStack): Boolean = source.sender.hasPermission(Hub.ADMIN)

    private fun tree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("스폰").requires { it.sender.hasPermission(Hub.SPAWN) }
            .executes { ctx -> player(ctx)?.let { hub.spawn.request(it) }; 1 }
            .then(Commands.literal("설정").requires(::isAdmin).executes { ctx ->
                player(ctx)?.let { player ->
                    val point = SpawnPoint.of(player.location)
                    hub.spawn.update { it.copy(point = point) }
                    hub.messages.send(player, "spawn-set", Ph.of().value(point.describe()))
                }
                1
            })
            .then(Commands.literal("관리").requires(::isAdmin).executes { ctx -> player(ctx)?.let { SpawnAdminMenu(hub, it).show() }; 1 })
}
