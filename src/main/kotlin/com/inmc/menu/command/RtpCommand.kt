package com.inmc.menu.command

import com.inmc.menu.Hub
import com.inmc.menu.gui.RtpAdminMenu
import com.inmc.menu.gui.RtpMenu
import com.inmc.menu.gui.RtpStatus
import com.inmc.menu.util.Ph
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kr.inmc.core.util.Text
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * `/야생`(별칭 `rtp`·`랜덤tp`) — 월드 고르기 · `/야생 <월드>` 바로 · `/야생 상태`·`/야생 관리`(관리자).
 * 월드 이름은 줄 끝까지 받는다(메뉴 명령어와 같은 이유 — `word()` 는 한글·점에서 멈춘다).
 */
class RtpCommand(private val hub: Hub) {

    fun register(owner: JavaPlugin) {
        owner.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(tree().build(), "랜덤 TP", listOf("rtp", "랜덤tp"))
        }
    }

    private val worldNames = SuggestionProvider<CommandSourceStack> { ctx, builder ->
        val sender = ctx.source.sender
        hub.rtpWorlds.usable().filter { it.permission.isBlank() || sender.hasPermission(it.permission) }
            .map { it.world }.filter { it.startsWith(builder.remaining) }.forEach(builder::suggest)
        builder.buildFuture()
    }

    private fun player(ctx: CommandContext<CommandSourceStack>): Player? =
        (ctx.source.executor as? Player ?: ctx.source.sender as? Player) ?: null.also { hub.messages.send(ctx.source.sender, "player-only") }

    private fun isAdmin(source: CommandSourceStack): Boolean = source.sender.hasPermission(Hub.ADMIN)

    private fun tree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("야생").requires { it.sender.hasPermission(Hub.RTP) }
            .executes { ctx -> player(ctx)?.let { RtpMenu(hub, it, back = null).show() }; 1 }
            .then(Commands.literal("상태").requires(::isAdmin).executes { ctx ->
                RtpStatus.lines(hub).forEach { ctx.source.sender.sendMessage(Text.render(it)) }
                1
            })
            .then(Commands.literal("관리").requires(::isAdmin).executes { ctx -> player(ctx)?.let { RtpAdminMenu(hub, it).show() }; 1 })
            .then(Commands.argument("월드", StringArgumentType.greedyString()).suggests(worldNames).executes { ctx ->
                val player = player(ctx) ?: return@executes 1
                val name = StringArgumentType.getString(ctx, "월드").trim()
                val settings = hub.rtpWorlds.get(name)
                if (settings == null) hub.messages.send(player, "rtp-no-world", Ph.of().world(name)) else hub.rtp.request(player, settings)
                1
            })
}
