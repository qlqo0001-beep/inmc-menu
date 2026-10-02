package com.inmc.menu.menu

import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/** 내장 화면 — 버튼이 여는 이 플러그인의 화면. 디스크에는 [id]. */
enum class Screen(val id: String, val label: String) {
    SETTINGS("settings", "개인 설정"),
    ATTENDANCE("attendance", "출석"),
    RTP("rtp", "랜덤 TP"),
    SPAWN("spawn", "스폰으로 이동"),
    ;

    companion object {
        fun parse(raw: String?): Screen? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/**
 * 버튼을 누르면 하는 일 하나. 디스크에는 `종류: 값` 한 줄(`player: 상점` · `menu: contents` · `close`).
 * 명령어는 앞의 `/` 를 떼고 적는다.
 */
sealed interface Action {

    val type: Type

    enum class Type(val id: String, val label: String, val hint: String) {
        PLAYER("player", "명령어(본인)", "누른 사람이 실행. 예: 상점 · 배낭"),
        CONSOLE("console", "명령어(콘솔)", "콘솔이 실행. {플레이어} = 누른 사람"),
        MENU("menu", "메뉴 열기", "메뉴 이름. 예: contents"),
        SCREEN("screen", "내장 화면", "settings · attendance · rtp · spawn"),
        MESSAGE("message", "메시지", "누른 사람에게 보낼 글(MiniMessage)"),
        CLOSE("close", "닫기", "값 없음"),
        ;

        companion object {
            fun parse(raw: String?): Type? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
        }
    }

    data class PlayerCommand(val command: String) : Action { override val type = Type.PLAYER }
    data class ConsoleCommand(val command: String) : Action { override val type = Type.CONSOLE }
    data class OpenMenu(val menu: String) : Action { override val type = Type.MENU }
    data class OpenScreen(val screen: Screen) : Action { override val type = Type.SCREEN }
    data class Message(val text: String) : Action { override val type = Type.MESSAGE }
    data object Close : Action { override val type = Type.CLOSE }

    fun value(): String = when (this) {
        is PlayerCommand -> command
        is ConsoleCommand -> command
        is OpenMenu -> menu
        is OpenScreen -> screen.id
        is Message -> text
        Close -> ""
    }

    fun encode(): String = if (this == Close) type.id else type.id + ": " + value()

    /** 화면에 보일 한 줄. */
    fun describe(): String = if (this == Close) type.label else type.label + " — " + value()

    companion object {

        /** 못 읽으면 null(그 줄만 버린다). */
        fun decode(raw: String): Action? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            val typeText = text.substringBefore(':')
            val value = if (':' in text) text.substringAfter(':').trim() else ""
            return of(Type.parse(typeText) ?: return null, value)
        }

        fun of(type: Type, rawValue: String): Action? {
            val value = rawValue.trim()
            return when (type) {
                Type.PLAYER -> value.removePrefix("/").takeIf { it.isNotBlank() }?.let(::PlayerCommand)
                Type.CONSOLE -> value.removePrefix("/").takeIf { it.isNotBlank() }?.let(::ConsoleCommand)
                Type.MENU -> value.takeIf { it.isNotBlank() }?.let(::OpenMenu)
                Type.SCREEN -> Screen.parse(value)?.let(::OpenScreen)
                Type.MESSAGE -> value.takeIf { it.isNotBlank() }?.let(::Message)
                Type.CLOSE -> Close
            }
        }
    }
}

/** 특별한 버튼 — 그림 대신 진짜 아이템을 그 칸에 그린다. */
enum class Special(val id: String, val label: String) {
    /** 누른 사람의 머리 + 정보(설명에 PlaceholderAPI). */
    PROFILE("profile", "내 정보(머리)"),
    ;

