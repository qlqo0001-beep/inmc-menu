package com.inmc.menu.util

import kr.inmc.core.util.TokenBag

/** 메시지·명령어 한 번 렌더링에 쓰이는 토큰 주머니. 한글/영문 둘 다 받는다 — 다른 INMC 플러그인들과 같은 관례. */
class Ph : TokenBag<Ph>() {

    override val aliases: Map<String, List<String>> get() = ALIASES

    fun player(name: String): Ph = put(PLAYER, name)

    fun value(text: String): Ph = put(VALUE, text)

    fun count(value: Int): Ph = put(COUNT, value.toString())

    fun amount(text: String): Ph = put(AMOUNT, text)

    fun world(name: String): Ph = put(WORLD, name)

    fun time(text: String): Ph = put(TIME, text)

    fun playtime(text: String): Ph = put(PLAYTIME, text)

    companion object {

        fun of(): Ph = Ph()

        const val PLAYER = "player"
        const val VALUE = "value"
        const val COUNT = "count"
        const val AMOUNT = "amount"
        const val WORLD = "world"
        const val TIME = "time"
        const val PLAYTIME = "playtime"

        private val ALIASES: Map<String, List<String>> = mapOf(
            PLAYTIME to listOf("{플레이타임}", "{playtime}"),
            PLAYER to listOf("{플레이어네임}", "{플레이어}", "{player}"),
            VALUE to listOf("{값}", "{value}"),
            COUNT to listOf("{개수}", "{count}"),
            AMOUNT to listOf("{금액}", "{amount}"),
            WORLD to listOf("{월드}", "{world}"),
            TIME to listOf("{시간}", "{time}"),
        )
    }
}
