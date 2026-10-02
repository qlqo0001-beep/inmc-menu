package com.inmc.menu.command

import com.inmc.menu.Hub
import com.inmc.menu.gui.AttendanceAdminMenu
import com.inmc.menu.gui.AttendanceScreens
import com.inmc.menu.util.Ph
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * `/출석` — 출석 화면 · `/출석 관리`(관리자) · `/출석 초기화 <플레이어> <판>`(관리자 — 한 사람의 판 하나 기록).
 * 판 이름은 한글일 수 있어 맨 뒤 [StringArgumentType.greedyString] 이다(`word()` 는 한글을 못 받는다).
 */
class AttendanceCommand(private val hub: Hub) {

    fun register(owner: JavaPlugin) {
        owner.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(tree().build(), "출석", listOf("attendance"))
        }
    }

    private fun player(ctx: CommandContext<CommandSourceStack>): Player? =
        (ctx.source.executor as? Player ?: ctx.source.sender as? Player) ?: null.also { hub.messages.send(ctx.source.sender, "player-only") }

    private fun tree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("출석").requires { it.sender.hasPermission(Hub.ATTENDANCE) }
            .executes { ctx -> player(ctx)?.let { AttendanceScreens.open(hub, it, back = null) }; 1 }
            .then(Commands.literal("관리").requires { it.sender.hasPermission(Hub.ADMIN) }
                .executes { ctx -> player(ctx)?.let { AttendanceAdminMenu(hub, it).show() }; 1 })
            .then(Commands.literal("초기화").requires { it.sender.hasPermission(Hub.ADMIN) }
                .then(Commands.argument("플레이어", StringArgumentType.word())
                    .suggests { _, builder -> Bukkit.getOnlinePlayers().forEach { builder.suggest(it.name) }; builder.buildFuture() }
                    .then(Commands.argument("판", StringArgumentType.greedyString())
                        .suggests { _, builder -> hub.attendance.boards.forEach { builder.suggest(it.id) }; builder.buildFuture() }
                        .executes { ctx -> resetPlayer(ctx, StringArgumentType.getString(ctx, "플레이어"), StringArgumentType.getString(ctx, "판").trim()) })))

    private fun resetPlayer(ctx: CommandContext<CommandSourceStack>, name: String, boardId: String): Int {
        val sender = ctx.source.sender
        val board = hub.attendance.board(boardId) ?: return 0.also { hub.messages.send(sender, "attend-no-board", Ph.of().value(boardId)) }
        // 이름 → 사람: 캐시만 본다(모르는 이름을 모장에 묻느라 메인이 멈추지 않게).
        val target = Bukkit.getPlayerExact(name) ?: Bukkit.getOfflinePlayerIfCached(name)
            ?: return 0.also { hub.messages.send(sender, "attend-no-player", Ph.of().player(name)) }
        val shown = target.name ?: name
        hub.attendance.resetPlayer(board.id, target.uniqueId) { removed ->
            hub.messages.send(sender, if (removed) "attend-player-reset" else "attend-player-reset-none", Ph.of().player(shown).value(board.id))
        }
        return 1
    }
}
