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
      MovieNightApp.kt      # Application: crea el AppContainer y los canales de notificación (6C); cuenta las pantallas visibles
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
        home/AdminPanelLauncher.kt # 6B: openInBrowser(): abre un link en el navegador con ACTION_VIEW
        room/RoomScreen.kt      # sala: cabecera, contraseña, error, "te sacaron" (5), chat (el reproductor de la sala va con `RoomPlayer`, Fase 3A)
        room/ViewersDialog.kt   # lista "En la sala": estado de cada persona y, para el host, el menú Acciones (hacer host / silenciar / expulsar) (5)
        room/ChangeVideoDialog.kt # host: cuadro "Cambiar video" (biblioteca o subir uno nuevo) y aviso de avance de la subida (4B)
        upload/UploadStatus.kt  # cuerpo de una subida según su UploadState (lo usan la pantalla principal y la sala) y KeepScreenOn
        room/PictureInPicture.kt # 7A: disponibilidad de PiP, actividad y PipHost
        upload/NotificationPermissionGate.kt # 6C-A: rememberNotificationGate(): explica y pide POST_NOTIFICATIONS (Android 13+) antes de abrir el selector, una sola vez
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
        UploadNotificationLogic.kt # 6C-A: texto del aviso de subida según su estado final, cuándo pedir el permiso de notificaciones, VisibilityCounter (JVM puro)
        PictureInPictureLogic.kt # 7A: cuándo entrar en PiP, aspecto, contenido de la ventana, PauseOnStopTracker (cuándo pausar al dejar de verse) (JVM puro)
        UploadServiceLogic.kt  # 6C-B: qué estados necesitan el servicio, notificación de avance (porcentaje entero), UploadActivityTracker (JVM puro)
        AdminAccessLogic.kt    # 6B: si la cuenta es admin (respuesta de GET /admin/stats -> AdminAccess), cuándo mostrar el acceso, URL del panel (JVM puro)
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

**Fase 7A, punto de la X resuelto y confirmado en el emulador (2026-10-10): cerrar la ventana flotante con la X ahora pausa.** Reemplaza el
punto pendiente de la entrada siguiente. Queda sin probar lo que se detalla abajo; la 7B sigue sin empezar (solo si hace falta, y empieza preguntando).
- **Qué falló y cómo se arregló:** sin `configChanges`, cerrar la ventana con la X hacía que Android destruyera y recreara la actividad y no mandara
  `onPictureInPictureModeChanged`; el video (y la sala, si eras host) seguía sonando. Se declaró en `MainActivity`
  `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"`: entrar y salir de PiP y **girar el teléfono ya no recrean la
  actividad** (reciben `onConfigurationChanged`); tema oscuro, idioma y tamaño de letra siguen recreándola, y `PauseWhenAppStops` no pausa en ese caso. No
  se tocó la lógica de pausa (`PauseOnStopTracker` ya cubría el orden).
- **Qué se vio** (`adb logcat -s MovieNightPip MovieNightSync`): dos cierres con la X, uno como host y otro como invitado. En los dos sale
  `ON_STOP pip=true -> no pauso` y, medio segundo después, `MODO_PIP=false lifecycle=CREATED -> PAUSO`. Como host: `onAppStopped isHost=true`,
  `playWhenReady=false`, `emito pause` y los heartbeats siguen con `paused=true`. Como invitado: `onAppStopped isHost=false` y no emite nada. La persona
  confirmó que se pausó bien. Un primer intento anterior no dejó ningún evento (la actividad seguía `pip=true`); no se explicó, posiblemente el toque no
  fue sobre la X.
- **También respaldado por ese log (entrada, expandir y pantalla apagada):** al entrar, `ON_PAUSE pip=true` y luego `MODO_PIP=true -> no pauso`, con la
  misma actividad y el heartbeat del host cada 4 s; al tocar la ventana, `MODO_PIP=false` y `ON_RESUME` sin pausa; con la pantalla apagada en PiP,
  `ON_STOP ... pantalla=off -> PAUSO` y el host emite `pause`, y al encenderla sigue pausado. Pausado y Inicio no abre ventana (`entrar=false`). Como
  invitado en PiP llegó un `heartbeat` antes de cerrar.
