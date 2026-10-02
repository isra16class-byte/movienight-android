# 🔌 Contrato de API — MovieNight Android ↔ Backend

Este documento lista, en un solo lugar, exactamente qué rutas HTTP y eventos
de Socket.io del backend (`server.js`, repo `movienight`) va a consumir esta
app. Es de solo lectura respecto al backend — **no se modifica nada acá**,
solo se documenta el contrato tal como ya existe, para no tener que releer
todo `server.js` cada vez que se implementa algo nuevo del lado Android.

Si algún día el backend cambia algo de esto, hay que revisar este archivo y
actualizarlo — y viceversa, si la app necesita algo que el backend no
expone, es acá donde se anota como pendiente antes de decidir si hace falta
tocar `server.js`.

**Fuente**: `movienight/server.js` de la rama **`plan-produccion`** (commit `a9bca2a`),
revisado directamente el 2026-09-26 y re-verificado ruta por ruta el 2026-09-28.

> ⚠️ **La rama `main` de `movienight` NO tiene nada de esto** (sin `/auth/*`, sin sesiones, sin
> presign, sin `/health`). Esta app solo funciona contra un servidor que corra `plan-produccion`
> **y** tenga `DATABASE_URL` (Postgres) configurada — sin eso `/auth/*` responde 404.

**Versiones del backend**: `socket.io 4.8.3`, `engine.io 6.6.9` (protocolo EIO v4). Cliente Android:
`io.socket:socket.io-client:2.1.2` (la serie 2.x es la compatible con servidores 3.x/4.x).

---

## Autenticación

Cookie `httpOnly` `movienight.sid` — no hay token. El cliente Android necesita
un `CookieJar` persistente (ej. `okhttp3.JavaNetCookieJar` o uno propio sobre
`SharedPreferences`) que guarde esta cookie tras el login y la reenvíe en
**todas** las siguientes requests, incluido el handshake de Socket.io.

Atributos de la cookie (verificados en `server.js`): `httpOnly`, **`secure: true`** (solo se
reenvía por HTTPS; para pruebas locales por HTTP el server tiene que arrancar con
`SESSION_COOKIE_INSECURE=1`), `sameSite: lax`, duración 30 días con `rolling` (cada request
autenticada la renueva). `/auth/logout` la borra con `res.clearCookie`.

**Socket.IO y la cookie**: el cliente Java solo manda la cookie en el handshake si se le pasa el
mismo `OkHttpClient` (el que lleva el `CookieJar`) en `IO.Options.callFactory` **y**
`IO.Options.webSocketFactory`. Ese cliente necesita `readTimeout` largo (≥ 30 s): el long-polling
de Engine.IO deja la request colgada ~25 s y con el timeout por defecto de OkHttp (10 s) se corta.

**Rate limits**: el de login es por `ip + email`; el general de la API es de
300 requests / 5 min por IP (no aplica a `/health`, `/healthz`, `/metrics`, estáticos ni Socket.IO).

| Ruta | Método | Body | Respuesta | Notas |
|---|---|---|---|---|
| `/auth/register` | POST | `{ email, password }` | `201 { id, email }` — **no deja sesión iniciada**: el cliente tiene que hacer `/auth/login` después | 400 si falta algo o formato inválido; 409 si el email ya existe (case-insensitive); 404 si el server no tiene cuentas habilitadas (`DATABASE_URL` no configurada) |
| `/auth/login` | POST | `{ email, password }` | `200 { id, email }` + `Set-Cookie: movienight.sid` | 401 con mensaje genérico si el email no existe O la contraseña está mal (no distingue, a propósito); 429 tras 3 intentos fallidos (bloqueo 15 min, clave ip+email) |
| `/auth/logout` | POST | — | `200 { ok: true }` | Borra la sesión en Redis y expira la cookie. No requiere estar logueado. |
| `/auth/me` | GET | — | `200 { loggedIn: bool, id?, email? }` | Única forma de que el cliente sepa el estado de sesión (la cookie es httpOnly, no legible desde código) |
| `/auth/forgot-password` | POST | `{ email }` | `200 { ok, message }` siempre el mismo mensaje genérico (también si el email no existe, si hay límite de pedidos —3 por hora por ip+email— o ante un error interno) | 400 `{ error }` solo si falta el email. No confirma si el email existe (anti-enumeración). El email trae un link `<APP_BASE_URL>/reset-password.html?token=...` (vence en 1 h) |
| `/auth/reset-password` | POST | `{ token, password }` | `200 { ok: true }` | 400 `{ error }` si falta algo, la contraseña tiene menos de 8 caracteres o el token venció / ya se usó. **La app no lo llama:** el link del email se abre en el navegador y `reset-password.html` lo resuelve |

