package com.inmc.menu.gui

import com.inmc.menu.Hub
import com.inmc.menu.util.Ph
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * core [kr.inmc.core.gui.Menu] 에 이 플러그인의 로케이터와 보는 사람을 붙인 얇은 층(상점·드랍 `gui/Menu.kt` 와 같은 모양).
 * 제목은 글자(MiniMessage)나 이미 만든 [Component] — 리소스팩 화면은 제목이 그림 글자다.
 *
 * 값 입력은 채팅이 아니라 **입력창(Dialog)** — [ask].
 */
abstract class Menu(
    protected val hub: Hub,
    protected val viewer: Player,
    size: Int,
    title: Component,
) : kr.inmc.core.gui.Menu(size, title) {

    constructor(hub: Hub, viewer: Player, size: Int, title: String) : this(hub, viewer, size, Text.renderFlat(title))

    override val owner: Any get() = hub

    /** 뒤로 버튼이 여는 화면. null 이면 뒤로 버튼이 없다. */
    protected open val back: (() -> Unit)? = null

    protected fun navigation(backSlot: Int = Paging.SLOT_BACK, closeSlot: Int = Paging.SLOT_CLOSE) {
        back?.let { go -> set(backSlot, Icon.back()) { go() } }
        set(closeSlot, Icon.close()) { viewer.closeInventory() }
    }

    fun show() = open(viewer)

    /** 입력창을 띄운다. 확인하면 [onSubmit] 뒤에 이 화면을 다시 연다(그 안에서 다른 화면을 열면 그쪽이 이긴다). */
    protected fun ask(form: DialogForm, reopen: () -> Unit = { show() }, onSubmit: (DialogForm.Values) -> Unit) {
        form.show(hub.plugin, viewer, onCancel = { reopen() }) { _, values ->
            onSubmit(values)
            if (viewer.openInventory.topInventory.holder !is kr.inmc.core.gui.Menu) reopen()
        }
    }

    protected fun pager(page: Int, total: Int, perPage: Int = Paging.PER_PAGE, go: (Int) -> Unit) {
        val pages = Paging.pageCount(total, perPage)
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { go(page - 1) }
        if (page < pages - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { go(page + 1) }
    }

    protected fun toggleIcon(name: String, value: Boolean, vararg lore: String): ItemStack =
        Icon.of(Icon.toggleMaterial(value), "<yellow>$name: </yellow>" + Icon.toggle(value), lore.toList() + listOf("", "<gray>클릭해서 바꾸기</gray>"))

    protected fun valueIcon(material: Material, name: String, value: String, vararg lore: String): ItemStack =
        Icon.of(material, "<yellow>$name: </yellow><white>$value</white>", lore.toList() + listOf("", "<gray>클릭해서 바꾸기</gray>"))

    companion object {

        /**
         * [stack] 에 보는 사람 기준으로 이름·설명을 단다 — 자리표시({플레이어}·{플레이타임})와 PlaceholderAPI 가 그 사람으로 풀린다.
         * 기울임을 끈다(바닐라는 이름 붙인 아이템을 기울여 그린다).
         */
        fun named(stack: ItemStack, name: String, lore: List<String>, ph: Ph?, viewer: Player?): ItemStack {
            stack.editMeta { meta ->
                meta.displayName(Text.renderFlat(name, ph, viewer).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                meta.lore(if (lore.isEmpty()) null else Text.renderLore(lore, ph, viewer).map { it.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE) })
            }
            return stack
        }
    }
}