- **Límite conocido (decidido por la persona: se deja así):** un **invitado** que cierra la ventana con la X pausa, pero el siguiente `heartbeat` de un
  host que sigue reproduciendo lo pone otra vez en play (1,1 s después en el log) y suena en segundo plano hasta que el socket cae (~5 s). Es el mismo
  camino de la pantalla apagada y no es nuevo de PiP. Arreglarlo exigiría que el invitado ignore `play` y `heartbeat` mientras la app no se ve (tocaría
  `RoomPlayer`/`RoomViewModel`, sin tocar el servidor); queda para la 7B si se hace.
- **Código de este paso:** `fedab99` (el `configChanges`) y un patch que quita los logs de depuración `[DEBUG-X]` (revierte `5baa7c6`) y actualiza el
  comentario del manifiesto. Se mantienen los logs `MovieNightPip` y `MovieNightSync` (decidir si se quitan). Sin dependencias nuevas ni cambios en el servidor.
- **Sin probar (no darlos por probados):** girar el teléfono en la sala con `configChanges` (diseño horizontal, barras, teclado; sin `ON_STOP`,
  `ON_DESTROY` ni `PAUSO`) y el tema oscuro (debe recrear sin pausar); la pantalla principal y el login sin ventana (solo se ve en el log la principal
  con el video pausado); el camino de antes de Android 12; PiP desactivado en Ajustes; "Subir uno nuevo" con el video sonando; ser expulsado con la
  ventana abierta; el invitado en PiP durante más tiempo (solo llegó un heartbeat antes de cerrar); y el contenido de la ventana (solo video), que no se ve
  en el log. `gradlew testDebugUnitTest` no se confirmó.

**Fase 7A hecha y probada en el emulador (2026-10-10): mini-reproductor flotante (Picture-in-Picture), con un punto por verificar: cerrar la
ventana con la X.** La persona reporta que todas las pruebas funcionaron y que no vio parpadeos; el log que adjuntó respalda casi todo, pero
no muestra el cierre con la X. Queda la 7B (sin empezar; solo si hace falta, y empieza preguntando).
- **Decisión de la persona:** al entrar en PiP **no se pausa nada**, ni el video local ni la sala, ni siendo host ni invitado. Se pausa cuando
  la ventana se cierra o la app deja de verse del todo (por ejemplo, pantalla apagada).
- **Cómo funciona:** `MainActivity` declara `supportsPictureInPicture` y **no** `configChanges`: entrar en PiP recrea la actividad (se ve en el
  log: `ON_STOP cambioConfig=true` seguido de `ON_START pip=true`) y no se vio parpadeo porque el reproductor vive en el `RoomViewModel`. La
  pantalla de la sala le pide a la actividad (`PipHost.updatePip(PipRequest)`) lo que hace falta cada vez que cambia: en la sala, video
  cargado y sano, reproduciéndose, y el aspecto. La actividad lo aplica con `setPictureInPictureParams` y, desde Android 12, la entrada
  automática (`setAutoEnterEnabled`); antes de Android 12 entra con `onUserLeaveHint` + `enterPictureInPictureMode`. Al salir de la
  pantalla de la sala se manda `PipRequest.OFF`, para que desde la pantalla principal o el login nunca aparezca la ventana. Si PiP no está
  disponible (función ausente o desactivada para la app en Ajustes, vía `AppOpsManager`) o entrar falla, la app se pausa como siempre.
- **Piezas:** `net/PictureInPictureLogic.kt` (`PipConditions`, `shouldEnterPip`, `pipAspectFor` —16:9 por defecto, acotado a 1:2,39..2,39:1—,
  `pipContentFor`, `PauseOnStopTracker`; JVM puro), `ui/room/PictureInPicture.kt` (disponibilidad, actividad, `PipHost`), cambios en
  `MainActivity`, `RoomScreen` (`PictureInPictureRequests`, `PipVideo`, `PauseWhenAppStops`), `RoomPlayer` (`videoWidth`/`videoHeight` desde
  `onVideoSizeChanged`, solo para el aspecto) y `RoomViewModel` (logs). En la ventana solo va el video; si la sala deja de estar sana con la
  ventana abierta (te sacaron, se cerró, el video falló) se muestra un mensaje corto. No se hicieron los botones de reproducir/pausar de la ventana.