**Forma de los errores de `/auth/*`** *(verificado en `server.js` el 2026-10-02)*: siempre
`{ "error": "mensaje en español" }`. Reglas de validación: email = `^[^\s@]+@[^\s@]+\.[^\s@]+$`
(el server hace `trim()`), contraseña de mínimo **8 caracteres** (sin `trim()`). El 429 de
`/auth/login` trae además `lockedMinutes` (a veces solo el mensaje, con "15 min" fijo).

---

## Salas — rutas HTTP

| Ruta | Método | Auth requerida | Notas |
|---|---|---|---|
| `/api/uploads/presign` | POST | `requireUploadAuth` (sesión o `LIBRARY_PASSWORD`) | Devuelve URL prefirmada de R2 para subir un video directo al bucket. 404 si el server no tiene R2 configurado. |
| `/create-room` | POST | `requireUploadAuth` | Sube un video por multipart (modo disco local o streaming a R2). Sujeto al límite `413` de Cloudflare si se comparte por Tunnel proxied. |
| `/create-room-from-upload` | POST | `requireUploadAuth` | Body JSON `{ filename, password? }` (`filename` = key/nombre del video en la biblioteca). Responde `{ roomId, hostToken }`. Si hay sesión iniciada la sala queda **con dueño** (ver "Rol de host"). |
| `/room/:id/change-video` | POST | `requireUploadAuth` (además, dueño/host de la sala) | Sube un video nuevo para reemplazar el actual de la sala |
| `/room/:id/change-video-from-upload` | POST | dueño/host de la sala (no pasa por `requireUploadAuth`) | Body JSON `{ filename, hostToken? }`. 403 `No autorizado` si no eres el dueño. Responde `{ ok: true }`. |
| `/room/:id/upload-subtitle` | POST | dueño/host de la sala (no pasa por `requireUploadAuth`) | `multipart/form-data`: campo de archivo **`subtitle`** + campo `hostToken` (solo salas anónimas). Sube un `.srt`/`.vtt`, validado por estructura real. Responde `{ ok, subtitleFile }`. |
| `/room/:id` | GET | — | Devuelve la página HTML de la sala (no aplica a la app, es para el navegador) |
| `/api/room/:id` | GET | — | **Solo** `{ passwordProtected: bool }` (404 `{ error: 'not found' }` si la sala no existe). Sirve para saber si pedir contraseña antes de unirse; no devuelve video ni título. |
| `/api/uploads` | GET | `requireLibraryAuth` (sesión o `LIBRARY_PASSWORD`) | Lista videos de la biblioteca compartida. Con sesión no hace falta la contraseña; sin sesión se manda en el header `x-library-password`. |
| `/api/uploads/:filename` | DELETE | `requireLibraryAuth` | Borra un video de la biblioteca |
| `/health` (alias `/healthz`) | GET | — | Útil para un chequeo de conectividad desde la app antes de intentar conectar |

---

## Salas — eventos de Socket.io

El servidor lee la sesión también en el handshake de Socket.io
(`io.engine.use(sessionMiddleware)`) — la misma cookie del login alcanza,
no hace falta mandar nada extra en el `join-room` si ya hay sesión iniciada
(aunque `join-room` sigue aceptando `hostToken`/`userId` para el esquema
anónimo, que la app también podría necesitar soportar si el usuario entra a
una sala sin cuenta).

### Cliente → servidor

| Evento | Payload | Notas |
|---|---|---|
| `join-room` | `{ roomId, username, hostToken?, userId?, password? }` | `username` se trunca a 40 caracteres. Rate limiting de contraseña: 3 intentos → bloqueo 15 min, clave ip+roomId. |
| `sync` | `{ type: 'play'\|'pause'\|'seek'\|'heartbeat', time: number, paused?: bool }` | Solo tiene efecto si el socket es host (`socket.isHost`). `heartbeat` se manda cada 4s con `time` + `paused` juntos, como red de seguridad. |
| `chat-message` | `{ text: string, replyTo?: { user, text, isHost } }` | `text` se trunca a 500 caracteres. Rate limit: 8 mensajes / 10s por socket (no corta la conexión, solo descarta el mensaje de más y avisa con `chat-rate-limited`). |
| `typing` | *(sin payload)* | Se retransmite a los demás como `{ username }` |
| `reaction` | `emoji` (string plano) | Se retransmite tal cual a toda la sala |
| `buffering-status` | `bool` | Muestra/oculta un indicador junto al nombre en el panel de viewers |
| `kick-user` | `targetId` (socket.id) | Solo si el emisor es host |
| `toggle-mute` | `targetId` (socket.id) | Solo si el emisor es host |
| `make-host` | `targetId` (socket.id) | Solo si el emisor es host — transfiere el control remoto |

