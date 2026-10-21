package com.jaivish.gowork.core

object KnownApps {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"

    /** Time-sink apps shown at the top of the picker when they are installed. */
    val suggestions = listOf(
        INSTAGRAM,
        YOUTUBE,
        "com.instagram.barcelona", // Threads
        "com.facebook.katana", // Facebook
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok (Asia)
        "com.snapchat.android",
        "com.twitter.android", // X
        "com.reddit.frontpage",
        "in.mohalla.sharechat",
        "in.mohalla.video", // Moj
        "com.eterno.shortvideos", // Josh
        "com.instagram.lite",
        "com.facebook.lite",
        "app.revanced.android.youtube",
    )

    fun fallbackLabel(pkg: String): String = when (pkg) {
        INSTAGRAM -> "Instagram"
        YOUTUBE -> "YouTube"
        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
