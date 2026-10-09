package com.isra16.movienight.net

import org.json.JSONObject

/*
 * Lógica pura de la Fase 4B (usar un video de la biblioteca en una sala): sin imports de Android, para
 * probarla como JVM puro. Lo que toca red y pantalla vive en `HomeViewModel` y `RoomViewModel`.
 *
 * Contrato verificado en `movienight` (rama plan-produccion), server.js + lib/hostAuth.js:
 *
 *  - `POST /create-room-from-upload`, body `{ filename, password? }` (`filename` es la key del bucket, la
 *    misma que devuelve el presign o lista `GET /api/uploads`). Pasa por `requireUploadAuth`: alcanza la
 *    sesión de la cuenta (la que usa la app). Responde `{ roomId, hostToken }`. Valida el contenido del
 *    video leyendo unos KB del bucket; si no es un video de verdad, lo BORRA de la biblioteca y contesta 400.
 *    Errores: 400 (`Ese archivo no existe` o `El video no pasó la validación de contenido: ...`), 401, 429
 *    (límite de intentos de contraseña, solo sin sesión), 502 (no se pudo consultar R2).
 *    Con sesión iniciada la sala queda con dueño (`ownerUserId`).
 *
 *  - `POST /room/:id/change-video-from-upload`, body `{ filename, hostToken? }`. NO pasa por
 *    `requireUploadAuth`: la autoriza `isRoomOwner`, que mira el DUEÑO de la sala, no quién es host ahora
 *    mismo. Si la sala tiene dueño (se creó con sesión, como hace la app y la web con cuenta), solo vale
 *    la sesión de esa cuenta y el `hostToken` se ignora; si no tiene dueño (sala anónima), vale el
 *    `hostToken` (el server se lo manda al host en `host-status`). Responde `{ ok: true }`.
 *    Errores: 400 (los mismos dos de arriba, con el mismo borrado), 403 (`No autorizado`), 404 (`Sala no
 *    existe`), 502.
 *    Un host que recibió el rol por traspaso pero no es el dueño recibe 403: pasa de verdad.
 *
 *  - Qué ven los demás: el server pone `videoPosition` en `{ time: 0, paused: true }`, manda un mensaje de
 *    sistema al chat y emite `video-changed { videoFile }` a TODA la sala, quien lo pidió incluido. La app
 *    ya sabía recibirlo desde la 3A (`RoomViewModel`: recarga forzada, en pausa desde el segundo 0).
 */

/** Por qué falló crear la sala o cambiar el video, y qué conviene hacer. */
data class RoomVideoFailure(
    val message: String,
    /** `true` si tiene sentido ofrecer "Reintentar" con el mismo video. */
    val canRetry: Boolean,
    /** `true` si la biblioteca pudo cambiar (el server borra un video que no pasa la validación): conviene recargarla. */
    val libraryChanged: Boolean = false,
)

sealed interface CreateRoomResult {
    data class Ok(val roomId: String) : CreateRoomResult
    data class Failed(val failure: RoomVideoFailure) : CreateRoomResult
}

sealed interface ChangeVideoResult {
    data object Ok : ChangeVideoResult
    data class Failed(val failure: RoomVideoFailure) : ChangeVideoResult
}

/** Ruta de `POST /create-room-from-upload`. */
const val CREATE_ROOM_FROM_UPLOAD_PATH = "/create-room-from-upload"

/** Ruta de `POST /room/:id/change-video-from-upload` (el id de sala es hexadecimal: no necesita escape). */
fun changeVideoPath(roomId: String): String = "/room/$roomId/change-video-from-upload"

/** Cuerpo de `create-room-from-upload`. [password] vacía o en blanco = sala sin contraseña (no se manda). */
fun createRoomBody(filename: String, password: String): JSONObject {
    val body = JSONObject().put("filename", filename)
    val trimmed = password.trim()
    if (trimmed.isNotEmpty()) body.put("password", trimmed)
    return body
}

/** Cuerpo de `change-video-from-upload`. [hostToken] solo cuenta en salas sin dueño; si no hay, no se manda. */
fun changeVideoBody(filename: String, hostToken: String?): JSONObject {
    val body = JSONObject().put("filename", filename)
    if (!hostToken.isNullOrEmpty()) body.put("hostToken", hostToken)
    return body
}

/** Interpreta la respuesta de `POST /create-room-from-upload`. [code] `-1` = sin respuesta (red). */
fun interpretCreateRoom(code: Int, body: String): CreateRoomResult {
    if (code == 200) {
        val roomId = parseCreatedRoomId(body)
        return if (roomId != null) {
            CreateRoomResult.Ok(roomId)
        } else {
            CreateRoomResult.Failed(
                RoomVideoFailure("El servidor respondió algo inesperado al crear la sala. Intentá de nuevo.", canRetry = true),
            )
        }
    }
    return CreateRoomResult.Failed(failureFromRoomVideoCall(code, body))
}

/** Interpreta la respuesta de `POST /room/:id/change-video-from-upload`. Cualquier 200 es éxito (el server contesta `{ ok: true }`). */
fun interpretChangeVideo(code: Int, body: String): ChangeVideoResult {
    if (code == 200) return ChangeVideoResult.Ok
    val failure = when (code) {
        403 -> RoomVideoFailure(
            "Solo quien creó la sala puede cambiarle el video, y esta cuenta no la creó (aunque ahora seas el host). " +
                "Pedile a esa persona que lo cambie.",
            canRetry = false,
        )
        404 -> RoomVideoFailure("Esa sala ya no existe o se cerró.", canRetry = false)
        else -> failureFromRoomVideoCall(code, body)
    }
    return ChangeVideoResult.Failed(failure)
}

/** Errores comunes a las dos rutas. */
private fun failureFromRoomVideoCall(code: Int, body: String): RoomVideoFailure {
    val server = serverErrorMessage(body)
    return when (code) {
        // 400 en estas dos rutas siempre habla del archivo (no existe, o no es un video): el server pudo haberlo borrado.
        400 -> RoomVideoFailure(apiErrorMessage(code, server), canRetry = false, libraryChanged = true)
        401 -> RoomVideoFailure(apiErrorMessage(code, server), canRetry = false)
        else -> RoomVideoFailure(
            apiErrorMessage(code, server),
            canRetry = code == -1 || code == 429 || code in 500..599,
        )
    }
}

/**
 * Qué hay que hacer apenas el video termina de subir (o al elegir uno ya subido): se ejecuta después del
 * PUT, con la key del objeto.
 */
sealed interface FollowUpOutcome {
    /** [roomId] si el paso creó una sala. */
    data class Done(val roomId: String? = null) : FollowUpOutcome

    /** El video YA está subido a la biblioteca; falló lo que venía después. */
    data class Failed(val failure: RoomVideoFailure) : FollowUpOutcome
}

fun CreateRoomResult.toFollowUp(): FollowUpOutcome = when (this) {
    is CreateRoomResult.Ok -> FollowUpOutcome.Done(roomId)
    is CreateRoomResult.Failed -> FollowUpOutcome.Failed(failure)
}

fun ChangeVideoResult.toFollowUp(): FollowUpOutcome = when (this) {
    ChangeVideoResult.Ok -> FollowUpOutcome.Done()
    is ChangeVideoResult.Failed -> FollowUpOutcome.Failed(failure)
}
