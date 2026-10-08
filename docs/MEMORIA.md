# 📎 Memoria del proyecto (activa) — MovieNight Android

**Leé esto primero** al retomar el proyecto (vos o una IA). Resumen corto a
propósito — el detalle histórico completo vive en `docs/historico/` (ver su `README.md`).
**Para retomar alcanza con leer la sección "Por dónde seguir"** y, si hace falta, el resto por secciones.

Cada cambio importante que se haga de ahora en adelante debería:
1. Actualizar este archivo si cambia algo esencial (arquitectura, decisiones, riesgos).
2. Agregar una entrada en `docs/CHANGELOG.md`.

---

## Qué es

App nativa Android para MovieNight: un segundo cliente para el mismo watch
party privado que ya existe como web (`https://github.com/isra16class-byte/movienight`).
No hay backend propio — habla contra el mismo servidor que la web, sin
tocarlo. Repo: `https://github.com/isra16class-byte/movienight-android`.

## Relación con el proyecto web (`movienight`)

- **Rama objetivo: `plan-produccion`.** La rama `main` de `movienight` es la versión vieja
  (sin cuentas ni `/auth/*`); esta app solo funciona contra `plan-produccion` con Postgres
  configurado. Confirmado el 2026-09-28.
- **Un solo backend, dos clientes.** El servidor (`server.js` en el repo web)
  no distingue entre un request de la app Android y uno del navegador — mismo
  JSON, mismos endpoints, mismos eventos de Socket.io. Por diseño, esta app
  **no debería requerir cambios en el servidor** salvo que aparezca un
  bloqueo real (documentado si/cuando pase, ver `docs/PLAN-PRODUCCION.md`,
  Fase 0). El detalle exacto de rutas/eventos que la app consume vive en
  `docs/API-CONTRATO.md` — para no tener que releer todo `server.js` cada vez.
- **Repos completamente separados** — sin submódulos, sin código compartido.
  Cualquier cambio acá no puede romper la web, y viceversa.
- **Autenticación**: cookie `httpOnly` (`movienight.sid`), la misma que usa
  la web — no token. Ver Fase 0 de `docs/PLAN-PRODUCCION.md` para el porqué.

## Stack

- **Lenguaje**: Kotlin.
- **UI**: Jetpack Compose (plantilla "Empty Activity" de Android Studio).
- **Package**: `com.isra16.movienight`.
- **Min SDK**: 26 (Android 8.0) — **Target SDK**: 36.
- **Reproductor de video**: Media3 (ExoPlayer) `1.10.1`, integrado en la Fase 3A (la app reproduce
  el video de la sala, sigue al host desde la 3B y lo maneja si es host desde la 3C). **No subir Media3 a 1.11.x**: se compila
  con Kotlin 2.2 y este proyecto usa 2.0.21, el compilador falla con "Internal compiler error" (error
  en `MainActivity.kt`, sin mencionar la librería). Se puede subir cuando se suba Kotlin.
- **Navegación** (2026-10-02): Navigation Compose `2.8.9` (elegida sin poder resolver
  dependencias en el entorno del asistente: si el sync de Gradle falla, es lo primero a revisar)
  con rutas de texto (sin la
  serialización de rutas tipadas, para no sumar otro plugin). **Sin Hilt**: los objetos
  compartidos viven en `AppContainer`, creado por `MovieNightApp`.
- **Networking** (decidido 2026-09-28): OkHttp `4.12.0` + `io.socket:socket.io-client:2.1.2`
  (serie 2.x = compatible con Socket.IO 3.x/4.x del server) + coroutines. Sin Retrofit por
  ahora. Las dos comparten un `PersistentCookieJar` (cookie de sesión en SharedPreferences,
  excluida de los backups).

## Estructura de archivos

