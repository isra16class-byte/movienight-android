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
      home/HomeViewModel.kt     # biblioteca, crear sala, subir y crear sala (4B), unirse por código o link; fetchLibrary()
      room/                     # una sala
        RoomViewModel.kt        # comprueba la sala, contraseña, socket, estado del chat; emite sync si es host (3C); cambiar el video de la sala (4B); moderación del host (5)
        RoomPasswordCache.kt    # pasa la contraseña de una sala recién creada (solo en memoria)
        RoomPlayer.kt           # ExoPlayer de la sala: carga el video, sigue al host (applySync) o lo maneja si es host; subtítulos y buffering; Fase 3
      ui/
        AppRoot.kt              # elige pantalla según SessionState; un NavHost por estado
        auth/                   # LoginScreen, RegisterScreen, ForgotPasswordScreen, componentes
        home/HomeScreen.kt      # unirse por código/link + subir (solo subir / subir y crear sala) + biblioteca + diálogo de crear sala
        room/RoomScreen.kt      # sala: cabecera, contraseña, error, "te sacaron" (5), chat (el reproductor de la sala va con `RoomPlayer`, Fase 3A)
        room/ViewersDialog.kt   # lista "En la sala": estado de cada persona y, para el host, el menú Acciones (hacer host / silenciar / expulsar) (5)
        room/ChangeVideoDialog.kt # host: cuadro "Cambiar video" (biblioteca o subir uno nuevo) y aviso de avance de la subida (4B)
        upload/UploadStatus.kt  # cuerpo de una subida según su UploadState (lo usan la pantalla principal y la sala) y KeepScreenOn
      net/                  # capa de red, sin nada de UI (pensada para reusarse)
        PersistentCookieJar.kt  # CookieJar persistente (movienight.sid)
        MovieNightApi.kt        # llamadas HTTP genéricas (get / postJson), devuelven código + cuerpo
        RoomSocket.kt           # wrapper de Socket.IO: join-room, eventos del server como RoomEvent, sendSync / sendBuffering / sendModeration, socketId
        RoomEvents.kt           # RoomEvent, ChatMessage, Viewer y parseServerEvent() (JVM + org.json)
        LibraryParsing.kt       # parseLibrary(), parseCreatedRoomId(), formatFileSize()
        RoomIds.kt              # extractRoomId() (código o link), isRoomPasswordError(), videoDisplayName()
        VideoUrl.kt             # resolveVideoUrl(), resolveSubtitleUrl(), shouldLoadVideo(), playbackErrorMessage()
        SyncLogic.kt            # sync: parser, planSync (desfase), HostReference, planRejoin; emitir: toSyncPayload, canEmitSync (JVM puro)
        UploadLogic.kt         # subida: validar extensión y tamaño, nombre sin tildes, parsear presign, mensajes de error, progreso, UploadState/UploadGoal
        UploadFlow.kt          # subida de punta a punta (4A) + paso siguiente opcional (4B): cancelar, reintentar solo el paso siguiente
        RoomVideoLogic.kt      # 4B: cuerpos y respuestas de create-room-from-upload y change-video-from-upload (JVM puro)
        ModerationLogic.kt     # 5: acciones de moderación del host: qué se ofrece según el rol, comprobaciones, textos, pedidos en curso (JVM puro)
        VideoUploader.kt       # subida: metadatos del URI, RequestBody en streaming y PUT cancelable al bucket
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
- **La biblioteca de la app lista, crea sala y sube videos** (4A), y el host puede cambiar el video de la
  sala (4B). Borrar videos (`DELETE /api/uploads/:filename`) no está planeado todavía.

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

**Fase 5 hecha y probada en el emulador (2026-10-09).** Queda hecho todo lo que el plan pedía hasta la Fase 5 (el
fallback sin R2 de la Fase 4 sigue fuera de alcance).
La app como host modera la sala, y como invitada reacciona bien cuando la moderan:
- **Lista "En la sala"** (`ui/room/ViewersDialog.kt`, se abre tocando "N conectados"): cada persona con su estado
  ("vos", "host", "silenciado"). Si la app es host **confirmado por `host-status`**, cada otra persona trae un menú
  **Acciones**: hacer host, silenciar / quitar silencio y expulsar. Los invitados no ven acciones. Hacer host y
  expulsar piden confirmar; silenciar no (se revierte con un toque).
