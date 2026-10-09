package com.isra16.movienight.net

/**
 * Acceso al panel de administración web desde la app (Fase 6B, opción (a): abrirlo en el navegador).
 *
 * El server NO dice en `GET /auth/me` si la cuenta es admin (solo `loggedIn`, `id` y `email`). El rol
 * vive en Postgres (`users.role`) y `requireAdmin` lo consulta en cada request a las rutas de admin. La única
 * forma de saberlo sin tocar el server es un GET de solo lectura a una ruta de admin, como hace
 * `admin.html`: [ADMIN_PROBE_PATH] contesta 200 solo a un admin.
 *
 * Esto solo decide si se muestra el botón; la autorización real la sigue haciendo el server (y el
 * navegador necesita su propia sesión: la cookie de la app nunca sale de la app).
 */

/** Ruta de solo lectura y liviana que solo contesta 200 a un admin (`GET /admin/stats`). */
const val ADMIN_PROBE_PATH = "/admin/stats"

/** Página del panel: un archivo estático (`public/admin.html`), no hay ruta `/admin`. */
const val ADMIN_PANEL_PATH = "/admin.html"

/** Lo que se sabe de la cuenta actual respecto del panel. */
enum class AdminAccess {
    /** Todavía no se sabe, o la última consulta no pudo responder (red, 5xx). No se muestra nada. */
    UNKNOWN,

    /** El server confirmó que la cuenta es admin. */
    ADMIN,

    /** El server confirmó que no: cuenta normal, sin sesión o servidor sin cuentas (Postgres). */
    NOT_ADMIN,
}

/**
 * Interpreta la respuesta de [ADMIN_PROBE_PATH]. Códigos verificados en `lib/adminAuth.js`: 200 admin,
 * 403 cuenta normal, 401 sin sesión, 404 server sin Postgres, 500 error al consultar el rol.
 *
 * Un 200 solo cuenta si el cuerpo es el JSON esperado (trae `activeRooms`): un 200 con una página HTML
 * (URL equivocada, un proxy) no debe mostrar el botón.
 */
fun adminAccessFrom(code: Int, body: String): AdminAccess = when (code) {
    200 -> if (parseJsonObject(body)?.has("activeRooms") == true) AdminAccess.ADMIN else AdminAccess.UNKNOWN
    401, 403, 404 -> AdminAccess.NOT_ADMIN
    else -> AdminAccess.UNKNOWN // -1 (sin red), 5xx, 429, etc.: no se sabe
}

/**
 * Estado a guardar tras una consulta nueva. Una respuesta que no se pudo interpretar ([AdminAccess.UNKNOWN],
 * por ejemplo un corte de red momentáneo) no borra lo que ya se sabía; una respuesta clara sí lo reemplaza
 * (así, si le quitan el rol a la cuenta, el botón desaparece en la próxima consulta).
 */
fun mergeAdminAccess(previous: AdminAccess, latest: AdminAccess): AdminAccess =
    if (latest == AdminAccess.UNKNOWN) previous else latest

/** El botón solo se ofrece si el server confirmó que la cuenta es admin. */
fun shouldShowAdminAccess(access: AdminAccess): Boolean = access == AdminAccess.ADMIN

/** URL del panel a partir de la URL del server (`https://host[:puerto]`, sin barra final). */
fun adminPanelUrl(baseUrl: String): String = baseUrl.trimEnd('/') + ADMIN_PANEL_PATH

/** Aviso cuando el teléfono no tiene ningún navegador que abra el link. */
const val ADMIN_NO_BROWSER_MESSAGE = "No se encontró un navegador para abrir el panel."