### Servidor → cliente

| Evento | Payload | Cuándo llega |
|---|---|---|
| `room-error` | string | Sala inexistente, contraseña incorrecta, rate limit alcanzado |
| `chat-history` | `Array<mensaje>` | Al hacer `join-room`, una sola vez |
| `chat-message` | `{ system: bool, user?, text, replyTo?, isHost?, userId? }` | Mensajes nuevos (de otros, del sistema, o el propio reflejado) |
| `chat-rate-limited` | `{ message }` | Solo a quien mandó de más, no se ve en el chat de nadie más |
| `host-status` | `{ isHost: bool, hostToken: string\|null }` | Al hacer join, y cada vez que cambia el rol de host |
| `room-data` | `{ videoFile, subtitleFile, position }` | Al hacer join — el estado actual del video para sincronizarse |
| `mute-status` | `{ muted: bool }` | Al ser muteado/desmuteado |
| `viewer-count` | number | Cada vez que entra/sale alguien |
| `viewer-list` | *(lista completa de viewers con nombre, host, muted, buffering)* | Se re-emite en varios eventos (join, mute, buffering, etc.) |
| `sync` | igual shape que el `sync` que manda el host | Retransmitido a todos menos al propio host |
| `video-changed` | *(nuevo videoFile)* | Al cambiar de cinta |
| `subtitle-changed` | *(nuevo subtitleFile)* | Al subir un subtítulo nuevo |
| `typing` | `{ username }` | Alguien está escribiendo |
| `reaction` | emoji (string) | Retransmitido tal cual |
| `kicked` | *(sin payload)* | Al ser expulsado — el server llama `disconnect(true)` justo después |
| `server-restarting` | *(sin payload)* | El backend está por reiniciarse (graceful shutdown) — conviene mostrar un aviso y esperar la reconexión |

---

## Cosas a tener en cuenta al implementar

- **`userId` vs `socket.id`**: `userId` es estable por dispositivo/sesión de
  navegador (se guarda en `localStorage` en la web) y se usa para mute y
  reconexión. `socket.id` cambia en cada conexión nueva. La app necesita su
  propio mecanismo de `userId` persistente (ej. generado una vez y guardado
  en `SharedPreferences`/`DataStore`), coherente con lo que espera el server.
- **Reconexión rápida**: si el socket se reconecta con el mismo `userId`
  dentro de una ventana corta, el server no repite el mensaje de "se unió a
  la sala" — bueno tenerlo en cuenta para no duplicar lógica de UI para ese
  caso.
- **Rol de host (dueño de sala)**: si la sala se creó con una sesión iniciada, queda con
  `ownerUserId` y el host se reconoce **solo por la cookie de sesión** (un `hostToken` ya no
  alcanza). Si se creó sin sesión (anónima), vale el `hostToken` que devuelve la creación, que la
  app tiene que guardar y mandar en `join-room` y en `change-video-from-upload`/`upload-subtitle`.
  Ver `isRoomOwner()` en `lib/hostAuth.js`.
- **Solo el host puede mandar `sync` con efecto real** — el servidor
  descarta el evento si `socket.isHost` es falso, así que la UI de controles
  del reproductor en la app debería directamente no ofrecer controles de
  play/pause/seek a un invitado (igual que hace `room.html` con
  `video.controls = false`), en vez de confiar en que el rechazo del server
  alcance como única barrera.
- **Payload de subtítulos y video en `/room/:id/upload-subtitle`,
  `/room/:id/change-video`**: son `multipart/form-data`, no JSON — revisar el
  nombre exacto del campo (`video`, `subtitle`) en `server.js` al momento de
  implementar esa parte, si no coincide actualizar esta tabla.

---

*Este documento se actualiza cada vez que se implementa una integración
nueva contra el backend, o si algo de lo documentado acá resulta no
coincidir con el comportamiento real observado (en cuyo caso el
comportamiento real gana, y se corrige acá).*
