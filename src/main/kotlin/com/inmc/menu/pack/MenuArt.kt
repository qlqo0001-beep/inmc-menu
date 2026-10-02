package com.inmc.menu.pack

import com.inmc.menu.Hub
import com.inmc.menu.util.Ph
import kr.inmc.core.util.Text
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * 리소스팩 화면의 그림 — 메뉴마다 배경 글자 하나, 내장 화면은 줄 수마다 칸 격자 하나.
 *
 * - [draw]: 메뉴의 배치에서 배경을 그려([ArtRenderer]) 커스텀아이템의 팩 소스에 쓰고([PackExport]) 팩을 다시 만들게 한다.
 * - **팩에 들어갔는지 확인한 뒤에만 쓴다**(`hub.artReady`): 커스텀아이템이 만든 `pack.zip` 안의 `assets/inmc/font/menu.json` 이 우리가 쓴 것과
 *   같을 때. 그 전에 그림 화면을 보이면 사람들은 네모 칸을 본다. 새 팩이 나오면 그보다 먼저 팩을 받은 사람은 받은 팩 기록(`packLoaded`)에서
 *   빠진다 — 옛 팩을 가진 사람은 다시 받을 때까지 일반 화면.
 * - 글자는 **그린 때의 배치**에 묶인다 — 그린 뒤 메뉴를 고쳤으면 다시 그릴 때까지 그 메뉴는 일반 화면([backgroundOf] 가 null).
 * - 메뉴마다 글자 번호는 한 번 정하면 유지한다(기록 `drawn.yml`) — 다시 그려도 다른 메뉴의 글자가 밀리지 않게.
 */
class MenuArt(private val hub: Hub) {

    /** 메뉴 id → (배경 글자, 그 그림을 그린 배치의 지문). */
    private val drawn = ConcurrentHashMap<String, Pair<Char, Int>>()

    @Volatile
    private var font: BdfFont? = null

    @Volatile
    private var fontLoaded = false

    private val manifest: File get() = hub.io.file("drawn.yml")

    /** 서버 폴더 기준 팩 소스 폴더. */
    val exportRoot: File get() = File(serverRoot(), hub.config.packExport)

    /** 커스텀아이템이 만드는 팩 — 소스 폴더의 두 단계 위 `output/pack.zip`. */
    private val packZip: File get() = File(exportRoot.parentFile?.parentFile ?: exportRoot, "output/pack.zip")

    private fun serverRoot(): File = hub.plugin.dataFolder.absoluteFile.parentFile.parentFile

    // --- 쓰는 쪽 ------------------------------------------------------------------------------

    /** 지금 메뉴의 배치가 그린 때와 같을 때만 그 배경 글자. */
    fun backgroundOf(menuId: String): Char? {
        if (!hub.artReady) return null
        val (glyph, fingerprint) = drawn[menuId] ?: return null
        val def = hub.menus.get(menuId) ?: return null
        return glyph.takeIf { fingerprint == LayoutPrint.of(def) }
    }

    /** 그린 뒤 고쳤거나 아직 안 그린 메뉴 — 다시 그릴 때까지 일반 화면. */
    fun stale(): List<String> = hub.menus.all().filter { def -> drawn[def.id]?.second != LayoutPrint.of(def) }.map { it.id }.sorted()

    /** 이 메뉴의 칸 [slot] 에 놓을 그림 조각 아이템 — 그림 화면일 때만. 그린 때의 버튼 칸만 조각이 있다(배치 지문이 같으면 지금과 같다). */
    fun cellItem(menuId: String, slot: Int): org.bukkit.inventory.ItemStack? {
        val glyph = backgroundOf(menuId) ?: return null
        return Glyphs.cell(PackExport.cellName("m${glyph.code - 0xE000}", slot))
    }

    /** 기록이 옛 형식이다(칸 조각이 생기기 전 …) — 켤 때 다시 그린다. */
    @Volatile
    var outdated = false
        private set

    /** 이 사람에게 보일 내장 화면(개인 설정·출석 …)의 칸 격자 배경 — 그림 화면이 아니면 null. */
    fun gridFor(viewer: Player, rows: Int): Char? = if (hub.usesPack(viewer)) grid(rows) else null

    // --- 기록 ---------------------------------------------------------------------------------