    companion object {
        fun parse(raw: String?): Special? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/**
 * 버튼 하나 — 메뉴의 **칸 묶음**(왼쪽 위 [col]·[row] 에서 [width]×[height]). 리소스팩 화면에서는 그 칸 전부에 보이지 않는 아이템이 놓여 어디에 마우스를
 * 올려도 같은 이름·설명이 뜨고, 배경 그림은 같은 칸 좌표로 그린다(`gui/MenuArt`) — 그래서 그림과 칸이 어긋날 수 없다.
 */
data class ButtonDef(
    val id: String,
    val col: Int,
    val row: Int,
    val width: Int = 1,
    val height: Int = 1,
    val name: String,
    val lore: List<String> = emptyList(),
    /** 리소스팩을 안 받은 사람에게 보이는 아이템(손에 든 것으로 정한다). */
    val icon: StoredItem,
    /** 배경에 그릴 그림의 이름(기본 그림 또는 `art/` 의 png). 비우면 판만. */
    val art: String = "",
    /** 이 권한이 있어야 누른다. 비우면 누구나. */
    val permission: String = "",
    /** 권한이 없으면 숨김(true) / 잠금 표시(false). */
    val hideLocked: Boolean = false,
    val left: List<Action> = emptyList(),
    val right: List<Action> = emptyList(),
    val special: Special? = null,
) {

    /** 차지하는 칸 번호(줄 × 9 + 열). */
    fun slots(): List<Int> = buildList { for (r in row until row + height) for (c in col until col + width) add(r * 9 + c) }

    /** 왼쪽 위 칸 — 일반 아이콘 화면에서 아이콘이 놓이는 곳. */
    val anchor: Int get() = row * 9 + col

    fun fits(rows: Int): Boolean = col >= 0 && row >= 0 && width >= 1 && height >= 1 && col + width <= 9 && row + height <= rows

    fun overlaps(other: ButtonDef): Boolean =
        col < other.col + other.width && other.col < col + width && row < other.row + other.height && other.row < row + height

    fun save(section: ConfigurationSection) {
        section.set("at", listOf(col, row))
        section.set("size", listOf(width, height))
        section.set("name", name)
        if (lore.isNotEmpty()) section.set("lore", lore)
        icon.save(section.createSection("icon"))
        if (art.isNotBlank()) section.set("art", art)
        if (permission.isNotBlank()) section.set("permission", permission)
        if (hideLocked) section.set("hide-locked", true)
        if (left.isNotEmpty()) section.set("left", left.map(Action::encode))
        if (right.isNotEmpty()) section.set("right", right.map(Action::encode))
        special?.let { section.set("special", it.id) }
    }

    companion object {

        /** 버튼 id 는 YAML 열쇠다 — 점은 안 된다(지뢰 1). */
        val ID = Regex("^[a-z0-9_가-힣-]{1,32}$")

        fun load(id: String, section: ConfigurationSection): ButtonDef? {
            val at = section.getIntegerList("at")
            val size = section.getIntegerList("size")
            val icon = section.getConfigurationSection("icon")?.let(StoredItem::load)
                ?: StoredItem(kr.inmc.core.item.ItemRef.parse("minecraft:paper"), Material.PAPER)
            return ButtonDef(
                id = id,
                col = at.getOrElse(0) { 0 },
                row = at.getOrElse(1) { 0 },
                width = size.getOrElse(0) { 1 },
                height = size.getOrElse(1) { 1 },
                name = section.getString("name").orEmpty(),
                lore = section.getStringList("lore"),
                icon = icon,
                art = section.getString("art").orEmpty(),
                permission = section.getString("permission").orEmpty(),
                hideLocked = section.getBoolean("hide-locked", false),
                left = section.getStringList("left").mapNotNull(Action::decode),
                right = section.getStringList("right").mapNotNull(Action::decode),
                special = Special.parse(section.getString("special")),
            )
        }
    }
}

/**
 * 메뉴 하나 — `menus/<id>.yml`.
 *
 * [rejected] 는 읽을 때 뺀 버튼(범위 밖·겹침)의 **원문**이다. 저장할 때 그대로 되써 넣는다 — 안 그러면 손으로 고친 버튼이 다음 저장에서
 * 파일째 사라진다(지뢰 13).
 */
data class MenuDef(
    val id: String,
    val title: String,
    val rows: Int,
    val buttons: List<ButtonDef>,
    val rejected: Map<String, ConfigurationSection> = emptyMap(),
) {

    fun buttonAt(slot: Int): ButtonDef? = buttons.firstOrNull { slot in it.slots() }

    /** 이 버튼을 놓을 수 있는가(범위 안이고 다른 버튼과 안 겹침). [ignore] 는 옮기는 그 버튼. */
    fun canPlace(button: ButtonDef, ignore: String? = button.id): Boolean =
        button.fits(rows) && buttons.none { it.id != ignore && it.overlaps(button) }

    fun with(button: ButtonDef): MenuDef = copy(buttons = buttons.filter { it.id != button.id } + button)

    fun without(id: String): MenuDef = copy(buttons = buttons.filter { it.id != id })

    fun save(): YamlConfiguration = YamlConfiguration().also { y ->
        y.set("title", title)
        y.set("rows", rows)
        val section = y.createSection("buttons")
        for (button in buttons) button.save(section.createSection(button.id))
        for ((key, raw) in rejected) if (!section.contains(key)) section.set(key, raw)
    }

    companion object {

        const val MIN_ROWS = 1
        const val MAX_ROWS = 6

        /** 범위를 벗어나거나 앞 버튼과 겹치는 버튼은 버린다 — 칸 하나에 버튼 둘이면 어느 것을 누를지 모른다. */
        fun load(id: String, config: YamlConfiguration): MenuDef {
            val rows = config.getInt("rows", 6).coerceIn(MIN_ROWS, MAX_ROWS)
            val kept = ArrayList<ButtonDef>()
            val rejected = LinkedHashMap<String, ConfigurationSection>()
            val section = config.getConfigurationSection("buttons")
            if (section != null) {
                for (key in section.getKeys(false)) {
                    val raw = section.getConfigurationSection(key) ?: continue
                    val button = ButtonDef.load(key, raw)
                    if (button == null || !button.fits(rows) || kept.any { it.overlaps(button) }) {
                        rejected[key] = raw
                        continue
                    }
                    kept += button
                }
            }
            return MenuDef(id, config.getString("title").orEmpty(), rows, kept, rejected)
        }
    }
}
