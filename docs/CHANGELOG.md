# Changelog — MovieNight Android

Historial de cambios activo. Entradas nuevas van arriba de todo. El detalle
histórico archivado, cuando exista, va a vivir en `docs/historico/`.

---

## 2026-10-02 — Fase 2, Sesión B: biblioteca, crear sala y sala con chat (sin probar en emulador)

- **Verificado en `server.js` (`plan-produccion`)** antes de escribir código: `GET /api/uploads`
  responde `[{ filename, displayName, size, mtime }]` (ya ordenado, con sesión no pide contraseña);
  `POST /create-room-from-upload` toma `{ filename, password? }` y responde `{ roomId, hostToken }`;
  `chat-message` y `viewer-list` tienen la forma que usa la app. Los ids de sala son 6 hex.
  Documentado en `API-CONTRATO.md`.
- **Nuevo**: pantalla principal con la biblioteca (cargando / error con reintento / vacía / lista con
  tamaño y fecha), diálogo "Crear sala" con contraseña opcional, y "Unirse a una sala" por código
  o link pegado (`extractRoomId`).
- **Nuevo**: pantalla de sala. Comprueba que la sala exista (`GET /api/room/:id`), pide la contraseña si
  la tiene (y la vuelve a pedir si es incorrecta o está bloqueada), abre el socket y muestra el chat
  (historial, mensajes propios a la derecha, avisos del sistema, citas, "escribiendo…"), la cantidad
  de conectados con su lista, "Sos el host", reconexión automática con aviso, silenciado, expulsión
  y servidor reiniciando. El video es un recuadro con el nombre de la cinta: el reproductor es la Fase 3.
- **`userId` persistente** por instalación (`UserIdStore`), como en la web; excluido de los backups.
- **`RoomSocket` refactorizado**: en vez de loguear texto emite `RoomEvent` tipados
  (`parseServerEvent`). Cliente de Socket.IO con `readTimeout` de 60 s en el `AppContainer`.
- **Una llamada con 401 manda a login** (el `SessionManager` vuelve a consultar `/auth/me`).
- **Borrado el spike** (`SpikeScreen.kt`, `SpikeViewModel.kt`).
- **Tests**: 5 archivos nuevos (errores de API, ids de sala, nombre de usuario, parsers de biblioteca
  y de eventos de sala); en el entorno del asistente pasan los 59 tests del proyecto. Se suma
  `testImplementation(org.json:json:20240303)` porque el `org.json` de `android.jar` no sirve en tests unitarios.
- **No verificado**: la UI Compose, los ViewModels y `RoomSocket` no se compilaron (sin Android SDK). Puede
  haber errores de compilación al sincronizar Gradle, y falta toda la prueba en el emulador (ver
  "Por dónde seguir" en `MEMORIA.md`).

---

## 2026-10-02 — Fase 2, Sesión A probada en emulador

- **Primer build real y prueba**: el proyecto sincroniza y compila sin cambios (Navigation
  Compose `2.8.9` resolvió bien) y corre en el emulador Medium Phone API 36.1, con
  `movienight.baseUrl` apuntando al servidor real.
- **Funciona**: registro, login, sesión persistente (cerrar y reabrir la app), logout y el
  estado `Unreachable` con "Reintentar" sin red. El home muestra el email de la sesión.
- **Pendiente (no bloquea la Sesión B)**: recuperar contraseña de punta a punta. El servidor
  todavía no tiene el envío de emails configurado (`RESEND_API_KEY`, `EMAIL_FROM`,
  `APP_BASE_URL`), así que no se verificó que llegue el correo ni que el link abra
  `reset-password.html`. La app sí llama a `/auth/forgot-password` y muestra el mensaje genérico.
- Checkboxes de login/registro tildados en el plan; el de recuperar contraseña sigue abierto.

---

## 2026-10-02 — Fase 2, Sesión A: cuenta obligatoria, login, registro y sesión

- **Decisión nueva: la app exige cuenta** (también para unirse a una sala). Sin sesión solo se
  ve login/registro; no hay flujo anónimo ni `hostToken`. Registrada en `MEMORIA.md` y en la
  Fase 0 del plan.
- **Verificado en `server.js` (`plan-produccion`)**: `POST /auth/register` responde `201` pero
  **no deja sesión** (la app hace login después); los errores son `{ error: "..." }` en español;
  reglas: email `^[^\s@]+@[^\s@]+\.[^\s@]+$` y contraseña de mínimo 8. Corregido en
  `API-CONTRATO.md` (incluye `/auth/reset-password`, que antes figuraba sin detallar).