```
movienight-android/
  app/
    build.gradle.kts       # applicationId, minSdk, targetSdk, dependencias de red
    src/main/java/com/isra16/movienight/
      MovieNightApp.kt      # Application: crea el AppContainer
      AppContainer.kt       # singletons: CookieJar, OkHttpClient (+ socketClient), MovieNightApi, SessionManager, UserIdStore, RoomPasswordCache
      MainActivity.kt       # monta AppRoot
      auth/                 # sesión y cuenta, sin UI salvo los ViewModels
        SessionState.kt         # Loading / LoggedOut / LoggedIn / Unreachable, y AuthResult
        SessionManager.kt       # /auth/me, login, registro (+login), forgot-password, logout
        AuthValidation.kt       # reglas de email/contraseña (las mismas del server), JVM puro
        SessionViewModel.kt     # estado de sesión para la raíz
        AuthViewModel.kt        # estado de las pantallas de login/registro/recuperar
        Username.kt             # nombre en el chat = parte local del email, JVM puro
        UserIdStore.kt          # userId por instalación (UUID en SharedPreferences)
      home/HomeViewModel.kt     # biblioteca, crear sala, unirse por código o link
      room/                     # una sala
        RoomViewModel.kt        # comprueba la sala, contraseña, socket, estado del chat; emite sync si es host (3C)
        RoomPasswordCache.kt    # pasa la contraseña de una sala recién creada (solo en memoria)
        RoomPlayer.kt           # ExoPlayer de la sala: carga el video, sigue al host (applySync) o lo maneja si es host; subtítulos y buffering; Fase 3
      ui/
        AppRoot.kt              # elige pantalla según SessionState; un NavHost por estado
        auth/                   # LoginScreen, RegisterScreen, ForgotPasswordScreen, componentes
        home/HomeScreen.kt      # unirse por código/link + biblioteca + diálogo de crear sala
        room/RoomScreen.kt      # sala: cabecera, contraseña, error, chat (el reproductor de la sala va con `RoomPlayer`, Fase 3A)
      net/                  # capa de red, sin nada de UI (pensada para reusarse)
        PersistentCookieJar.kt  # CookieJar persistente (movienight.sid)
        MovieNightApi.kt        # llamadas HTTP genéricas (get / postJson), devuelven código + cuerpo
        RoomSocket.kt           # wrapper de Socket.IO: join-room, eventos del server como RoomEvent, sendSync / sendBuffering
        RoomEvents.kt           # RoomEvent, ChatMessage, Viewer y parseServerEvent() (JVM + org.json)
        LibraryParsing.kt       # parseLibrary(), parseCreatedRoomId(), formatFileSize()
        RoomIds.kt              # extractRoomId() (código o link), isRoomPasswordError(), videoDisplayName()
        VideoUrl.kt             # resolveVideoUrl(), resolveSubtitleUrl(), shouldLoadVideo(), playbackErrorMessage()
        SyncLogic.kt            # sync: parser, planSync (desfase), HostReference, planRejoin; emitir: toSyncPayload, canEmitSync (JVM puro)
        ApiErrors.kt            # apiErrorMessage(): código HTTP -> mensaje (datos; AuthErrors es el de login)
        UrlUtils.kt             # normalizeBaseUrl()
        AuthErrors.kt           # authErrorMessage(): código HTTP -> mensaje para la persona
        JsonUtils.kt            # parseJsonObject(), serverErrorMessage()
    src/test/                # tests unitarios (JVM): validación, errores, URL, ids de sala, parsers de JSON
  local.properties.example   # plantilla de movienight.baseUrl (local.properties no se sube)
    src/debug/              # solo builds debug: permite HTTP sin TLS (pruebas locales)
  build.gradle.kts
  settings.gradle.kts
  gradle/
  docs/
    MEMORIA.md              # Este archivo
    CHANGELOG.md             # Historial de cambios
    PLAN-PRODUCCION.md      # Roadmap por fases
    API-CONTRATO.md          # Rutas HTTP y eventos de Socket.io que la app consume del backend
    historico/               # Detalle viejo archivado de este archivo (ver su README.md),
                              # mismo criterio que ya usa el repo web.
```

## Decisiones de diseño ya tomadas (detalle completo en Fase 0 del plan)