    /** 켤 때 — 기록을 읽고 팩에 들어갔는지 본다. */
    fun load() {
        drawn.clear()
        val yaml = if (manifest.isFile) YamlConfiguration.loadConfiguration(manifest) else return
        outdated = yaml.getInt("version", 1) < FORMAT
        for (entry in yaml.getMapList("menus")) {
            val id = entry["id"]?.toString() ?: continue
            val index = (entry["char"] as? Number)?.toInt() ?: continue
            val print = (entry["print"] as? Number)?.toInt() ?: continue
            drawn[id] = Glyphs.background(index) to print
        }
        // 옛 형식이면 지금 팩에 칸 조각이 없다 — 다시 그려 팩에 들어갈 때까지 일반 화면(보라·검정 칸을 보이지 않게).
        hub.artReady = !outdated && packHasOurFont()
    }

    val hasManifest: Boolean get() = manifest.isFile

    private fun saveManifest(entries: Map<String, Pair<Int, Int>>) {
        val yaml = YamlConfiguration()
        yaml.options().setHeader(listOf("inmc-menu 가 그린 배경의 기록 — 메뉴마다 글자 번호와 그린 배치의 지문. 손대지 마세요."))
        yaml.set("version", FORMAT)
        yaml.set("menus", entries.map { (id, v) -> linkedMapOf("id" to id, "char" to v.first, "print" to v.second) })
        yaml.save(manifest)
    }

    // --- 그리기 -------------------------------------------------------------------------------

    /** 그리기·팩 다시 만들기가 도는 중 — 두 번 겹쳐 돌면 팩 소스가 반쯤 쓰인 채 팩이 만들어진다. */
    @Volatile
    var busy = false
        private set

    /**
     * 그림을 다시 만들어 팩에 넣는다 — [draw] 뒤 [rebuildPack]. 진행을 [target] 에게 알린다. 이미 도는 중이면 false.
     */
    fun refresh(target: CommandSender): Boolean {
        if (busy) return false
        busy = true
        draw { error, count ->
            if (error != null) {
                busy = false
                hub.messages.send(target, "art-failed", Ph.of().value(error))
                return@draw
            }
            hub.messages.send(target, "art-drawn", Ph.of().count(count))
            rebuildPack { ok ->
                busy = false
                when (ok) {
                    true -> hub.messages.send(target, "art-ready")
                    false -> hub.messages.send(target, "art-timeout")
                    null -> hub.messages.send(target, "art-no-pack-tool", Ph.of().value(hub.config.packExport))
                }
            }
        }
        return true
    }

    /**
     * 지금 메뉴 전부와 격자(1~6줄)를 그려 쓴다. 그림·파일은 워커에서, 결과 반영은 메인에서([then] — 오류, 그린 메뉴 수).
     * 메뉴 정의는 불변이라 메인에서 뜬 목록을 워커가 읽어도 된다.
     */
    private fun draw(then: (String?, Int) -> Unit) {
        val menus = hub.menus.all().toList()
        val previous = drawn.mapValues { (it.value.first.code - 0xE000) }
        val assignments = assign(menus.map { it.id }, previous)
        val root = exportRoot
        val artDir = hub.io.file("art").apply { mkdirs() }
        hub.io.async({
            runCatching {
                val renderer = ArtRenderer(loadFont()) { name -> custom(artDir, name) }
                val backgrounds = ArrayList<PackExport.Background>()
                for (rows in 1..6) backgrounds += PackExport.Background(grid(rows), "grid$rows", renderer.grid(rows))
                for (def in menus) {
                    val index = assignments.getValue(def.id)
                    val cells = def.buttons.filter { it.special == null }.flatMap { it.slots() }
                    backgrounds += PackExport.Background(Glyphs.background(index), "m$index", renderer.menu(def), cells)
                }
                PackExport.write(root, backgrounds)
                saveManifest(menus.associate { it.id to (assignments.getValue(it.id) to LayoutPrint.of(it)) })
                null
            }.getOrElse { it.javaClass.simpleName + (it.message?.let { m -> ": $m" } ?: "") }
        }) { error ->
            if (error == null) {
                outdated = false
                drawn.clear()
                for (def in menus) drawn[def.id] = Glyphs.background(assignments.getValue(def.id)) to LayoutPrint.of(def)
            } else {
                hub.logger.warning("메뉴 그림을 쓰지 못했습니다: $error")
            }
            then(error, menus.size)
        }
    }

