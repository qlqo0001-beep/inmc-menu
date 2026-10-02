package com.inmc.menu.config

import org.bukkit.configuration.file.YamlConfiguration

/**
 * `config.yml`. 화면에서 고치면 [toYaml] 로 통째로 다시 쓴다.
 *
 * 월드 이름은 **목록**으로 적는다 — 열쇠로 쓰면 이름에 점이 든 월드가 `getKeys(false)` 에서 사라진다(지뢰 1).
 */
data class MenuConfig(
    /** Shift+F 로 메뉴(서버 전체). 끄면 `/메뉴` 만. 사람마다 끄는 것은 개인 설정. */
    val hotkey: Boolean = true,
    /** Shift+F 와 `/메뉴` 가 여는 메뉴. */
    val mainMenu: String = "main",
    /** "다른 플레이어 숨기기"가 동작하는 월드(관리자가 정함). 비우면 어디서도 안 숨긴다. */
    val hideWorlds: List<String> = emptyList(),
    /** 리소스팩 화면(팩을 받은 사람에게만). 끄면 모두 일반 아이콘 화면. */
    val packEnabled: Boolean = true,
    /** 그림·글꼴을 써 넣을 곳 — 커스텀아이템이 합치는 팩 소스 폴더. 서버 폴더 기준. */
    val packExport: String = DEFAULT_EXPORT,
    /** 랜덤 TP 의 서버 전체 한도(월드마다의 설정은 `rtp-worlds.yml`). */
    val rtp: Rtp = Rtp(),
    /** 출석의 하루 경계·안내(출석판은 `attendance/`). */
    val attendance: Attendance = Attendance(),
) {

    data class Attendance(
        /** 하루를 세는 시간대(`Asia/Seoul` …). */
        val timezone: String = "Asia/Seoul",
        /** 날이 바뀌는 시각(0~23시). */
        val resetHour: Int = 0,
        /** 수동 출석판을 아직 안 눌렀으면 들어올 때·날이 바뀔 때 알린다. */
        val remind: Boolean = true,
    )

    data class Rtp(
        /** 서버 전체에서 동시에 자리를 찾는 수 — 넘으면 줄을 선다(청크 생성이 겹겹이 쌓이지 않게). */
        val maxSearches: Int = 2,
        /** 줄 길이 — 넘으면 "잠시 뒤에". */
        val queueSize: Int = 10,
        /** 평균 틱 시간(ms)이 이걸 넘으면 새 찾기를 잠시 받지 않는다. 0 = 안 봄. */
        val busyMspt: Double = 45.0,
        /** 찾기 한 번에 읽어 볼 청크 수 상한 — 넘으면 "이번엔 못 찾았습니다"(비용·쿨타임 안 씀). */
        val chunkTries: Int = 8,
    )

    fun toYaml(): YamlConfiguration = YamlConfiguration().also { y ->
        y.options().setHeader(HEADER.lines())
        y.set("hotkey", hotkey)
        y.set("main-menu", mainMenu)
        y.set("hide-players-worlds", hideWorlds)
        y.set("pack.enabled", packEnabled)
        y.set("pack.export-to", packExport)
        y.set("rtp.max-searches", rtp.maxSearches)
        y.set("rtp.queue-size", rtp.queueSize)
        y.set("rtp.busy-mspt", rtp.busyMspt)
        y.set("rtp.chunk-tries", rtp.chunkTries)
        y.set("attendance.timezone", attendance.timezone)
        y.set("attendance.reset-hour", attendance.resetHour)
        y.set("attendance.remind", attendance.remind)
    }

    companion object {

        const val DEFAULT_EXPORT = "plugins/inmc-customitems/pack/sources/40-inmc-menu"

        val HEADER = """
            inmc-menu 설정. 게임 안 /메뉴 관리 에서 고치는 것이 편합니다(고치면 이 파일을 다시 씁니다).
            메뉴의 버튼은 menus/ 에 있고 /메뉴 편집 으로 고칩니다.
        """.trimIndent()

        fun from(config: YamlConfiguration): MenuConfig = MenuConfig(
            hotkey = config.getBoolean("hotkey", true),
            mainMenu = config.getString("main-menu").orEmpty().ifBlank { "main" },
            hideWorlds = config.getStringList("hide-players-worlds").map(String::trim).filter(String::isNotEmpty).distinct(),
            packEnabled = config.getBoolean("pack.enabled", true),
            packExport = config.getString("pack.export-to").orEmpty().ifBlank { DEFAULT_EXPORT },
            rtp = Rtp(
                maxSearches = config.getInt("rtp.max-searches", 2).coerceIn(1, 16),
                queueSize = config.getInt("rtp.queue-size", 10).coerceIn(0, 200),
                busyMspt = config.getDouble("rtp.busy-mspt", 45.0).coerceAtLeast(0.0),
                chunkTries = config.getInt("rtp.chunk-tries", 8).coerceIn(1, 64),
            ),
            attendance = Attendance(
                timezone = config.getString("attendance.timezone").orEmpty().ifBlank { "Asia/Seoul" },
                resetHour = config.getInt("attendance.reset-hour", 0).coerceIn(0, 23),
                remind = config.getBoolean("attendance.remind", true),
            ),
        )
    }
}