- **Resultado a la vista:** el silencio se ve en la fila; al pasar el host, la app pierde el menú (y la fila
  pasa a decir "host") y la web lo gana; un expulsado desaparece de la lista.
**Lo que hace el server** (verificado en `server.js`, rama `plan-produccion`, y no es lo mismo que `change-video`):
- Los tres eventos los autoriza **solo `socket.isHost`**, no el dueño de la sala. El payload es el `id` de socket
  del objetivo como **texto plano** (el `id` de cada fila de `viewer-list`).
- **No contestan nada** (sin confirmación ni error): lo que no corresponde se ignora en silencio. El resultado se ve
  en `viewer-list`, `host-status`, y `kicked` / `mute-status` en el objetivo. Solo `make-host` deja un mensaje de
  sistema en el chat; expulsar y silenciar no.
- Solo `make-host` rechaza hacerse host a uno mismo. `kick-user` y `toggle-mute` sobre uno mismo SÍ se ejecutan, y
  **ninguno protege al dueño** de la sala. Expulsar no veta: la persona puede volver a entrar.
**Cómo funciona / decisiones:**
- Lógica pura en `net/ModerationLogic.kt` (con `ModerationLogicTest`, 30 tests): qué acciones se ofrecen según el rol,
  la comprobación que se repite justo al emitir, los textos y el seguimiento de pedidos. `RoomSocket.sendModeration`
  no encola si no hay conexión (igual que `sendSync`). `RoomViewModel.moderate()` es el único camino que emite.
- **Nunca se ofrece nada sobre uno mismo ni sobre quien figura como host**, y si no se conoce el `id` de socket propio
  (`RoomSocket.socketId`, se relee en cada conexión y lista) no se ofrece nada.
- Como el server no confirma, la app espera **5 s** a que `viewer-list` refleje el cambio; si no, avisa dentro de la
  lista. Mientras hay un pedido en curso sobre alguien no se acepta otro sobre esa persona (`toggle-mute` ALTERNA:
  dos toques seguidos lo dejarían como estaba). Los pedidos en curso se descartan al ceder el host, desconectarse o
  ser expulsado.
- Se pide confirmar también para **hacer host**, aunque lo pedido era solo expulsar: es la única acción con la que
  el host se quita su propio control, y en una sala con dueño solo se recupera volviendo a entrar con esa cuenta.
**Lado receptor, corregido:**
- `kicked` ahora tiene su propia pantalla ("Te sacaron de la sala", `RoomPhase.Kicked`), en vez del "No se pudo entrar a
  la sala" del error genérico, y deja limpio el rol, la lista y cualquier subida en curso.
- **Bug del silencio:** el server borra el silencio a los 15 s de que te fuiste y, al volver, solo avisa si SIGUES
  silenciado (nunca manda `muted:false`). La app conservaba el silencio viejo y podía dejar el chat bloqueado sin
  motivo; ahora lo borra al desconectarse y el server lo vuelve a mandar si corresponde.
**Qué se probó:** la persona confirmó "todo funcionó" y detalló: el menú Acciones en pantallas chicas; hacer host
(el menú desaparece en la app y aparece en la web); la web como host contra la app (expulsar, silenciar y quitar
silencio, con la app reaccionando bien); y reconectar siendo silenciado (el chat vuelve a bloquearse). **No se
probó:** el botón deshabilitado mientras hay un pedido en curso y el aviso a los 5 s sin confirmación (hay que
forzar un fallo), silenciar a alguien y que vuelva tras más de 15 s sin conexión, ser expulsado mientras se subía
un video como ex-host, ni una sala sin dueño con la app como host (el mismo pendiente de la 4B).
Siguiente: **Fase 6** (extras, no bloqueante). Antes de elegir, releer el plan: probar `server-restarting` y decidir
si hay algo que valga la pena (acceso al panel de administración, notificaciones).

**Fase 4B hecha y probada en el emulador (2026-10-08). La Fase 4 queda completa.** La app crea una sala con
un video recién subido y el host puede cambiar el video de la sala:
- **"Subir y crear sala"** (pantalla principal): contraseña opcional → selector del sistema → subida →
  `POST /create-room-from-upload` con la key del presign → entra sola a la sala (solo si la pantalla estaba a
  la vista; si no, queda el botón "Entrar a la sala"). También sigue "Solo subir", de la 4A.