- Reusar el backend de `movienight` tal cual, sin backend propio.
- Cookie de sesión en vez de token, para no tocar el server.
- Solo Android por ahora, nativo (no Flutter/React Native, no wrapper web).
- Media3/ExoPlayer para el reproductor.
- Repos separados (web y Android no comparten código ni historial de git).
- **La app exige cuenta** (decidido 2026-10-02): sin sesión solo hay login/registro; también
  hace falta cuenta para unirse a una sala. No hay flujo anónimo ni `hostToken` en la app.
- **La URL del servidor es fija por build** (`BuildConfig.BASE_URL`), leída de
  `local.properties` (`movienight.baseUrl`, y opcional `movienight.baseUrl.debug`). Ya no hay
  campo de texto como en el spike. Sin configurar queda `https://movienight.invalid`.
- **Estado de sesión `Unreachable`**: si `/auth/me` no se puede consultar (sin red, servidor
  caído, respuesta que no es JSON) se muestra "Reintentar", no login: con una sesión guardada,
  mandar a login sería engañoso.
- **Login con cookie no guardada**: si `/auth/login` da 200 pero el CookieJar no tiene
  `movienight.sid` (típico: server por HTTP sin `SESSION_COOKIE_INSECURE=1`, la cookie sale
  `secure:true`), la app lo avisa en vez de simular un login.
- **`POST /auth/register` no deja sesión**: la app registra y después hace login.
- Si el `POST /auth/logout` falla por red, la sesión se cierra igual en el dispositivo.
- **`userId` por instalación** (Sesión B): un UUID generado una vez (`UserIdStore`, prefs
  `movienight_identity`, excluido de los backups), igual que `getPersistentUserId()` de la web. Es
  por dispositivo, no el `id` de la cuenta. El nombre en el chat es la parte local del email.
- **La contraseña de una sala recién creada viaja solo en memoria** (`RoomPasswordCache`, nunca en la
  ruta de navegación): el server pide la contraseña en `join-room` incluso a la dueña.
- **Entrar a una sala**: primero `GET /api/room/:id` (404 = "no existe"; `passwordProtected` decide si
  pedir contraseña) y recién después el socket. Un `room-error` de contraseña vuelve al pedido de
  contraseña; cualquier otro `room-error` es un error final. Se distingue por el texto del mensaje.
- **401 en una llamada de datos = sesión vencida en el server**: se le pide al `SessionManager` que
  vuelva a consultar `/auth/me` y la app cae sola en el login.
- **La biblioteca de la app es solo lectura** por ahora (listar y crear sala). Subir va en la Fase 4;
  borrar videos (`DELETE /api/uploads/:filename`) no está planeado todavía.

## Cómo se trabaja en este repo

Mismo flujo que ya usa `movienight` (la web):

- El asistente (IA) no hace push directo. Clona el repo → hace el cambio →
  commit local (con las credenciales reales de la persona, no las del
  asistente, para que el historial de GitHub quede a su nombre) → genera un
  patch con `git format-patch` → lo entrega como archivo descargable → la
  persona lo aplica con `git am` y hace `git push` ella misma.
- **La terminal de trabajo es PowerShell (Windows, Android Studio/IntelliJ)**
  — `~` no se expande igual que en bash al pasarlo a un programa externo
  como `git`, así que hay que usar la ruta completa vía `$env:USERPROFILE`.
  El comando a entregar siempre debería tener esta forma exacta (reemplazando
  solo el nombre del archivo):
  ```powershell
  git am "$env:USERPROFILE\Downloads\nombre-del-patch.patch"
  git push
  ```
  Sin esto, `git am ~/Downloads/...` falla con
  `fatal: could not open '...' for reading: No such file or directory` en
  PowerShell (confirmado el 2026-09-26).
- Cada cambio importante se refleja acá (`docs/MEMORIA.md`, si cambia algo
  esencial) y como entrada nueva en `docs/CHANGELOG.md`.

## Cómo verifica el asistente sin Android SDK

