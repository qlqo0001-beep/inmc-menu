package com.inmc.menu.menu

import com.inmc.menu.Hub
import kr.inmc.core.store.YamlFolder
import org.bukkit.configuration.file.YamlConfiguration

/**
 * 메뉴 전부 — `menus/<id>.yml`. 메인 스레드에서만 고친다.
 *
 * 기본 메뉴는 **폴더에 파일이 하나도 없을 때(처음 설치)만** 깐다 — 관리자가 지운 기본 메뉴를 다시 살리지 않게.
 */
class MenuStore(private val hub: Hub) {

    private val folder = YamlFolder(hub.io, hub.logger, "menus", HEADER, "메뉴")

    private val menus = LinkedHashMap<String, MenuDef>()

    /** 켤 때 — 그 자리에서 읽는다(첫 Shift+F 전에 있어야 한다). */
    fun loadNow() {
        val first = folder.folder.listFiles { f -> f.isFile && f.name.endsWith(".yml") }.isNullOrEmpty()
        menus.clear()
        if (first) {
            for (def in DefaultMenus.all()) put(def)
            flushBlocking()
            hub.logger.info("기본 메뉴 ${menus.size}개를 깔았습니다(menus/).")
            return
        }
        for ((id, def) in folder.readAll { id, config -> MenuDef.load(id, config) }) {
            menus[id] = def
            if (def.rejected.isNotEmpty()) {
                hub.logger.warning("메뉴 '$id' 의 버튼 ${def.rejected.keys} 를 건너뜁니다 — 메뉴 밖이거나 다른 버튼과 겹칩니다(파일에는 남겨 둡니다).")
            }
        }
    }

    fun get(id: String): MenuDef? = menus[id]

    fun all(): Collection<MenuDef> = menus.values

    /** 넣거나 바꾼다(파일은 저장 틱에). */
    fun put(def: MenuDef) {
        menus[def.id] = def
        folder.markDirty(def.id)
    }

    fun delete(id: String) {
        menus.remove(id)
        folder.deleteFile(id)
    }

    fun flush() = folder.flushDirty(::render)

    fun flushBlocking() = folder.flushDirtyBlocking(::render)

    private fun render(id: String): YamlConfiguration? = menus[id]?.save()

    private companion object {
        const val HEADER = "inmc-menu 메뉴. 게임 안 /메뉴 편집 으로 고치세요 — 서버가 켜져 있는 동안 손으로 고치면 덮어씁니다."
    }
}