- **"Cambiar video"** (solo el host, en la fila de controles del video; `ui/room/ChangeVideoDialog.kt`): elegir
  uno de la biblioteca (pide confirmar) o "Subir uno nuevo", que al terminar pasa a ser el video de la sala.
  Usa `POST /room/:id/change-video-from-upload` con `{ filename, hostToken? }`. Si se oculta el cuadro durante
  una subida, `RoomUploadBanner` muestra el avance.
- **Quién ve el cambio:** el servidor emite `video-changed` a TODA la sala, a quien lo pidió también, pone la
  posición en 0 y en pausa y manda un mensaje de sistema al chat. La app ya lo manejaba desde la 3A (recarga
  forzada), así que no hubo que tocar el receptor.
**Cómo funciona / decisiones:**
- La subida de la 4A salió de `HomeViewModel` a `net/UploadFlow.kt`, compartida por la pantalla principal y la
  sala, con un paso siguiente opcional (`UploadGoal`: `LIBRARY`, `CREATE_ROOM`, `CHANGE_ROOM_VIDEO`). Si la
  subida sale bien y falla el paso siguiente, **Reintentar repite solo ese paso** (misma key, no resube). No se
  puede cancelar durante el paso siguiente (es un POST de un instante y cortarlo dejaría en duda si la sala se
  creó). La lógica pura está en `net/RoomVideoLogic.kt` y `net/UploadLogic.kt`, con tests.
- **El servidor autoriza el cambio por el DUEÑO de la sala, no por quién es host** (`isRoomOwner`, en
  `lib/hostAuth.js`). En una sala con dueño (creada con sesión: todas las que crea la app) solo vale la sesión
  de esa cuenta y el `hostToken` se ignora. Un host por traspaso que no la creó recibe **403**, y la app lo
  explica ("solo quien creó la sala puede cambiarle el video"). En una sala sin dueño (anónima de la web) vale
  el `hostToken`, que el server manda al host en `host-status`: la app lo guarda solo en memoria
  (`RoomEvent.HostStatus.hostToken`) y lo borra al dejar de ser host o al desconectarse.
- Si cuando termina la subida ya no se es host (traspaso mientras subía), no se manda el cambio y el video
  queda en la biblioteca.
- El servidor valida el contenido del video recién en estas dos rutas y **borra de la biblioteca** el que no
  pasa. Ante cualquier 400 la app recarga la lista.
- La subida desde la sala es una corrutina de `RoomViewModel`: **si se sale de la sala mientras sube, se
  corta** (mismo límite aceptado que en la 4A: sin servicio en primer plano). La pantalla se mantiene encendida
  mientras sube.
**Qué se probó:** la persona confirmó "todo funcionó" con el checklist de la sesión (crear con y sin
contraseña; cambiar desde la biblioteca y subiendo uno nuevo con la web viendo el cambio; un host que no es el
dueño; cancelar y ocultar el cuadro durante la subida; modo avión en el último paso con reintento sin resubir;
repaso de la 4A), sin detallar caso por caso. **No se probó:** una sala sin dueño donde la app sea host (usa el
`hostToken`; no estaba en el checklist), archivos de cientos de MB o GB, salir de la sala mientras sube, ni el
proceso muerto en segundo plano.

**Fase 4A hecha y probada en el emulador (2026-10-08).** La app sube un video del teléfono a la biblioteca:
selector del sistema (`OpenDocument`, sin permisos de almacenamiento), `POST /api/uploads/presign`
(`{ filename, contentType }` → `{ key, uploadUrl, expiresIn }`) y `PUT` directo al bucket de R2, leyendo el
archivo en streaming (bloques de 64 KB, nunca entero en memoria), con barra de progreso, cancelar y reintentar.
Código en `net/UploadLogic.kt` (lógica pura con tests), `net/VideoUploader.kt`, `HomeViewModel` y `HomeScreen`.
**Cómo funciona / decisiones:**
- La URL prefirmada firma solo el header `host`; el `PUT` manda el mismo `Content-Type`, como la web. Después
  del `PUT` **no hay confirmación**: `GET /api/uploads` lista el bucket filtrando por extensión, así que el
  video aparece solo. El contenido recién se valida al crear una sala con él.
- Extensiones aceptadas: mp4, mkv, mov, webm, avi, m4v. Límite: 5 GiB − 5 MiB (el del `PUT` simple de R2).
  El nombre se manda sin tildes.
- El `PUT` va por un cliente HTTP aparte (sin CookieJar y sin tope de duración de llamada). **Reintentar pide
  una URL nueva y sube desde cero.**