No hay SDK ni Maven en su entorno, así que la UI/Compose y OkHttp no se pueden compilar ahí.
Lo que sí hace (el arnés no está en el repo, se rehace cada sesión): bajar `kotlinc` 2.0.21 desde las releases de GitHub (el mismo Kotlin del
proyecto), compilar y ejecutar los archivos de lógica pura (sin imports de Android: `auth/AuthValidation.kt`,
`net/AuthErrors.kt`, `net/UrlUtils.kt`) con un mini-runner que imita JUnit, y pasar el resto por el
parser para detectar errores de sintaxis. Desde la Sesión B también se instala un JDK (`apt-get update && apt-get install openjdk-21-jdk-headless`) para
compilar `org.json` desde `stleary/JSON-java` y poder probar los parsers de JSON; en Gradle el equivalente es
`testImplementation(libs.org.json)` (el `org.json` de `android.jar` está "mockeado" en tests unitarios y no parsea).
Gotchas del entorno (2026-10-08): `apt-get update` falla con 403 por el repositorio de nodesource, se
soluciona moviendo `/etc/apt/sources.list.d/nodesource.sources` antes de actualizar; las fuentes de
`org.json` están en `JSON-java/src/main/java/org/json/`; el mini-JUnit necesita las sobrecargas con mensaje
(`assertNotNull(String, Object)`, etc.). **Mantener la lógica testeable sin dependencias de
Android.** Ojo con `/auth/*` dentro de un comentario KDoc: en Kotlin `/*` abre un comentario
anidado y rompe la compilación (ya pasó una vez).

## Por dónde seguir

**Fase 3C hecha y probada en el emulador (2026-10-08). La Fase 3 queda completa.** Con la app como host y la
web como invitada, la web sigue la reproducción: play, pausa y seek de la app (un solo `seek` al soltar la
barra) y un heartbeat cada 4 s. Según la persona, todas las pruebas de la lista de la sesión funcionaron
(cambio de host, corte de red, segundo plano, subtítulos, buffering, la app como invitada).
**Cómo emite** (`RoomViewModel`, `RoomPlayer`, `net/SyncLogic.kt`): lo que hace la persona entra por
`RoomPlayer.togglePlay` / `seekTo` / `pause`, que DEVUELVEN el `sync` a emitir; el único que emite es
`RoomViewModel.emitSync`, y exige `canEmitSync` (rol de host confirmado por `host-status` + socket conectado).
Lo que viene del server entra solo por `applySync`, `load`/`alignToRoom` y `setSubtitle`, y ninguno emite: un
seek del server no puede rebotar. Forma emitida (verificada en `server.js`): `{ type, time (segundos) }` y, solo
en el heartbeat, `paused`. **Decisiones:** (1) al desconectarse el rol se da por perdido (`isHost = false`)
hasta el `host-status` del nuevo join; sin conexión no se encola nada (socket.io soltaría posiciones viejas al
reconectar). (2) El heartbeat no se manda sin un video sano: con la cinta caída le pondría la sala en 0 y en
pausa a todos. (3) Al pasar la app a segundo plano el host emite `pause`. (4) Quien pasa a ser host pierde la
referencia del seguimiento (`setHostRole`), para que un `resync` viejo nunca mueva su video. (5) `buffering-status`
lo reportan todos los roles, solo en cambios y solo si el video quiere reproducir y se quedó sin datos
(`shouldReportBuffering`); se reenvía en cada `host-status` porque el server empieza de cero con cada socket.
(6) Subtítulos: `subtitle-changed` (objeto `{ subtitleFile }`, solo server→cliente) y `room-data.subtitleFile`; se
carga como `SubtitleConfiguration` WebVTT; cambiarlo con un video cargado lo vuelve a preparar en la posición
actual. El subtítulo persiste al cambiar de video, como en el server.
**Bug encontrado en la prueba, corregido:** si la app volvía a la sala con el mismo video ya cargado (p. ej. tras
pasarse el host a la web y volver), `load()` ignoraba la posición de `room-data`; como host no tenía a quién
seguir y su heartbeat devolvía a la sala a la posición vieja. Ahora `alignToRoom` / `planRejoin` la alinea con la
sala (de host o de invitado). **Límite conocido:** esa posición es la del último heartbeat guardado en el server
(hasta unos 4 s de antigüedad), así que la app puede quedar un poco atrás.
**Logcat** (`adb logcat -s MovieNightSync`): hay log de lo que la app EMITE (`emito ...`), de los cambios de
buffering (con `bufferedAhead`, para distinguir red lenta de un fallo de la app) y de la alineación al
reconectar. **No hay log de los `sync` que RECIBE:** una versión anterior de este archivo y el CHANGELOG de la
3B decían que sí, pero ese log nunca estuvo en el código.
**Buffering con R2:** en la prueba, buffering solo tras un salto a una zona sin descargar (unos 3,5 s); en más de
5 minutos de reproducción continua no hubo ninguno espontáneo. Sin señal de un bug de la app; con archivos
grandes desde R2 sigue siendo esperable (`movienight/docs/MEMORIA.md`, 2026-09-10).
Siguiente: **Fase 4** (subida de video) de `docs/PLAN-PRODUCCION.md`.

