plugins {
    id("inmc.paper-plugin")
}

group = "com.inmc.menu"
version = "1.0.0"

inmc {
    paper = "26.2"
    pluginName = "inmc-menu"
}

dependencies {
    compileOnly(libs.placeholderapi) { isTransitive = false }
    // 랜덤 TP 가 WorldGuard 지역을 피한다(urb 와 같은 방식 — WG 는 직접, Lands 는 리플렉션).
    compileOnly(libs.worldguard.bukkit) { isTransitive = false }
    compileOnly(libs.worldguard.core) { isTransitive = false }
    compileOnly(libs.worldedit.core) { isTransitive = false }
    compileOnly(libs.worldedit.bukkit) { isTransitive = false }
}