- La pantalla se mantiene encendida mientras sube. La subida es una corrutina del `HomeViewModel`, sin
  servicio en primer plano: sigue mientras Android mantenga vivo el proceso; **si lo mata, hay que empezar de
  nuevo** (un `PUT` simple cortado no deja objeto en el bucket). Es un límite aceptado, no un bug.
**Qué se probó:** el checklist completo de la 4A (selector, progreso, aparece en la biblioteca de la app y de
la web, crear sala con ese video y reproducirlo, cancelar sin dejar un video a medias, error y reintento con
modo avión, nombre con tilde y espacio, pantalla encendida y segundo plano) con un video de prueba de ~47 MB y
uno corto de 1,7 MB, generados con ffmpeg. **No se probó:** un archivo de cientos de MB o de GB, ni el límite
de 5 GiB con un archivo real (solo tests unitarios), ni el proceso muerto en segundo plano.
(Desde la 4B la subida vive en `net/UploadFlow.kt`; `HomeViewModel` solo la usa.)

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
(La Fase 4 está completa: lo siguiente es la Fase 5, arriba.)

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
- **Fase 3C (2026-10-08):** la app como host: emite `sync`, `subtitle-changed` y `buffering-status`.
- **Fase 4A (2026-10-08):** subir un video a la biblioteca.
- **Fase 4B (2026-10-08):** crear sala con un video recién subido y cambiar el video de la sala siendo host.
- **Fase 5 (2026-10-09):** la app como host hace host a otra persona, silencia y expulsa (`net/ModerationLogic.kt`,
  `ui/room/ViewersDialog.kt`), y reacciona a `kicked` y `mute-status` (ver arriba, es lo último que se hizo).

## Datos que siguen vigentes de las fases viejas

- `GET /api/room/:id` solo devuelve `{ passwordProtected }`.
- Solo puede haber un host a la vez: la app le quita el host a la web si entra con la cuenta dueña.
- El servidor destino tiene que correr la rama `plan-produccion`.
- **Media3 fijado en `1.10.1`** (la 1.11.x exige Kotlin 2.2 y el proyecto usa 2.0.21; el error sale en
  `MainActivity.kt` sin nombrar la librería). Ver "Stack".
- Con Android Studio nuevo, Gradle 8.13 pide "Use JVM 21" (Java 25 no es compatible).
- **Moderación (Fase 5):** `make-host`, `kick-user` y `toggle-mute` los autoriza solo `socket.isHost` (no el dueño), el
  payload es el `id` de socket en texto plano y el server no contesta: el resultado se ve en `viewer-list` / `host-status`.
  El silencio vale por `userId` y el server lo borra a los 15 s de desconectarse; al volver nunca manda `muted:false`.

## Pendientes que arrastramos (no bloquean la Fase 6)

- **Recuperar contraseña de punta a punta:** el servidor no tiene configurado el envío de emails
  (`RESEND_API_KEY`, `EMAIL_FROM`, `APP_BASE_URL`).
- **Sin prueba explícita:** el aviso `server-restarting` (hay que reiniciar el server), la reacción (`reaction`) y la
  red lenta con muchos saltos de sincronización. ("Te sacó de la sala" y el chat bloqueado al silenciar ya se probaron en la Fase 5.)
- El último ajuste de la pausa de la 3B (exacta a 150 ms) no se confirmó en el emulador.
- **Subida de video (4A):** no probada con un archivo grande real (cientos de MB o GB) ni cerca del límite de
  5 GiB; si Android mata la app durante una subida, se pierde (límite aceptado, ver la entrada de la 4A).
- **Cambiar el video (4B):** sin probar una sala sin dueño (anónima de la web) donde la app sea host (usa el
  `hostToken`), ni salir de la sala mientras sube (la subida se corta). Un host que no creó la sala no puede
  cambiar el video (403 del servidor, por diseño del servidor).
- **Moderación (5):** sin probar el botón deshabilitado mientras hay un pedido en curso ni el aviso a los 5 s sin
  confirmación; silenciar a alguien y que vuelva tras más de 15 s sin conexión; ser expulsado mientras se subía un video
  como ex-host; ni una sala sin dueño con la app como host. **Límites que vienen del server (por diseño del server):** no
  protege al dueño (el host de la web puede expulsarlo o silenciarlo; la app no sabe quién es el dueño y tampoco lo
  impide), expulsar no impide volver a entrar, y expulsar / silenciar no dejan mensaje en el chat.