- **Nuevo**: Navigation Compose; `AppContainer` + `MovieNightApp`; `SessionManager`
  (`/auth/me`, login, registro+login, forgot-password, logout); pantallas de login, registro y
  recuperar contraseña; home provisional con el email y "Cerrar sesión". La raíz usa un
  `NavHost` distinto por estado de sesión, así la pila se reinicia sola al entrar o salir.
- **Estado extra `Unreachable`** además de cargando / sin sesión / con sesión: si `/auth/me` no
  se puede consultar (sin red, servidor caído) se ofrece "Reintentar" en vez de mandar a login.
- **URL del servidor fija por build** (`buildConfigField`, leída de `local.properties`); se
  quita el campo de texto del spike. **Hay que crear `local.properties`** (ver
  `local.properties.example`).
- **Login con la cookie no guardada** (ej. server HTTP sin `SESSION_COOKIE_INSECURE=1`): se
  avisa con un mensaje claro en vez de simular un login.
- **Tests unitarios**: `AuthValidationTest` y `AuthErrorsTest` (18 casos), más los 4 de
  `UrlUtilsTest`. Ejecutados con `kotlinc` 2.0.21 + un mini-runner: 22 de 22 pasan. Corriendo
  esa compilación apareció (y se corrigió) un bug real: `/auth/*` dentro de un KDoc abre un
  comentario anidado en Kotlin y rompía la compilación.
- **No verificado**: la parte de UI/Compose y la integración con OkHttp no se pudieron
  compilar en el entorno del asistente (sin SDK ni Maven). Solo se pasó por el parser de
  `kotlinc` (sin errores de sintaxis). **Falta el primer build y la prueba en dispositivo**
  (registro, login, cerrar y reabrir, logout, credenciales malas, 3 intentos fallidos para ver
  el bloqueo 429, recuperar contraseña —llega el email y el link abre en el navegador— y sin
  red / modo avioneta al abrir). Si Gradle falla al sincronizar, sospechar primero de
  Navigation Compose `2.8.9` (versión elegida sin poder resolver dependencias).
- `SpikeScreen`/`SpikeViewModel` quedan sin usar; se borran en la Sesión B.

---

## 2026-09-28 — Fase 1 completada: spike probado en dispositivo real

- El spike compila y corre en un teléfono Android real contra el servidor de producción
  (`plan-produccion`, dominio propio por Cloudflare Tunnel; `/health` con Redis, R2 y Postgres
  en `ok`).
- **Verificado**: `POST /auth/login` (200, cookie `movienight.sid` guardada en el CookieJar),
  `GET /auth/me` (`loggedIn: true`), sesión que sobrevive al cierre de la app,
  `join-room` a una sala creada desde la web, y recepción de `chat-history`, `room-data`,
  `viewer-count`, `viewer-list` y `host-status`.
- **Punto crítico confirmado**: la cookie de sesión viaja en el handshake de Socket.IO —
  `host-status { isHost: true }` llegó sin mandar `hostToken`, porque el server reconoció la
  sesión de la cuenta dueña de la sala. Requiere pasar el `OkHttpClient` con el `CookieJar` como
  `callFactory` y `webSocketFactory` (hecho en `RoomSocket`).
- **Chat en ambos sentidos** entre la web y la app (mensajes propios reflejados, mensajes de la
  web recibidos, `typing` recibido). **Criterio de éxito de la fase cumplido**, sin cambios en el
  servidor.
- **Pendiente menor**: probar `reaction` y confirmar en la web el `typing` enviado desde la app.
- **Observación**: solo hay un host a la vez; al entrar la app con la cuenta dueña, el server le
  quitó el host a la pestaña web que ya estaba en la sala. A tener en cuenta en las Fases 3 y 5.
- Los riesgos técnicos de la Fase 1 quedaron todos tachados en `docs/PLAN-PRODUCCION.md`.

## 2026-09-28 — Fase 1: verificación del backend y código del spike

- **Hallazgo importante**: el backend con cuentas/sesiones (`/auth/*`, presign, `/health`,
  `io.engine.use(sessionMiddleware)`) existe solo en la rama **`plan-produccion`** de `movienight`;
  `main` es la versión vieja y no lo tiene. `API-CONTRATO.md` era correcto pero no decía de qué
  rama salía. Confirmado con el usuario que el servidor destino corre `plan-produccion`.
- **`docs/API-CONTRATO.md` corregido** contra el código real: fuente y versiones (`socket.io 4.8.3`),
  atributos de la cookie (`secure: true`, `sameSite: lax`, 30 días rolling), `GET /api/room/:id`
  devuelve solo `{ passwordProtected }`, body/respuesta de `/create-room-from-upload`
  (`{ filename, password? }` → `{ roomId, hostToken }`), campos de `change-video-from-upload` y
  `upload-subtitle` (`subtitle` + `hostToken`), y la regla de dueño de sala por sesión.
