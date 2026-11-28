package com.alex.minimaltv

import android.graphics.Color

/**
 * Apps com logo próprio (embutido no app, nunca buscado na internet; veja tools/logos_to_vector.py).
 * Cores e tamanhos vêm do protótipo (prototype/launcher.html). Medidas na escala 1920x1080.
 * [cardW]: largura do logo no card quadrado. [bannerH]: altura do logo no banner (card aberto).
 */
class Brand(
    val c1: Int, val c2: Int, val ink: Int,
    val cardLogo: Int, val cardW: Int,
    val bannerLogo: Int, val bannerH: Int,
)

object Brands {
    private fun c(h: String) = Color.parseColor(h)
    private val WHITE = Color.WHITE

    private val netflix = Brand(c("#E50914"), c("#6D0309"), WHITE, R.drawable.logo_netflix, 62, R.drawable.logo_netflix_w, 60)
    private val prime = Brand(c("#1399FF"), c("#08407F"), WHITE, R.drawable.logo_prime, 112, R.drawable.logo_prime_w, 62)
    private val globoplay = Brand(c("#F50234"), c("#730019"), WHITE, R.drawable.logo_globoplay, 150, R.drawable.logo_globoplay_w, 60)
    private val youtube = Brand(c("#FF3B30"), c("#7A1410"), WHITE, R.drawable.logo_youtube, 136, R.drawable.logo_youtube, 64)
    private val megafute = Brand(c("#17181A"), c("#08090A"), c("#FF5858"), R.drawable.logo_megafute, 104, R.drawable.logo_megafute_w, 54)
    private val cdntv = Brand(c("#FF6A1F"), c("#B83A06"), WHITE, R.drawable.logo_cdntv, 100, R.drawable.logo_cdntv_w, 60)
    private val downloader = Brand(c("#F47921"), c("#8F4208"), WHITE, R.drawable.logo_downloader, 112, R.drawable.logo_downloader, 60)
    private val alexa = Brand(c("#31C4F3"), c("#0B6F93"), WHITE, R.drawable.logo_alexa, 172, R.drawable.logo_alexa, 70)
    private val play = Brand(c("#34C77B"), c("#0D6B3A"), WHITE, R.drawable.logo_play, 100, R.drawable.logo_play, 64)

    private val byPkg = mapOf(
        "com.netflix.ninja" to netflix, "com.netflix.mediaclient" to netflix,
        "com.amazon.amazonvideo.livingroom" to prime, "com.amazon.avod.thirdpartyclient" to prime,
        "com.globo.globotv" to globoplay,
        "com.google.android.youtube.tv" to youtube, "com.google.android.youtube" to youtube,
        "com.esaba.downloader" to downloader,
        "com.android.vending" to play,
    )

    /** Acha o logo pelo pacote; se não achar, pelo nome do app (CDNTV, MegaTV, Alexa variam de TV pra TV). */
    fun find(pkg: String, label: String): Brand? {
        byPkg[pkg]?.let { return it }
        val n = label.lowercase()
        return when {
            "megafut" in n || "megatv" in n -> megafute
            "cdn" in n -> cdntv
            "alexa" in n -> alexa
            "downloader" in n -> downloader
            "netflix" in n -> netflix
            "globoplay" in n -> globoplay
            "prime video" in n -> prime
            "youtube" in n -> youtube
            else -> null
        }
    }
}