---

## Resumen de lo ya hecho (el detalle vive en `docs/historico/`)

Todo lo de abajo se probó en el emulador (Medium Phone API 36.1) contra el servidor real. Texto
completo, con lo que se verificó y los bugs de cada fase: `docs/historico/MEMORIA-fases-1-a-3B.md`.

- **Fase 1 (2026-09-28):** spike en un dispositivo real. Login por cookie, sesión persistente,
  `join-room` por Socket.IO con la cookie en el handshake y chat en ambos sentidos con la web. Validó que
  **no hace falta tocar el servidor**. Falta probar `reaction` y el `typing` enviado.
- **Fase 2, Sesión A (2026-10-02):** login, registro, logout, sesión persistente y el aviso "Reintentar"
  sin red.
- **Fase 2, Sesión B (2026-10-02):** biblioteca (`GET /api/uploads` → `[{ filename, displayName, size,
  mtime }]`), crear sala, unirse por código o link, sala con chat, `userId` persistente.
- **Fase 3A (2026-10-02):** el video de la sala con Media3 (`room/RoomPlayer.kt`, `net/VideoUrl.kt`).
- **Fase 3B (2026-10-03):** la app sigue al host como invitada (`net/SyncLogic.kt`). Umbrales de desfase:
  hasta 0.5 s no se toca, entre 0.5 y 1 s velocidad 1.06 / 0.94, más de 1 s salto; con el host en pausa se
  alinea si hay más de 150 ms. Quedó un desfase residual de 1 a 2 s tras un seek del host, que se corrige
  solo y se aceptó.
- **Fase 3C (2026-10-08):** la app como host (ver arriba, es lo último que se hizo).

## Datos que siguen vigentes de las fases viejas

- `GET /api/room/:id` solo devuelve `{ passwordProtected }`.
- Solo puede haber un host a la vez: la app le quita el host a la web si entra con la cuenta dueña.
- El servidor destino tiene que correr la rama `plan-produccion`.
- **Media3 fijado en `1.10.1`** (la 1.11.x exige Kotlin 2.2 y el proyecto usa 2.0.21; el error sale en
  `MainActivity.kt` sin nombrar la librería). Ver "Stack".
- Con Android Studio nuevo, Gradle 8.13 pide "Use JVM 21" (Java 25 no es compatible).

## Pendientes que arrastramos (no bloquean la Fase 4)

- **Recuperar contraseña de punta a punta:** el servidor no tiene configurado el envío de emails
  (`RESEND_API_KEY`, `EMAIL_FROM`, `APP_BASE_URL`).
- **Sin prueba explícita:** "te sacó de la sala", chat bloqueado al silenciar y el aviso
  `server-restarting` (necesitan un host que los dispare o reiniciar el server). Tampoco se probó la
  reacción (`reaction`) ni la red lenta con muchos saltos de sincronización.
- El último ajuste de la pausa de la 3B (exacta a 150 ms) no se confirmó en el emulador.