    /**
     * 커스텀아이템에게 팩을 다시 만들게 하고, 새 팩에 우리 글꼴이 들어갔는지 지켜본다(2초마다, 최대 2분). 들어가면 그림 화면을 켜고
     * 새 팩보다 먼저 팩을 받은 사람을 받은 팩 기록에서 뺀다 — 옛 팩을 가진 사람은 다시 받을 때까지 일반 화면.
     * [then]: 들어감 true · 2분 안에 못 찾음 false · 커스텀아이템 없음 null.
     */
    private fun rebuildPack(then: (Boolean?) -> Unit) {
        if (!Bukkit.getPluginManager().isPluginEnabled("inmc-customitems")) {
            then(null)
            return
        }
        hub.artReady = false
        // 그림 파일만 바뀌고 글꼴 json 은 그대로일 수 있다 — 옛 팩을 새 팩으로 착각하지 않게 이 시각 뒤에 만들어진 팩만 본다.
        // 초 단위로 내림 — 수정 시각을 초까지만 적는 파일 시스템이 있다.
        val requestedAt = System.currentTimeMillis() / 1000 * 1000
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "커스텀아이템 리팩 빌드")
        var tries = 0
        var done = false
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(hub.plugin, { task ->
            tries++
            hub.io.async({ packZip.lastModified().takeIf { it >= requestedAt && packHasOurFont() } }) { builtAt ->
                if (done) return@async
                if (builtAt != null) {
                    done = true
                    task.cancel()
                    hub.packLoaded.values.removeIf { it < builtAt }
                    hub.artReady = true
                    hub.logger.info("새 리소스팩에 메뉴 그림이 들어갔습니다 — 팩을 다시 받은 사람부터 그림 화면입니다.")
                    then(true)
                } else if (tries >= 60) {
                    done = true
                    task.cancel()
                    hub.logger.warning("새 리소스팩에서 메뉴 그림을 찾지 못했습니다(2분). /커스텀아이템 리팩 빌드 결과를 확인하세요.")
                    then(false)
                }
            }
        }, 40L, 40L)
    }

    /** 커스텀아이템의 `pack.zip` 안 우리 글꼴이 우리가 쓴 것과 같은가. 워커에서 불러도 된다. */
    fun packHasOurFont(): Boolean = runCatching {
        val ours = File(exportRoot, "assets/inmc/font/menu.json").takeIf { it.isFile }?.readText(Charsets.UTF_8) ?: return false
        if (!packZip.isFile) return false
        ZipFile(packZip).use { zip ->
            val entry = zip.getEntry("assets/inmc/font/menu.json") ?: return false
            zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) } == ours
        }
    }.getOrDefault(false)

    private fun loadFont(): BdfFont? {
        if (!fontLoaded) {
            font = BdfFont.bundled()
            fontLoaded = true
            if (font == null) hub.logger.warning("갈무리 글꼴을 읽지 못했습니다 — 버튼 이름 없이 그립니다.")
        }
        return font
    }

    private fun custom(dir: File, name: String): BufferedImage? {
        if (name.isBlank() || name.contains('/') || name.contains('\\') || name.contains("..")) return null
        val file = File(dir, "$name.png")
        return if (file.isFile) runCatching { ImageIO.read(file) }.getOrNull() else null
    }

    companion object {

        /** 기록 형식 — 2: 버튼 칸마다 그림 조각(2026-10-02). 올리면 켤 때 다시 그린다. */
        const val FORMAT = 2

        /** 메뉴 배경의 글자 번호는 [FIRST_MENU] 부터(앞은 격자 1~6줄). */
        const val FIRST_MENU = 16

        fun grid(rows: Int): Char = Glyphs.background((rows - 1).coerceIn(0, 5))

        /** 화면 제목 — 배경 글자가 있으면 그 위에([Glyphs.title]), 없으면 글자만. 자리표시·PlaceholderAPI 는 보는 사람으로. */
        fun titled(background: Char?, raw: String, viewer: Player): Component =
            Text.renderFlat(raw, Ph.of().player(viewer.name), viewer).let { text -> if (background == null) text else Glyphs.title(background, text) }

        /** 이미 정한 번호는 그대로, 새 메뉴는 비어 있는 다음 번호. 지운 메뉴의 번호는 비운다. */
        fun assign(ids: List<String>, previous: Map<String, Int>): Map<String, Int> {
            val result = LinkedHashMap<String, Int>()
            for (id in ids) previous[id]?.takeIf { it >= FIRST_MENU }?.let { result[id] = it }
            var next = FIRST_MENU
            for (id in ids.sorted()) {
                if (id in result) continue
                while (next in result.values) next++
                result[id] = next++
            }
            return result
        }
    }
}