- **Pausa (`PauseOnStopTracker`, reemplaza "ON_STOP y no es cambio de configuración"):** sin PiP, o con la pantalla apagada, se pausa; un cambio
  de configuración no pausa; **detenida en PiP con la pantalla encendida no pausa en el momento: espera al cambio de modo de PiP**, y pausa si la
  actividad quedó detenida (la ventana se cerró). Es un diseño que depende de que Android mande ese cambio de modo al cerrar la ventana.
- **Qué muestra el log** (`adb logcat -s MovieNightPip MovieNightSync`, emulador SDK 36 / Android 16): al entrar en PiP no se pausa y el host sigue
  emitiendo `heartbeat` cada 4 s sin huecos; un invitado en PiP recibe `heartbeat`, `pause` y `play` y la ventana los sigue; dos veces el
  `ON_STOP` llegó en PiP con `cambioConfig=false` al volver a pantalla completa y no pausó (con la condición anterior habría pausado, y un host
  habría pausado a toda la sala); con la pantalla apagada estando en PiP sale `pantalla=off -> PAUSO` y el host emite `pause`; desde la pantalla
  principal `onUserLeaveHint ... entrar=false` y no hay ventana.
- **Qué se probó:** la persona confirmó que todas las pruebas de la lista funcionaron y que no hubo parpadeos. **Qué NO queda respaldado por el
  log y sigue sin verificar:** (1) **cerrar la ventana con la X**: en todo el log no hay ninguna pausa por esa vía ni una sola línea `MODO_PIP=`,
  así que no se ve que el video pare; si Android no manda el cambio de modo al cerrar una actividad que nació en PiP (se recrea al entrar), la
  pausa quedaría en espera y el video podría seguir sonando; (2) a las 18:27:44 el log marca `inRoom=false` con la ventana abierta y no se sabe
  qué se hizo ahí; (3) el camino de antes de Android 12 (`onUserLeaveHint` + `enterPictureInPictureMode`), porque solo se probó SDK 36; (4) PiP
  desactivado en Ajustes, abrir "Subir uno nuevo" con el video reproduciéndose y ser expulsado con la ventana abierta (no dejan línea en el log
  aunque se hayan hecho); (5) el contenido de la ventana (solo video) y tocarla para volver, que no se ven en el log. **Verificado por el
  asistente:** los 21 tests de `PictureInPictureLogicTest` pasan (`kotlinc` 2.0.21, arnés propio); `gradlew testDebugUnitTest` no se confirmó.
- **Límites:** con la pantalla apagada se pausa y, unos 5 s después, el socket cae con `transport error` y reconecta al volver: es el mismo corte
  de red de la 6C, independiente de PiP (la 7B tendría que resolverlo si se quiere audio con la pantalla apagada). Los logs de depuración
  `MovieNightPip` y `MovieNightSync` (heartbeat cada 4 s, sync emitido y recibido, socket) quedaron en el código: decidir si se dejan o se quitan.
- **Próximo paso:** repetir solo la prueba de la X con `adb logcat -s MovieNightPip MovieNightSync` (entrar en PiP con la sala reproduciendo, tocar
  la X y mirar si sale `PAUSO` y si el sonido para). Si no pausa, hay que arreglarlo en un patch de código aparte. Después, usar la 7A unos días antes
  de decidir si hace falta la 7B.

