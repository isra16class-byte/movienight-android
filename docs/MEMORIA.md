# 📎 Memoria del proyecto (activa) — MovieNight Android

**Leé esto primero** al retomar el proyecto (vos o una IA). Resumen corto a
propósito — el detalle histórico completo, cuando exista, va a vivir en
`docs/historico/` (por ahora vacío, recién arranca el proyecto).

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
- **Reproductor de video**: Media3 (ExoPlayer) — planeado, todavía no
  integrado (ver Fase 3 del plan).
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
        RoomViewModel.kt        # comprueba la sala, contraseña, socket, estado del chat
        RoomPasswordCache.kt    # pasa la contraseña de una sala recién creada (solo en memoria)
      ui/
        AppRoot.kt              # elige pantalla según SessionState; un NavHost por estado
        auth/                   # LoginScreen, RegisterScreen, ForgotPasswordScreen, componentes
        home/HomeScreen.kt      # unirse por código/link + biblioteca + diálogo de crear sala
        room/RoomScreen.kt      # sala: cabecera, contraseña, error, chat (el reproductor es la Fase 3)
      net/                  # capa de red, sin nada de UI (pensada para reusarse)
        PersistentCookieJar.kt  # CookieJar persistente (movienight.sid)
        MovieNightApi.kt        # llamadas HTTP genéricas (get / postJson), devuelven código + cuerpo
        RoomSocket.kt           # wrapper de Socket.IO: join-room y eventos del server como RoomEvent
        RoomEvents.kt           # RoomEvent, ChatMessage, Viewer y parseServerEvent() (JVM + org.json)
        LibraryParsing.kt       # parseLibrary(), parseCreatedRoomId(), formatFileSize()
        RoomIds.kt              # extractRoomId() (código o link), isRoomPasswordError(), videoDisplayName()
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
    historico/               # Vacío por ahora — para cuando este archivo crezca
                              # demasiado y convenga archivar detalle viejo,
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
Lo que sí hace: bajar `kotlinc` 2.0.21 desde las releases de GitHub (el mismo Kotlin del
proyecto), compilar y ejecutar los archivos de lógica pura (sin imports de Android: `auth/AuthValidation.kt`,
`net/AuthErrors.kt`, `net/UrlUtils.kt`) con un mini-runner que imita JUnit, y pasar el resto por el
parser para detectar errores de sintaxis. Desde la Sesión B también se instala un JDK (`apt-get update && apt-get install openjdk-21-jdk-headless`) para
compilar `org.json` desde `stleary/JSON-java` y poder probar los parsers de JSON; en Gradle el equivalente es
`testImplementation(libs.org.json)` (el `org.json` de `android.jar` está "mockeado" en tests unitarios y no parsea).
**Mantener la lógica testeable sin dependencias de
Android.** Ojo con `/auth/*` dentro de un comentario KDoc: en Kotlin `/*` abre un comentario
anidado y rompe la compilación (ya pasó una vez).

## Por dónde seguir

**Fase 2, Sesión B hecha y probada en el emulador (2026-10-02).** Quedaron hechos:
biblioteca (`GET /api/uploads`), crear sala (`POST /create-room-from-upload`), unirse por código o
link, y la sala con chat (comprobar sala, contraseña, mensajes, "escribiendo…", lista de conectados,
estados de host / silenciado / expulsado / servidor reiniciando), más el `userId` persistente. El spike
se borró. La forma de `GET /api/uploads` se verificó en `server.js`: `[{ filename, displayName, size, mtime }]`.
**Qué está verificado:** los 59 tests de lógica pura y parsers pasan, y la Sesión B compiló y corrió en el
emulador (Medium Phone API 36.1) contra el servidor real, sin cambios de código tras el primer build.
Se probó con éxito: biblioteca con los videos reales; crear sala con y sin contraseña (la app queda
como host); unirse desde la app a una sala creada en la web (con y sin contraseña, y con contraseña mala);
chat en ambos sentidos con la web; salir de la sala; cortar y devolver la red dentro de la sala.
**Sin prueba explícita todavía** (necesitan un host que los dispare o un reinicio del server): "te sacó de
la sala", chat bloqueado al silenciar y el aviso `server-restarting`.
Sigue pendiente (no bloquea): recuperar contraseña de punta a punta (el server no tiene emails configurados).
Siguiente: **Fase 3** (reproductor Media3 sincronizado). Antes de escribir código, revisar en `server.js`
cómo llegan `sync`, `video-changed` y `subtitle-changed`, y cómo se sirve el video (`/uploads/...` en
disco o URL pública de R2).

**Fase 1 completada (2026-09-28), salvo probar `reaction`.** El spike corre en un
dispositivo Android real contra el servidor de producción: login por cookie, sesión
persistente, `join-room` por Socket.IO (la cookie viaja en el handshake y el server
reconoce a la cuenta dueña como host) y chat en ambos sentidos con la web. Quedó validado
que **no hace falta tocar el servidor**. La capa `net/` sirve de base para la Fase 2.

Siguiente paso: **Fase 2** de `docs/PLAN-PRODUCCION.md` (pantallas base: login/registro
reales, biblioteca, crear sala). Cosas a tener presentes: `GET /api/room/:id` solo devuelve
`{ passwordProtected }`; solo puede haber un host a la vez (la app le quita el host a la web
si entra con la cuenta dueña); el servidor destino tiene que correr `plan-produccion`.
