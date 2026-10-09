package com.isra16.movienight.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Abre [url] en el navegador del teléfono con un `ACTION_VIEW` (sin librerías nuevas). Solo le pasa el
 * link: la cookie de sesión de la app (`PersistentCookieJar`) no sale de la app, así que el navegador
 * pide su propio inicio de sesión. Devuelve `false` si no hay ningún navegador que lo abra.
 */
fun openInBrowser(context: Context, url: String): Boolean =
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