**Fase 6C hecha y probada en el emulador (2026-10-10): notificaciones que se pueden hacer sin tocar el servidor.** Con esto
la Fase 6 queda cerrada, salvo el push real, que se evaluó y **no se hizo** (exige cambiar el servidor; ver abajo). Lo que sigue
es la **Fase 7** (PiP y reproducción en segundo plano), ya escrita en el plan; releerla antes de empezar.
- **Evaluación (verificada en `server.js` y `lib/`, rama `plan-produccion`):** las salas expiran tras `ROOM_TTL_HOURS` sin
  actividad (24 h por defecto; `null` = nunca) con un barrido cada 30 minutos. **No hay aviso previo** a la expiración: lo único
  que existe es el `room-error` "Esta sala expiró por inactividad…" emitido **después** de cerrarla y solo a los sockets
  conectados. Los demás eventos (`host-status`, `mute-status`, `chat-message`, `video-changed`, `room-error`) también llegan solo
  a quien tiene el socket abierto. No hay nada de push: ni tokens de dispositivo, ni Firebase, ni web-push.
- **Push real, no hecho:** exigiría cambiar el servidor (guardar el token de cada dispositivo con un endpoint nuevo, una cuenta
  de servicio de Firebase para enviar, ganchos en cada evento y un programador que avise antes de expirar la sala) y en la app un
  proyecto de Firebase con su `google-services.json` y sus dependencias, comprobando antes que no exijan un Kotlin más nuevo que
  el 2.0.21. Es la única forma de avisar con la app cerrada y de tener "sala por expirar". Queda en el plan sin tildar.
- **A — aviso local de subida.** Cuando una subida llega a `Done` o `Failed` y la app **no se está viendo**, sale una notificación
  (canal `uploads`, id 1001): "Subida terminada", "Sala lista", "Video de la sala cambiado", "No se pudo subir el video" o
  "La subida quedó a medias" (video ya subido pero falló el paso siguiente). Tocarla trae la app al frente; con la app a la vista
  no avisa, y al volver a verla se borra el aviso viejo. Lógica pura en `net/UploadNotificationLogic.kt` (`uploadNotice`,
  `shouldAskNotificationPermission`, `VisibilityCounter`); la notificación en `notify/UploadNotifier.kt`. `UploadFlow` avisa por
  `onFinished` desde un único punto (el setter de `state`). Se sabe si la app se ve contando `onActivityStarted`/`Stopped` en
  `MovieNightApp`, sin dependencias nuevas; se comprueba con `areNotificationsEnabled()`.
