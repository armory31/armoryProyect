/*
 * ARMORY Project Original (2026)
 * armory31 (github.com/armory31)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ruben.spotitube.utils

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import java.util.Locale

fun reportException(throwable: Throwable) {
    throwable.printStackTrace()
}

@Suppress("DEPRECATION")
fun setAppLocale(context: Context, locale: Locale) {
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}

fun shareItem(
    context: Context,
    type: String,
    id: String,
    title: String,
    subtitle: String? = null,
    imageUrl: String? = null
) {
    val queryBuilder = StringBuilder("t=${Uri.encode(title.take(80))}")
    queryBuilder.append("&s=${Uri.encode(subtitle.orEmpty().take(80))}")
    val encodedImage = imageUrl?.takeIf { it.isNotEmpty() }?.let(Uri::encode)
    if (encodedImage != null) {
        queryBuilder.append("&i=$encodedImage")
    }

    val link = "https://${com.ruben.spotitube.BuildConfig.SHARE_HOST}/$type/$id?$queryBuilder"
    val intent = Intent(Intent.ACTION_SEND).apply {
        this.type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