- **Riesgos de la Fase 1**: versión de Socket.IO resuelta (cliente `2.1.2`), `requireSameOrigin`
  verificado por código (solo `/admin/*`), cleartext configurado solo para debug. Agregados dos
  riesgos nuevos: la cookie en el handshake de Socket.IO (requiere pasar el `OkHttpClient` como
  `callFactory` y `webSocketFactory`) y que el servidor destino corra `plan-produccion` con Postgres.
- **Código del spike** (descartable, `SpikeScreen`/`SpikeViewModel`; la capa `net/` sí está pensada
  para reusarse): `PersistentCookieJar`, `MovieNightApi` (health/login/me/logout), `RoomSocket`
  (join-room, chat, typing, reaction y log de todos los eventos del server), `normalizeBaseUrl`
  con test unitario. Dependencias nuevas: OkHttp 4.12.0, socket.io-client 2.1.2, coroutines 1.9.0,
  lifecycle-viewmodel-compose.
- Permiso `INTERNET`; `app/src/debug/` con `network_security_config` (HTTP solo en debug); la cookie
  de sesión se excluye de los backups (`backup_rules.xml`, `data_extraction_rules.xml`).
- **No verificado**: el proyecto no se compiló ni se corrió en un dispositivo (el entorno donde se
  escribió no tiene Android SDK ni acceso a Maven). Solo se ejecutó `normalizeBaseUrl` con
  kotlinc 2.0.21. Los checkboxes de la Fase 1 siguen sin tachar hasta probarlo en un dispositivo.

## 2026-09-26 — Riesgos técnicos agregados a la Fase 1 + fix del comando `git am`

- **Revisión crítica del plan**: se detectaron 3 riesgos técnicos que el
  plan original (Fase 0/1) no contemplaba, agregados como checklist dentro
  de la Fase 1 de `docs/PLAN-PRODUCCION.md`: (1) compatibilidad de versión
  entre el cliente Socket.io de Android y `socket.io ^4.7.5` del server
  (confirmado en `movienight/package.json`), sin resolver todavía; (2)
  bloqueo de tráfico cleartext (HTTP sin TLS) por parte de Android en
  pruebas locales sin pasar por el túnel de Cloudflare; (3) confirmar que
  `requireSameOrigin` (visto en las rutas de `/admin/*`) no afecta a las
  rutas que la app sí necesita — revisado por lectura de código, no
  confirmado todavía con una request real.
- **Fix de proceso documentado en `docs/MEMORIA.md`**: `git am ~/Downloads/...`
  falla en PowerShell (`~` no se expande igual que en bash al pasarlo a un
  programa externo) — el comando correcto usa `$env:USERPROFILE`. Anotado
  como la forma exacta en que el asistente debe entregar el comando de
  ahora en adelante.

## 2026-09-26 — `docs/API-CONTRATO.md`

- Agregado `docs/API-CONTRATO.md`, documentando el contrato exacto (rutas
  HTTP + eventos de Socket.io, con shapes de payload) que la app va a
  consumir del backend de `movienight`. Confirmado leyendo directamente
  `server.js` (rutas de `/auth/*`, salas, y los 9 eventos de Socket.io que
  maneja `io.on('connection', ...)`), no de memoria — para que quede como
  referencia confiable al implementar cada fase del plan.
- Referenciado desde `docs/MEMORIA.md`.
- Sin código propio todavía — sigue pendiente la Fase 1 del plan (spike
  técnico de conexión).

## 2026-09-26 — Setup inicial del proyecto y documentación

- Creado el repositorio `movienight-android` en GitHub.
- Proyecto base generado con Android Studio (plantilla "Empty Activity",
  Jetpack Compose): `com.isra16.movienight`, minSdk 26, targetSdk 36, Kotlin
  DSL para los archivos de Gradle.
- Agregados `docs/MEMORIA.md`, `docs/PLAN-PRODUCCION.md` y este
  `docs/CHANGELOG.md`, más la carpeta `docs/historico/` (vacía por ahora),
  replicando el mismo esquema de documentación que ya usa el proyecto web
  (`movienight`).
- Decisiones de arquitectura tomadas (Fase 0 del plan, ver
  `docs/PLAN-PRODUCCION.md` para el detalle completo): reusar el backend de
  `movienight` sin modificarlo, autenticación por cookie de sesión (no
  token), solo Android por ahora (nativo, Kotlin), Media3/ExoPlayer para el
  reproductor, repos separados sin código compartido.
- Sin código propio todavía — el próximo paso es la Fase 1 del plan (spike
  técnico de conexión).
