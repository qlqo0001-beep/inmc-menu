package com.inmc.menu.gui

import com.inmc.menu.Hub
import com.inmc.menu.pack.MenuArt
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.integration.PlayerSettings
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 개인 설정 — core 창구([PlayerSettings])에 올라온 설정 전부를 **올린 플러그인별로** 모아 그린다. 이 화면은 설정의 뜻을 모른다.
 * 켜고 끄기는 클릭, 고르기는 좌클릭 다음·우클릭 이전. 권한이 필요한데 없으면 잠금(기본값으로 보임).
 */
class SettingsMenu(
    hub: Hub,
    viewer: Player,
    private var page: Int = 0,
    override val back: (() -> Unit)? = { com.inmc.menu.menu.Actions.openMain(hub, viewer) },
    /** 리소스팩 화면이면 칸 격자 배경. */
    private val grid: Char? = hub.art.gridFor(viewer, SIZE / 9),
) : Menu(hub, viewer, SIZE, MenuArt.titled(grid, "개인 설정", viewer)) {

    override fun draw() {
        clear()
        // 올린 플러그인끼리 붙여 둔다(올린 순서는 그 안에서 지킨다).
        val all = PlayerSettings.all().withIndex().sortedWith(compareBy({ ownerOrder(it.value.owner) }, { it.index })).map { it.value }
        page = Paging.clamp(page, all.size)
        for ((slot, setting) in Paging.slice(all, page).withIndex()) {
            set(slot, icon(setting)) { event ->
                if (!PlayerSettings.allowed(viewer, setting)) {
                    hub.messages.send(viewer, "setting-locked")
                    return@set
                }
                when (val kind = setting.kind) {
                    is PlayerSettings.Toggle -> PlayerSettings.set(viewer, setting.key, !PlayerSettings.enabled(viewer, setting.key, kind.default))
                    is PlayerSettings.Choice -> {
                        val ids = kind.options.map { it.first }
                        val index = ids.indexOf(PlayerSettings.choice(viewer, setting.key, kind.default)).coerceAtLeast(0)
                        val next = if (event.isRightClick) (index - 1 + ids.size) % ids.size else (index + 1) % ids.size
                        PlayerSettings.set(viewer, setting.key, ids[next])
                    }
                }
                refresh()
            }
        }
        if (all.isEmpty()) set(SLOT_EMPTY, Icon.of(org.bukkit.Material.GRAY_DYE, "<gray>바꿀 수 있는 설정이 없습니다.</gray>"))
        if (grid == null) fillEmpty(Icon.FILLER)
        pager(page, all.size) { page = it; refresh() }
        navigation()
    }

    /** 메뉴 → 공용 → 그 밖은 이름순. */
    private fun ownerOrder(owner: String): String = when (owner) {
        com.inmc.menu.settings.MenuSettings.OWNER -> "0"
        "공용" -> "1"
        else -> "2$owner"
    }

    private fun icon(setting: PlayerSettings.Setting): ItemStack {
        val allowed = PlayerSettings.allowed(viewer, setting)
        val lore = buildList {
            add("<dark_gray>${setting.owner}</dark_gray>")
            setting.description.forEach { add("<gray>$it</gray>") }
            add("")
            when (val kind = setting.kind) {
                is PlayerSettings.Toggle -> {
                    add("<gray>지금: </gray>" + Icon.toggle(PlayerSettings.enabled(viewer, setting.key, kind.default)))
                    if (allowed) add("<yellow>▶ 클릭: 바꾸기</yellow>")
                }
                is PlayerSettings.Choice -> {
                    val current = PlayerSettings.choice(viewer, setting.key, kind.default)
                    kind.options.forEach { (id, label) -> add(if (id == current) "<green>▶ $label</green>" else "<dark_gray>· $label</dark_gray>") }
                    if (allowed) add("<yellow>▶ 좌클릭: 다음 · 우클릭: 이전</yellow>")
                }
            }
            if (!allowed) add("<red>🔒 권한이 없습니다</red>")
        }
        val on = (setting.kind as? PlayerSettings.Toggle)?.let { PlayerSettings.enabled(viewer, setting.key, it.default) } ?: false
        val stack = Icon.of(setting.icon, "<white>${setting.label}</white>", lore)
        if (on && allowed) stack.editMeta { it.setEnchantmentGlintOverride(true) }
        return stack
    }

    companion object {
        const val SIZE = 54
        const val SLOT_EMPTY = 22
    }
}