- **Permiso `POST_NOTIFICATIONS` (Android 13+):** se pide **una sola vez, justo antes de abrir el selector de video** (en "Solo
  subir", "Subir y crear sala" y "Cambiar video" de la sala), con un cuadro que explica para qué es
  (`ui/upload/NotificationPermissionGate.kt`). "Activar avisos" abre el cuadro del sistema; "Ahora no" no vuelve a preguntar;
  cerrar el cuadro sin elegir sí vuelve a preguntar la próxima vez; con cualquier respuesta la subida sigue. Nunca se pide al abrir
  la app y en Android 12 o menos no aparece nada. El "ya se preguntó" vive en `NotificationPrefs` y se excluye del backup.
- **B — servicio en primer plano de subidas.** Hallazgo de la prueba real: con la app en segundo plano la subida **fallaba siempre**
  con "Se cortó la conexión durante la subida" aunque el proceso seguía vivo (el aviso de fallo salía de ese mismo proceso). Se
  sospecha que Android corta la red de una app en segundo plano sin servicio en primer plano; **la causa técnica exacta no se
  registró** (el detalle del error de red, `PutOutcome.NetworkFailure.detail`, se descarta y no se muestra ni se loguea). El
  servicio la resolvió en el emulador. Piezas: `net/UploadServiceLogic.kt` (`progressNoticeFor`, `UploadActivityTracker`, JVM
  puro), `notify/UploadKeepAlive.kt` (controlador en el `AppContainer`), `notify/UploadService.kt` (servicio `dataSync`) y
  `UploadFlow.onStateChanged`, que cada `HomeViewModel` y `RoomViewModel` conectan con su propia clave.
- **Cómo funciona B:** el servicio **no sube nada**: la subida sigue siendo la corrutina de `UploadFlow`; el servicio solo mantiene
  vivo el proceso y muestra una notificación de avance (canal silencioso `upload_progress`, id 1002, barra con porcentaje entero
  que solo se actualiza al cambiar el número). Se arranca ya en `Preparing` (Android 12+ no deja arrancarlo desde segundo plano;
  las notificaciones de servicios de vida corta se difieren, así que un archivo inválido que falla al instante no muestra nada) y se
  para 2 s después de que no queda ninguna subida (para no pararlo antes de que termine de arrancar). `onStartCommand` siempre llama
  a `startForeground` y devuelve `START_NOT_STICKY`. Con dos subidas a la vez (principal y sala) sigue hasta que terminen ambas.
  `UploadFlow.release()` avisa `Idle` al cerrarse la pantalla dueña. Si Android no deja arrancarlo, la subida sigue como antes.
  Manifiesto nuevo: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` y el servicio con
  `foregroundServiceType="dataSync"`. Sin dependencias nuevas. Se actualizaron las pistas de la subida (ya no dicen "dejá la app
  abierta") y el texto del cuadro de permiso.
- **Qué se probó** (la persona confirmó "todas las pruebas funcionaron como debían" con el checklist de la B): la subida sigue con
  la app en segundo plano con la notificación de avance; el aviso de "terminada" al acabar; salir de la app justo después de
  elegir el video; pantalla apagada; un archivo inválido no muestra notificación de avance; con el wifi cortado sale el aviso de
  fallo y la notificación de avance desaparece; rotar el teléfono durante la subida. Antes, con A sola, el aviso de **fallo** sí
  llegó (esa prueba mostró el problema que resolvió B). **No se confirmaron uno por uno** los puntos del checklist de la A: el
  cuadro de permiso y su texto, "Ahora no", el permiso negado, "Subir y crear sala" y "Cambiar video" desde una sala. **Verificado
  por el asistente:** los 11 tests de `UploadNotificationLogicTest` y los 11 de `UploadServiceLogicTest` pasan (`kotlinc` 2.0.21,
  arnés propio); el resto del código nuevo (UI, servicio, notificaciones) pasó el parser sin errores de sintaxis.
  `gradlew testDebugUnitTest` no se confirmó en esta fase (la app sí compiló y corrió en el emulador).
- **Límites:** cerrar la app deslizándola desde recientes **cancela** la subida (muere el ViewModel que la tiene), igual que salir
  de la sala mientras se cambia el video; evitarlo exigiría mover la subida al servicio, un cambio mayor. Android 15 limita los
  servicios `dataSync` a unas 6 h por día (`onTimeout` lo para). Si el sistema mata el proceso aun así, la subida se pierde (sin
  basura en el bucket). La notificación de avance no tiene botón de cancelar (se cancela desde la app). Si el cuadro de permiso se
  pierde al girar el teléfono en ese instante, el selector no se abre y hay que volver a tocar el botón (en "Subir y crear sala" la
  contraseña recién se guarda cuando se abre el selector, así que no queda pendiente).
Siguiente: **Fase 7** (7A, PiP) según el plan, o lo que decida la persona.

**Fase 6B hecha y probada en el emulador (2026-10-09): acceso al panel de administración desde la app.** Queda la 6C
(el único punto sin tildar de la Fase 6 en el plan es PWA/notificaciones push; releer el plan antes de elegir). Se eligió
el alcance mínimo (opción a): **no hay pantallas nativas del panel**, solo un botón que abre el panel web.
- **Cómo sabe la app si la cuenta es admin:** `GET /auth/me` no lo dice (solo `loggedIn`, `id` y `email`). El rol vive en
  Postgres (`users.role`) y `requireAdmin` lo consulta en cada request a las rutas de admin. La app hace un sondeo de
  solo lectura a `GET /admin/stats` (`ADMIN_PROBE_PATH`): **200 con el JSON esperado (trae `activeRooms`) = admin**;
  401 / 403 / 404 = no admin; red, 429 o 5xx = no se sabe (`adminAccessFrom`). Una respuesta dudosa no borra lo ya
  sabido y una clara lo reemplaza (`mergeAdminAccess`), así que quitarle el rol a una cuenta oculta el botón en la
  próxima consulta. Solo `AdminAccess.ADMIN` muestra algo (`shouldShowAdminAccess`).
- **Cuándo se consulta:** `HomeScreen` llama a `HomeViewModel.refreshAdminAccess()` cada vez que entra en composición
  (al abrir la app y al volver de una sala). **No** se vuelve a consultar al volver del navegador a la app. El estado
  vive en el `HomeViewModel`, que muere al cerrar sesión: una cuenta nueva no hereda el acceso de la anterior.
- **El acceso:** tarjeta "Administración" en la pantalla principal (`AdminCard`, solo con admin confirmado) con el botón
  "Abrir panel de administración"; abre `adminPanelUrl(baseUrl)` = `<server>/admin.html` con
  `Intent.ACTION_VIEW` (`ui/home/AdminPanelLauncher.kt`, sin librerías nuevas; Custom Tabs hubiera exigido
  `androidx.browser`). Si no hay navegador, muestra `ADMIN_NO_BROWSER_MESSAGE` (ese aviso no se probó).
- **Sesión y cookie (verificado leyendo el código del server; el comportamiento en el navegador lo confirmó la persona):** el
  panel usa la misma sesión de siempre (cookie `movienight.sid`), no tiene login ni contraseña propios. `/admin.html` es un
  archivo estático que se sirve sin autenticar; si el navegador no tiene sesión, la página redirige a `/`, donde se inicia
  sesión con el mismo email y contraseña, y **no vuelve sola al panel** (hay que abrir `/admin.html` otra vez). La cookie
  de la app queda en `PersistentCookieJar` (SharedPreferences privadas) y no pasa al navegador: el navegador crea una
  sesión distinta en el server, y cerrar sesión en el panel no cierra la de la app. Lo único compartido es el límite de
  intentos de login (por IP y email).
- **Cómo se hace admin una cuenta:** no hay ninguna ruta HTTP, a propósito. Solo `scripts/make-admin.js <email>` en el
  server, con la cuenta ya registrada. Con Docker, desde la carpeta del server: `docker compose exec app node
  scripts/make-admin.js <email>` (correrlo con `node` directo en Windows falla: `DATABASE_URL` solo está dentro del
  contenedor). El rol se nota sin cerrar sesión.
- **Qué se probó** (la persona lo confirmó, "todos los puntos funcionaron"): cuenta admin ve la tarjeta y llega al panel
  (con el login aparte en el navegador); cuenta normal no ve nada; la tarjeta desaparece al quitar el rol y volver a la
  pantalla principal; cerrar sesión en el panel no cierra la de la app. **Verificado antes** (arnés del asistente): los 11
  tests de `AdminAccessLogicTest` pasan, y el `requireAdmin` real del server dio 200 / 403 / 401 / 404 / 500 con una base
  simulada. `gradlew testDebugUnitTest` no se confirmó en esta fase (la app sí compiló y corrió en el emulador).
- **Mejora posible, no hecha:** que `/auth/me` devuelva el rol (hoy hay que sondear una ruta de admin). Tocaría el server
  y la app tendría que seguir funcionando con servers viejos. No se tocó el server.
Siguiente: **6C** (releer el plan antes de elegir).

**Fase 6A hecha y probada en el emulador (2026-10-09): aviso de `server-restarting` y reconexión coherente.** Quedan
pendientes la 6B y la 6C del resto de la Fase 6 (ver el plan). Qué hace la app cuando el server se reinicia:
- **Aviso** (franja sobre el chat, `connectionBanner` en `net/ServerRestartLogic.kt`): "El servidor se va a reiniciar en
  unos segundos…" mientras sigue la conexión y "El servidor se está reiniciando. Reconectando en cuanto vuelva…" cuando
  se corta. Manda sobre los demás avisos (los `connect_error` de la caída no lo pisan) y desaparece solo cuando
  `room-data` confirma que volvimos a la sala. Si el server no vuelve, a los 90 s (`RESTART_GIVE_UP_MS`) cae al genérico.
- **Posición:** `RestartState` (inmutable) sabe si el reinicio fue anunciado y si el socket llegó a cortarse; solo
  entonces `room-data` cuenta como "vuelta de un reinicio" y `planRejoin(afterRestart = true)` **no salta**. El host
  conserva su posición y su pausa y su heartbeat alinea al resto en unos 4 s; el invitado toma solo la pausa de la sala.
  Motivo: Redis guarda la posición con hasta ~8 s de atraso. Sin reinicio, `planRejoin` sigue igual que en la 5.
- **Video en error:** si el mismo video había quedado en error durante la caída (típico con videos en disco, los sirve el
  mismo Node), `RoomPlayer.load` lo vuelve a preparar. **Sin probar.**
- **`room-error` al volver:** se le agrega contexto (`roomErrorAfterRestart`); típico: la sala no se recuperó.
- **Subidas:** sin cambios. El PUT va directo a R2; solo `presign` y el paso final pasan por el server y son reintentables.
**Lo que hace el server** (verificado en `server.js`, rama `plan-produccion`, y con un server real): SIGTERM / SIGINT / IPC
de PM2 → `gracefulShutdown` → `io.emit('server-restarting')` a todos, **sin payload** → corte `SHUTDOWN_GRACE_MS` después
(5 s; PM2 `kill_timeout: 8000`), razón `transport close`, el cliente reconecta solo. Al volver, `join-room` devuelve
chat-history, host-status y room-data; las salas salen de Redis.
**Cómo se probó** (Docker Desktop, proyecto `movienight`): reiniciar con ⋮ → Restart sobre **`app-1`** (no sobre
`redis-1` ni el grupo). `app-1` no termina de arrancar sin Redis. Las salas están en Redis como `movienight:room:<id>`
(`docker exec movienight-redis-1 redis-cli --scan --pattern "movienight:room:*"`). Para probar la sala perdida hay que
**detener `app-1`** antes de borrar la clave: mientras corre, el server la vuelve a escribir (el heartbeat del host la guarda).
**Qué se probó** (la persona lo confirmó, con la web como la otra punta): aviso y reconexión; el video del host no
retrocede ni se adelanta; el invitado se realinea; host y silencio; sala perdida; server caído más de 90 s; subida en
curso (las dos últimas, "pasaron", sin más detalle). **No se probó:** la recarga del video en error (prueba 4,
descartada).
**Pendientes fuera de la app** (no se tocó el server ni la web):
- **El navegador (no host) retrocede 5-6 s** al terminar el reinicio y luego se corrige con el heartbeat del host.
  Hipótesis sin confirmar: `room.html` compara `player.src.indexOf(videoFile)`; con nombres con espacios, tildes o
  símbolos `player.src` queda codificado, no coincide, y se reaplica la posición vieja de `room-data`.
- **El dueño recupera el host tras un reinicio** aunque se lo hubiera pasado a otro (probado): `join-room` concede el host
  a cualquier socket que pruebe ser el dueño y el host vive solo en memoria. Del código se deduce que también pasa con
  una reconexión normal del dueño (no se probó). Si el host era alguien que NO es el dueño y el dueño no está, tras el
  reinicio la sala queda sin host hasta que entre el dueño: la app no manda `hostToken` en `join-room` (no se cambió para
  no alterar las reconexiones normales).
Siguiente: **6B y 6C** del resto de la Fase 6 (releer el plan antes de elegir).

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
  corta** (mismo límite que en la 4A; desde la 6C sigue valiendo para salir de la sala, aunque con la app en
  segundo plano la subida ya no se corta: ver la primera entrada). La pantalla se mantiene encendida
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
  nuevo** (un `PUT` simple cortado no deja objeto en el bucket). Es un límite aceptado, no un bug. **(Superado en la 6C:
  con la app en segundo plano la subida falló siempre en la prueba real y se agregó un servicio en primer plano; ver la
  primera entrada de "Por dónde seguir".)**
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
- **Reinicio del server (6A):** `server-restarting` llega sin payload y el corte 5 s después (`transport close`). Redis guarda
  la posición con hasta ~8 s de atraso, y el host solo vive en memoria: tras un reinicio el dueño (cookie de sesión) recupera
  el host sin importar quién lo tuviera. `app-1` no arranca sin Redis.
- **Admin (6B):** el rol está en `users.role` y `requireAdmin` lo consulta en cada request (401 sin sesión, 403 cuenta normal,
  404 sin Postgres, 500 si falla la consulta); `/auth/me` no lo informa. `/admin.html` es estático y el panel usa la misma
  cookie `movienight.sid`; no hay ruta HTTP para dar el rol (solo `scripts/make-admin.js`). Las rutas POST de admin pasan
  `requireSameOrigin` con OkHttp (no manda Origin ni Referer), por si algún día hacen falta acciones nativas.
- **Notificaciones y subida en segundo plano (6C):** canales `uploads` (aviso, id 1001) y `upload_progress` (avance, silencioso, id
  1002); servicio `UploadService` con `foregroundServiceType="dataSync"` que no sube nada. El servidor no tiene push ni aviso
  previo a la expiración de salas (`ROOM_TTL_HOURS`, barrido cada 30 min; `room-error` solo después de cerrar y a los conectados).
- **PiP y rotación (7A):** `MainActivity` declara `configChanges="screenSize|smallestScreenSize|screenLayout|orientation"`: girar el teléfono o entrar
  y salir de PiP no recrean la actividad; tema, idioma y tamaño de letra sí. Sin ese `configChanges`, cerrar la ventana con la X no avisa a la
  actividad y el video no se pausa.

## Pendientes que arrastramos (no bloquean la Fase 6)

- **Recuperar contraseña de punta a punta:** el servidor no tiene configurado el envío de emails
  (`RESEND_API_KEY`, `EMAIL_FROM`, `APP_BASE_URL`).
- **Sin prueba explícita:** la reacción (`reaction`) y la
  red lenta con muchos saltos de sincronización. (El aviso `server-restarting` se probó en la 6A.) ("Te sacó de la sala" y el chat bloqueado al silenciar ya se probaron en la Fase 5.)
- El último ajuste de la pausa de la 3B (exacta a 150 ms) no se confirmó en el emulador.
- **PiP (7A):** la X ya se confirmó (ver la primera entrada). Sin probar: girar el teléfono y el tema oscuro con `configChanges`, la pantalla
  principal y el login sin ventana, el camino de antes de Android 12, PiP desactivado en Ajustes, "Subir uno nuevo" con el video reproduciéndose y
  ser expulsado con la ventana abierta. Quedan los logs `MovieNightPip` y `MovieNightSync` en el código; sin botones de reproducir/pausar en la
  ventana; el invitado que cierra la ventana vuelve a reproducir con el siguiente heartbeat del host (límite conocido, ver la primera entrada).
- **Notificaciones y servicio de subida (6C):** sin confirmar uno por uno el cuadro de permiso, "Ahora no" y el permiso negado,
  ni los avisos de "Subir y crear sala" y "Cambiar video"; sin probar con un video de varios GB ni en un teléfono real (el
  comportamiento de los servicios cambia con la batería y el fabricante); no se registra el error técnico de red de una subida
  fallida; cerrar la app desde recientes cancela la subida; sin push real (necesita el servidor). Ver la primera entrada de "Por
  dónde seguir".
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
- **Reinicio del server (6A):** sin probar la recarga automática del video si queda en error durante la caída (videos
  servidos desde disco). **Fuera de la app:** el navegador no host retrocede 5-6 s al terminar el reinicio (hipótesis
  del nombre del archivo codificado en `room.html`, sin confirmar) y el dueño recupera el host tras un reinicio aunque se
  lo hubiera pasado a otro. Ver la primera entrada de "Por dónde seguir".
- **Acceso al panel (6B):** el rol se averigua con un sondeo a `/admin/stats` y se vuelve a consultar solo al entrar a la pantalla
  principal (no al volver del navegador). Sin probar el aviso de "no hay navegador". Tras iniciar sesión en el navegador el
  panel no se abre solo (la web no redirige de vuelta). Ver la primera entrada de "Por dónde seguir".
