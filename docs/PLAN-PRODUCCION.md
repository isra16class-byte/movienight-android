# 📱 Plan hacia producción — MovieNight Android

Este documento junta, en un solo lugar, el trabajo pendiente para llevar la app
Android de MovieNight de "no existe todavía" a una app instalable y usable en
paralelo con la web (`movienight`), sin romper nada de esa web en el camino.

Sigue el mismo criterio del plan de la web (`movienight/docs/PLAN-PRODUCCION.md`):
ordenado por fases, cada fase deja la app en un estado mejor y verificable, sin
necesitar la fase siguiente para tener sentido.

---

## Fase 0 — Decisiones de arquitectura ✅ (resuelta el 2026-09-26)

- [x] **¿Reescribir el backend o reusar el de `movienight`?** → **Se reusa
      tal cual, sin tocar nada.** La app es un segundo cliente que habla
      contra el mismo servidor (HTTP + Socket.io) que ya usa la web — mismo
      criterio que ya sigue el proyecto web de tener "un solo backend, varios
      clientes". Ver `docs/API-CONTRATO.md` para el detalle exacto de rutas y
      eventos que la app consume.
- [x] **¿Autenticación por cookie o por token?** → **Cookie `httpOnly`
      (`movienight.sid`), igual que la web.** No requiere ningún cambio en
      `server.js`. Un `CookieJar` persistente (OkHttp) maneja esto del lado
      de Android. Se revisita esta decisión más adelante si en algún momento
      se prefiere el patrón más estándar de apps nativas (token en header
      `Authorization`) — no es una necesidad técnica hoy, solo una
      preferencia de arquitectura a evaluar.
- [x] **¿Alcance de plataforma?** → **Solo Android por ahora**, nativo
      (Kotlin + Jetpack Compose). iOS queda fuera de este plan.
- [x] **¿Reproductor de video?** → **Media3 (ExoPlayer)**, el estándar actual
      de Android para reproducción de video.
- [x] **¿Contra qué versión del backend?** → **Rama `plan-produccion` de `movienight`**
      (cuentas, sesiones en Redis, presign de R2, `/health`). La rama `main` del repo web
      es la versión vieja (sin `/auth/*`) y no sirve para esta app. Confirmado el 2026-09-28
      leyendo `server.js` de ambas ramas. Además el servidor necesita `DATABASE_URL`
      (Postgres) para que `/auth/*` esté habilitado.
- [x] **¿La app permite usar salas sin cuenta (invitado anónimo)?** → **No: la app exige
      cuenta** *(decidido el 2026-10-02)*. Sin sesión iniciada solo se muestra login/registro,
      y también hace falta cuenta para unirse a una sala. Consecuencias: la app no maneja el
      flujo anónimo (no guarda ni manda `hostToken` en `join-room`, `change-video-from-upload`
      ni `upload-subtitle`), toda sala creada desde la app tiene dueño y el host se reconoce
      solo por la cookie. La web sigue aceptando invitados anónimos (el servidor no se toca).
      El `userId` persistente sigue haciendo falta (mute y reconexión).
- [x] **¿Un solo repo o repos separados?** → **Repos separados.**
      `movienight` (web + servidor) y `movienight-android` (esta app) no
      comparten código ni historial de git — solo se comunican por red, igual
      que cualquier otro cliente.

---

## Fase 1 — Spike técnico: validar la conexión

Antes de invertir tiempo en pantallas o en el reproductor, confirmar que lo
más riesgoso del proyecto (sesión + Socket.io desde Android) funciona.

- [x] Login (`POST /auth/login`) + confirmar que el `CookieJar` persiste la
      cookie de sesión entre requests. *(Probado en dispositivo 2026-09-28:
      login 200, `movienight.sid` guardada, sesión también tras cerrar la app.)*
- [x] `GET /auth/me` — confirmar que el server reconoce la sesión. *(Devuelve
      `loggedIn: true` con el id/email de la cuenta.)*
- [x] Conectar a una sala ya creada desde la web vía Socket.io, con
      `join-room` (roomId, username, hostToken/userId, password). *(Sala creada
      desde la web con la cuenta de prueba, sin contraseña.)*
- [x] Confirmar que el handshake de Socket.io manda la cookie correctamente
      (el server la lee vía `io.engine.use(sessionMiddleware)` — punto crítico
      a validar, es la parte más fácil de romper sin darse cuenta). *(Confirmado:
      `host-status { isHost: true }` sin mandar `hostToken`.)*
- [x] Recibir `room-data`, `chat-history`, `viewer-list`, `viewer-count`.
- [x] Mandar y recibir `chat-message` (en ambos sentidos web ↔ Android) y recibir
      `typing`.
- [x] Probar `reaction` (enviar y recibir). *(Hecho con la parte 2D de la Fase 7: la persona mandó y recibió emojis entre la web y el teléfono el 2026-10-11.)*
- [ ] Confirmar en la web el `typing` enviado desde la app — el código ya lo emite, no se probó todavía.

### Riesgos técnicos detectados en esta fase (2026-09-26; resueltos el 2026-09-28)

- [x] **Compatibilidad de versión de Socket.io.** *(Resuelto 2026-09-28.)* El
      lockfile del backend fija `socket.io 4.8.3` / `engine.io 6.6.9` (protocolo
      EIO v4). Según el README de `socketio/socket.io-client-java`, la serie
      **2.x** es la compatible con servidores 3.x/4.x; se usa `2.1.2` (última
      tag). Confirmado en dispositivo: el handshake conecta y el join funciona.
- [x] **Tráfico "cleartext" (HTTP sin TLS) para pruebas locales.** *(Configurado
      2026-09-28, sin probar en dispositivo.)* Android lo bloquea por defecto desde
      API 28+. Se agregó `app/src/debug/` con un `network_security_config` que lo
      permite **solo en builds debug** (release queda solo HTTPS). **Ojo:** no
      alcanza solo con eso — la cookie de sesión sale con `secure: true`, así que
      para probar por HTTP el server local tiene que arrancar con
      `SESSION_COOKIE_INSECURE=1`. Contra el túnel HTTPS no hace falta nada.
- [x] **Confirmar que `requireSameOrigin` no bloquea las rutas que la app
      necesita.** *(Verificado por código 2026-09-28.)* En `server.js` solo lo
      usan las rutas de escritura de `/admin/*`; ninguna de las que consume la
      app lo tiene. CORS (`ALLOWED_ORIGINS`) solo afecta a navegadores y un
      cliente nativo no manda `Origin`. Confirmado con requests reales desde la
      app (health, login, me y Socket.IO por el dominio propio, sin bloqueos).
- [x] **La cookie tiene que viajar en el handshake de Socket.IO.** *(Nuevo,
      2026-09-28; confirmado.)* El cliente Java solo la manda si se le pasa el
      mismo `OkHttpClient` (con el `CookieJar`) como `callFactory` **y**
      `webSocketFactory`, y con `readTimeout` largo (el long-polling se corta
      con los 10 s por defecto). Implementado en `net/RoomSocket.kt` y verificado:
      una sala creada con la cuenta de prueba devolvió `host-status`
      `{ isHost: true }` sin mandar `hostToken`.
- [x] **El servidor destino corre `plan-produccion` con Postgres.** *(Nuevo,
      2026-09-28; confirmado.)* `/health` del servidor real reporta Redis, R2 y
      Postgres activos, y `/auth/*` responde.

**Estado del spike (2026-09-28)**: el proyecto compila y corre en un dispositivo
Android real contra el servidor de producción (dominio propio por Cloudflare
Tunnel). Solo quedaba pendiente probar `reaction` (hecho después, ver checklist arriba); no bloqueaba
pasar a la Fase 2.

**Comportamiento observado que hay que tener presente (Fases 3 y 5)**: solo puede
haber un host a la vez. Al entrar la app con la cuenta dueña de la sala, el server
le quitó el host a la pestaña web que ya estaba dentro (`setHost` degrada al host
anterior). Recargar la web se lo devuelve y se lo quita a la app.

**Criterio de éxito de esta fase** ✅ *(cumplido 2026-09-28)*: dos clientes en la
misma sala (uno web, uno Android) viéndose el chat en tiempo real, sin tocar nada
del servidor.

---

## Fase 2 — Pantallas base (CRUD estándar)

> **Se hace en 2 sesiones** (la app exige cuenta, ver Fase 0). **Sesión A** = cimientos y
> autenticación (login, registro, logout, recuperar contraseña). **Sesión B** = biblioteca,
> crear sala y entrar a sala (chat), más la limpieza del spike.

- [x] Login / registro (`/auth/register`, `/auth/login`, `/auth/logout`). *(Sesión A: código
      y tests unitarios listos el 2026-10-02; probado en el emulador Medium Phone API 36.1
      contra el servidor real el 2026-10-02: registro, login, sesión persistente al cerrar y
      reabrir, logout y modo sin red funcionaron bien.)*
- [ ] Recuperación de contraseña (`/auth/forgot-password`). *(Sesión A: pantalla nativa de un
      campo; el link del email se abre en el navegador y `reset-password.html` hace el reseteo,
      sin reconstruir esa pantalla en la app. **Pendiente de probar de punta a punta:** el
      servidor todavía no tiene configurado el envío de emails (`RESEND_API_KEY`, `EMAIL_FROM`,
      `APP_BASE_URL`), así que no se pudo comprobar que llegue el correo ni que el link abra
      `reset-password.html`.)*
- [x] Crear sala reusando un video de la biblioteca
      (`POST /create-room-from-upload`). *(Sesión B: código escrito y **probado en el emulador el
      2026-10-02**, con y sin contraseña; la app queda como host.)*
- [x] Ver detalle de una sala (`GET /api/room/:id`) — **ojo:** hoy solo devuelve
      `{ passwordProtected }`, sirve para decidir si pedir contraseña, no para
      mostrar título/video (eso llegaría por `room-data` al unirse por socket). *(Sesión B:
      código escrito; la pantalla de la sala usa `/api/room/:id` para saber si existe y pedir
      contraseña, y el nombre de la cinta llega por `room-data`. **Probado en el emulador el
      2026-10-02**, junto con el chat: unirse por código/link, contraseña de sala (también la
      incorrecta) y chat en ambos sentidos con la web.)*
- [x] Listar biblioteca (`GET /api/uploads`, alcanza con sesión iniciada,
      sin necesitar `LIBRARY_PASSWORD`). *(Sesión B: forma de la respuesta verificada en
      `server.js`; **probado en el emulador el 2026-10-02**, lista los videos reales.)*

---

## Fase 3 — Reproductor sincronizado (la parte crítica)

> **Se hace en 3 sesiones**, cada una con un objetivo que se puede probar solo.
> **3A** = el video carga (la app es espectadora, sin sincronizar). **3B** = seguir a la sala
> (escuchar `sync`, controles propios, seek bloqueado a invitados). **3C** = ser host (emitir `sync`,
> `subtitle-changed`, `buffering-status`).
> **Fase 3 completa y probada en el emulador (2026-10-08).**

- [x] Integrar Media3/ExoPlayer. *(3A, probado en el emulador el 2026-10-02: el video de la sala se
      ve y suena, con play/pause local; `/uploads/...` en disco o URL absoluta de R2. **Media3
      fijado en 1.10.1**: la 1.11.x se compila con Kotlin 2.2 y este proyecto usa 2.0.21.)*
- [x] Escuchar `sync` (play/pause/seek + heartbeat cada 4s del host) y
      aplicarlo al `ExoPlayer`. *(3B, probado en el emulador el 2026-10-03 con la app como invitada y
      la web como host: play, pausa y seek desde la web se reflejan en la app. **Queda un desfase
      residual de 1 a 2 s** tras un seek del host o al entrar con el video en marcha, que se corrige
      solo en unos segundos; se dejó así a propósito. Lógica en `net/SyncLogic.kt`, umbrales
      documentados ahí. Último ajuste —la pausa deja el video exacto (150 ms)— no se probó por
      separado: no confirmado.)*
- [x] Emitir `sync` solo si el rol actual es host (confirmado por
      `host-status`). *(3C, probado en el emulador el 2026-10-08 con la app como host y la web como
      invitada: play, pausa y seek de la app los sigue la web, y el heartbeat sale cada 4 s. Solo se emite
      con el rol confirmado y el socket conectado; al desconectarse el rol se da por perdido hasta el
      `host-status` del nuevo join, y sin conexión no se encola nada. El heartbeat no se manda sin un
      video sano. Un `sync` que viene del server no se re-emite: ningún listener del ExoPlayer emite.
      Al pasar la app a segundo plano el host emite `pause`. **Bug encontrado en la prueba y corregido:**
      al volver a la sala con el mismo video cargado, la app como host ignoraba la posición de la sala y
      su heartbeat la devolvía a la posición vieja; ahora se alinea con `room-data` (`planRejoin`).)*
- [x] UI de controles propia (no la nativa de ExoPlayer) para poder bloquear
      el seek en invitados, igual que hace `room.html` con
      `video.controls = false`. *(3B, probado en el emulador: el invitado ve una barra de progreso de
      solo lectura, sin play/pause ni seek. Quien es host: ver 3C, justo abajo.)*
      *(3C, probado el 2026-10-08: el host tiene play/pausa y una barra para saltar —un solo `seek` al
      soltar— y ambos llegan a la sala. El invitado conserva la barra de solo lectura.)*
- [x] Manejar `video-changed` y `subtitle-changed`. *(`video-changed` hecho y probado en la 3A: la
      app recarga el video en pausa y desde 0. `subtitle-changed`, 3C, probado el 2026-10-08: el `.vtt`
      se carga como subtítulo de Media3 (idioma "es", activo por defecto) y también se aplica el
      `subtitleFile` de `room-data` al entrar o reconectar. Si cambia con un video ya cargado se vuelve a
      preparar el mismo video en la posición actual: hay un instante de carga. El subtítulo sobrevive a
      `video-changed`, igual que en el server. La app no sube subtítulos: llegan por HTTP desde la web.)*
- [x] Reportar `buffering-status` — tener en cuenta que buffering
      intermitente con archivos grandes servidos desde R2 es **esperable**
      (confirmado como tráfico real en `movienight/docs/MEMORIA.md`,
      2026-09-10), no asumir que es un bug de la implementación Android antes
      de descartar esa causa. *(3C, probado el 2026-10-08: lo reportan todos los roles, solo en cambios,
      solo si el video quiere reproducir y se quedó sin datos (cargar en pausa no cuenta) y se reenvía al
      reconectar. En el log, buffering solo tras un salto a una zona sin descargar (unos 3,5 s); en más de
      5 minutos de reproducción continua no hubo ninguno espontáneo: sin señal de un bug de la app. Con
      R2 sigue siendo esperable.)*

---

## Fase 4 — Subida de video

> **Se hizo en 2 sesiones, las dos hechas y probadas.** **4A** = subir un video del teléfono a la biblioteca
> (presign + `PUT`). **4B** = usar lo subido en una sala (crear sala y cambiar el video de una sala).
> El servidor tiene R2 100% funcional, así que el fallback multipart queda fuera por ahora.

- [x] Camino con R2 (recomendado): `POST /api/uploads/presign` → `PUT`
      directo al bucket desde el dispositivo → confirmar con
      `/create-room-from-upload` o `/room/:id/change-video-from-upload`.
      *(4A hecha y probada en el emulador el 2026-10-08: presign + `PUT` en streaming, con progreso,
      cancelar y reintentar; el video aparece en la biblioteca de la app y de la web, y se reproduce en una
      sala. 4B hecha y probada el mismo día: "Subir y crear sala" desde la pantalla principal, y "Cambiar
      video" dentro de la sala (biblioteca o subiendo uno nuevo) con la web viendo el cambio. Ojo: el
      servidor autoriza el cambio por el dueño de la sala, no por quién es host; ver `MEMORIA.md`.)*
- [ ] Fallback sin R2: `POST /create-room` / `/room/:id/change-video`
      (multipart directo) — mismo límite de `413` de Cloudflare que ya se
      conoce del lado web si se comparte por Tunnel proxied. *(Fuera de alcance mientras R2 funcione.)*
- [x] Validar el mismo límite de 5GB por archivo del PUT simple prefirmado
      (limitación ya conocida y documentada en el plan de la web, Fase 2.7). *(4A: la app rechaza antes de
      subir lo que supere 5 GiB − 5 MiB, con tests unitarios. **No probado con un archivo real tan grande.**)*

---

## Fase 5 — Rol de host y moderación

> **Hecha y probada en 1 sesión (2026-10-09).** Verificado en `server.js` antes de escribir código: los tres eventos los
> autoriza solo `socket.isHost`, mandan el `id` de socket en texto plano y el server no contesta (ver `MEMORIA.md`).

- [x] Consumir `make-host`, `kick-user`, `toggle-mute` (eventos ya existen en
      el server, del lado Android solo hay que dispararlos/escucharlos).
      *(Probado en el emulador el 2026-10-09: lista "En la sala" con un menú Acciones solo para el host (hacer
      host, silenciar / quitar silencio, expulsar), con confirmación al expulsar y al hacer host. Probado el menú
      en pantallas chicas, el traspaso del host con la web, y la web como host silenciando y expulsando a la
      app. **Sin probar:** el aviso a los 5 s si el server no refleja el cambio, y una sala sin dueño con la app
      como host.)*
- [x] Reaccionar a `kicked`, `mute-status`, `room-error`. *(Adelantado en la Sesión B. Probado en
      el emulador el 2026-10-02: la contraseña de sala incorrecta (`room-error`). Probado el 2026-10-09 con la web
      como host: "te sacó de la sala" (ahora con su propia pantalla) y el chat bloqueado al silenciar, incluso
      reconectando siendo silenciado. Se corrigió que el silencio local no se borraba al reconectar.)*

---

## Fase 6 — Extras (no bloqueante, evaluar más adelante)

- [x] Manejar `server-restarting` (graceful shutdown del backend) con un
      aviso en pantalla, igual que hace `room.html`. *(6A, probada en el emulador el 2026-10-09 contra el server real en
      Docker: aviso, reconexión sola, host sin retroceder, invitado alineado, host y silencio, sala perdida, server caído
      más de 90 s y subida en curso. **Sin probar:** la recarga automática del video si queda en error (video servido
      desde disco). Detalle y pendientes del server y la web en `MEMORIA.md` y `CHANGELOG.md`.)*
- [x] Evaluar si tiene sentido un acceso al panel de administración
      (`/admin/*`) desde la app — no es prioritario, el panel web ya cubre
      ese caso de uso. *(6B, probada en el emulador el 2026-10-09 contra el server real en Docker. Se eligió el alcance
      mínimo: una tarjeta "Administración" que abre `/admin.html` en el navegador, visible solo para cuentas admin;
      sin pantallas nativas del panel. Detalle en `MEMORIA.md` y `CHANGELOG.md`.)*
- [x] Notificaciones que se pueden hacer sin tocar el servidor *(6C, probadas en el emulador el 2026-10-10. A: aviso local
      cuando una subida termina o falla con la app en segundo plano, con el permiso de Android 13+ pedido una sola vez
      antes de la primera subida. B: servicio en primer plano que mantiene viva la subida y muestra su avance, porque con la
      app en segundo plano Android cortaba la red de la subida. Detalle en `MEMORIA.md` y `CHANGELOG.md`.)*
- [ ] Push real (PWA / Firebase) para avisar con la app cerrada, por ejemplo "tu sala está por expirar". *(Evaluado en la
      6C y **no hecho**: exige cambiar el servidor. Hoy el server no tiene tokens de dispositivo, ni envío de push, ni un aviso
      previo a la expiración de la sala; solo emite `room-error` después de cerrarla y a los conectados. Ver `MEMORIA.md`.)*

---

## Fase 7 — Reproducción fuera de la pantalla de la sala (PiP y segundo plano)

> **Plan escrito el 2026-10-09; no toca el servidor. 7A hecha y probada en el emulador el 2026-10-10 (la X de la ventana
> se arregló con `configChanges` y se confirmó; quedan sin probar girar el teléfono con `configChanges` y los casos de
> abajo); 7B escrita y compilada, con pruebas pendientes (ver su sección); 7C (sala en horizontal y controles sobre el video, agregada después) hecha y confirmada por la persona el 2026-10-11, con su parte 2A (comentarios flotantes del chat en pantalla completa), los niveles de ajuste del video en pantalla completa y la parte 2 de reacciones (2B en pantalla completa, sin confirmar uno por uno; 2D en vertical y en la ventana flotante, confirmada por la persona el 2026-10-11).** Dos mejoras distintas, con costo y
> riesgo muy diferentes, que se hacen **en este orden y por separado** (7A se entrega y se prueba antes de empezar 7B).
> No depende de la 6C, pero la 6C ya está hecha (aviso local de subida y servicio en primer plano de subidas): 7B puede
> reusar el permiso `POST_NOTIFICATIONS` (hoy se pide antes de la primera subida, con `rememberNotificationGate`) y el
> patrón de la 6C (un servicio en primer plano que no es dueño de la tarea y una notificación con `NotificationCompat`).

**Cómo estaba antes de la 7A** (verificado en el código el 2026-10-09):
- El reproductor es un `ExoPlayer` dentro de `RoomPlayer`, que vive en `RoomViewModel`. La pantalla lo dibuja con un
  `PlayerView` sin controles propios (los botones son de Compose: el host manda, los demás no mueven el video).
- `RoomScreen` llama a `PauseWhenAppStops`: en `ON_STOP` (salvo girar el teléfono) se llama a `RoomViewModel.onAppStopped()`,
  que pausa el video local y, **si la persona es host, también emite `sync` de pausa a toda la sala**.
- Media3 está en 1.10.1 con `media3-exoplayer` y `media3-ui`. No hay `media3-session` ni un servicio de reproducción.
  Desde la 6C el manifiesto tiene `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` y un servicio
  de subidas (`UploadService`, tipo `dataSync`). `MainActivity` no declara PiP ni `configChanges`.
- Lo visto en la 6C: con la app en segundo plano y **sin** servicio en primer plano, la subida fallaba siempre con "Se cortó
  la conexión" aunque el proceso seguía vivo (se sospecha que Android corta la red de la app; la causa técnica exacta no se
  registró). El servicio de subidas lo resolvió. Hay que esperar el mismo riesgo para la conexión de la sala y la
  reproducción en segundo plano: verificarlo en 7B.

### 7A — Mini-reproductor flotante (Picture-in-Picture)

Al salir de la app estando en una sala, el video queda en una ventana flotante sobre la otra app. Sin dependencias nuevas
(PiP viene con el sistema desde Android 8; el `minSdk` es 26).

- [x] **Decisión previa — qué pasa con la sala cuando el host entra en PiP.** Hoy salir de la app pausa a todos si eres
      host. Propuesta: **en PiP no se pausa** (ni el video local ni la sala); se pausa cuando la ventana flotante se cierra
      o se deja de ver. *(Decidido por la persona: en PiP no se pausa nada, ni siendo host ni invitado; se pausa al cerrar la
      ventana o cuando la app deja de verse del todo, por ejemplo con la pantalla apagada.)*
- [x] Declarar en el manifiesto que `MainActivity` soporta PiP (`supportsPictureInPicture`), y decidir si hace falta
      `configChanges` para que entrar en PiP no recree la actividad. **Verificar en el emulador:** el reproductor sobrevive
      porque vive en el ViewModel, pero un `PlayerView` recreado puede dejar un parpadeo o una pantalla negra.
      *(`supportsPictureInPicture` declarado. Primero sin `configChanges` (entrar en PiP recreaba la actividad y no hubo
      parpadeo), pero **cerrar con la X recreaba la actividad y no mandaba el cambio de modo de PiP**, así que el video no se
      pausaba (medido con logs). Se declaró `configChanges="screenSize|smallestScreenSize|screenLayout|orientation"`: entrar y
      salir de PiP y girar el teléfono ya no recrean la actividad; tema oscuro, idioma y tamaño de letra sí. **Efecto sobre
      girar el teléfono: sin probar** en el emulador.)*
- [x] Entrar en PiP solo cuando corresponde: la persona está en la pantalla de la sala, el video está cargado y se
      reproduce, y deja la app (botón Inicio o cambio a otra app). Nunca desde la pantalla principal, el login ni una
      sala con error, o "te sacaron". *(Android 12+: entrada automática con parámetros que se actualizan y se desactivan al
      salir de la sala; antes de Android 12: `onUserLeaveHint`. Si PiP no está disponible o falla, se pausa como siempre. En el
      log: sin ventana desde la pantalla principal. Solo se probó SDK 36.)*
- [x] Relación de aspecto de la ventana: la del video (con un valor por defecto de 16:9 si todavía no se conoce). *(Log: 16:9
      mientras no se conoce y 1920x1080 con el video cargado.)*
- [x] Mientras la ventana está activa, mostrar **solo el video** (sin chat, cabecera ni controles) y volver a la pantalla
      completa al tocarla. *(Según la persona; el log no muestra el contenido de la ventana.)*
- [x] Reemplazar la condición de `PauseWhenAppStops` por una que mire si la actividad está en PiP
      (`isInPictureInPictureMode` / el listener de cambio de modo de `ComponentActivity`). **Verificar en el emulador** en
      qué orden llegan `ON_PAUSE`, `ON_STOP` y el cambio de modo al entrar y al cerrar la ventana: la lógica de "cuándo pausar"
      va como función pura con tests, y el orden real se confirma a mano. *(`PauseOnStopTracker`. El orden al entrar y al
      volver a pantalla completa se vio en el log. Con `configChanges`, el cierre con la X llega así: `ON_STOP pip=true -> no pauso`
      y medio segundo después `MODO_PIP=false` con la actividad detenida `-> PAUSO`.)*
- [ ] Opcional: botones en la ventana (`RemoteAction`) de reproducir / pausar **solo para el host**; el invitado no los tiene.
      *(No se hizo: se dejó fuera de la primera entrega.)*
- [x] Tests de la lógica pura (cuándo entrar en PiP, cuándo pausar). La UI y el comportamiento de Android solo se prueban en
      el emulador. *(21 tests en `PictureInPictureLogicTest`.)*
- [x] **Verificar el cierre con la X de la ventana flotante:** que pause el video, el host emita `pause` y el sonido pare.
      *(Confirmado por la persona en el emulador con `configChanges`: "se pausó bien", dos cierres, uno como host y otro como
      invitado. El log: como host sale `PAUSO`, `onAppStopped isHost=true`, `playWhenReady=false` y `emito pause`; como invitado
      `PAUSO` y `onAppStopped isHost=false`, sin `emito pause`. Un primer intento sin ningún evento en el log quedó sin explicar
      —posiblemente el toque no fue sobre la X—.)*
- [ ] **Girar el teléfono en la sala y el tema oscuro, con `configChanges` declarado:** girar no debe pausar ni recrear la
      actividad y la pantalla debe adaptarse (diseño horizontal, barras, teclado); el tema oscuro sí debe recrearla sin pausar.
      *(Sin probar.)*

**Hecho cuando:** salgo de la sala a otra app y el video sigue en una ventana flotante, sin pausar a los demás; tocar la
ventana me devuelve a la sala; cerrar la ventana pausa como hoy; y en la pantalla principal, el login o una sala con
error no aparece ninguna ventana flotante. *(Según la persona se cumple, y la X se confirmó con `configChanges`. Falta
"girar el teléfono en la sala sigue funcionando como hoy" con `configChanges`; la pantalla principal sin ventana se vio en el
log, el login y la sala con error no.)*

**Límite que se acepta:** la ventana vive mientras Android mantenga la app. Si Android mata el proceso, el video se detiene.

**Límite conocido (decidido por la persona: se deja así):** un **invitado** que cierra la ventana con la X (o apaga la pantalla)
pausa su video, pero el siguiente `heartbeat` de un host que sigue reproduciendo se lo vuelve a poner en play (visto en el log:
1,1 s después de pausar) y suena en segundo plano hasta que el socket cae (~5 s). No es nuevo de PiP. Arreglarlo exigiría que
el invitado ignore `play` y `heartbeat` mientras la app no se ve (tocaría `RoomPlayer`/`RoomViewModel`), y queda para la 7B
si se hace.

### 7B — Audio y video que siguen con la pantalla apagada o en otra app (servicio de reproducción)

Más grande y de más riesgo. Se evalúa **después** de usar 7A, y solo si hace falta.

> **Estado (2026-10-12): escrita y compilada; pruebas sin hacer al 100%.** La escribió otra sesión (`5164d5b`): `RoomPlayer` y su `ExoPlayer` siguen en el `RoomViewModel`, `PlaybackBridge` presta el
> reproductor a un `MediaSessionService` (`PlaybackService`) con notificación de reproducir y pausar, y se agregó `media3-session` en 1.10.1. La persona confirmó que **compila**; dijo que la ha usado y cree que
> hasta ahora no hay problemas, pero **no hizo las pruebas completas** y no detalló qué usó. Por eso **nada de esto está marcado como probado**.
>
> Pruebas pendientes (en el teléfono real además del emulador: el ahorro de batería del fabricante puede matar el servicio):
> - [ ] Pantalla apagada, varios minutos: el audio sigue.
> - [ ] En otra app: el audio sigue.
> - [ ] Pausar y reanudar desde la notificación.
> - [ ] Salir de la sala: el servicio y la notificación desaparecen.
> - [ ] Cerrar sesión: igual.
> - [ ] Que el host salga de la sala (qué pasa con los demás).
> - [ ] Girar el teléfono y entrar a la ventana flotante (PiP) sin que se pause ni se reinicie.
> - [ ] Que la app no pida permisos al abrirla ni muestre notificaciones fuera de lugar.
> - [x] `gradlew testDebugUnitTest` (incluye `PlaybackServiceLogicTest`). **Pasó completo el 2026-10-18 sobre `522e8fc`** (`BUILD SUCCESSFUL`; reporte: 530 tests, 0 fallos, 0 ignorados; lo mostró la persona).

- [ ] **Decisión previa — qué se espera:** (1) que siga solo el audio con la pantalla apagada, o (2) también el video (eso
      ya lo cubre 7A mientras haya ventana). Confirmar con la persona.
- [ ] **Versión de Media3 y Kotlin:** agregar `androidx.media3:media3-session` en la **misma versión 1.10.1**, y **comprobar
      antes de agregarla** que no exige un Kotlin más nuevo que el 2.0.21 (así falló Media3 1.11). Si lo exigiera, avisar y
      no agregarla.
- [ ] Un `MediaSessionService` en primer plano con notificación de reproducción. Manifiesto: el servicio con
      `foregroundServiceType="mediaPlayback"`, `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Android 14+) y
      `POST_NOTIFICATIONS` (Android 13+; la 6C ya lo declara y lo pide antes de la primera subida, así que 7B solo
      decide si lo reutiliza o lo vuelve a pedir en un momento con sentido, por ejemplo al entrar a una sala con video,
      y nunca al abrir la app).
- [ ] **Mover el reproductor:** hoy el `ExoPlayer` lo crea `RoomPlayer` dentro del ViewModel. Hay que decidir si el servicio
      lo es dueño (y el ViewModel lo usa) o si se mantiene `RoomPlayer` y el servicio lo observa. Es la parte delicada:
      `RoomPlayer` tiene la lógica de sincronización (`planRejoin`, `RestartState`, correcciones de posición) y no debe
      duplicarse.
- [ ] **El socket:** con el servicio vivo el proceso se mantiene y el socket sigue conectado, así que la sincronización
      continúa. Definir qué pasa si el host sale: sigue reproduciendo para todos (distinto de hoy) o se pausa (como hoy).
- [ ] Detener el servicio al salir de la sala, al cerrar sesión, cuando la sala se cierra o expira, o si te sacan.
- [ ] Tests de la lógica pura (cuándo arrancar y detener el servicio, qué se muestra en la notificación).

**Hecho cuando:** con la pantalla apagada o en otra app el audio de la sala sigue, la notificación permite pausar, y al
salir de la sala el servicio se detiene solo y la notificación desaparece. Y la app no pide permisos al abrirla ni muestra
notificaciones fuera de lugar.

**Riesgos:** es el cambio más grande desde la Fase 3 y la UI y el servicio no se pueden compilar fuera de Android Studio,
así que cada paso se descubre en el emulador. Los servicios y su ciclo de vida se comportan distinto en un teléfono real
que en el emulador (ahorro de batería, límites del fabricante): conviene probar también en uno real antes de darlo por bueno.

### 7C — Sala en horizontal y controles encima del video (agregada el 2026-10-10; no estaba en el plan original)

En horizontal la caja 16:9 del video superaba la altura y empujaba controles y chat fuera de vista, sin scroll. Solo UI y lógica pura; sin
servidor, sin tocar `RoomPlayer`/`RoomViewModel`/`MainActivity`/PiP y sin dependencias nuevas.

- [x] **Pantalla completa horizontal** (`cc50f27`): en horizontal o con el icono se ve solo el video; controles que aparecen al tocar y se esconden a los 3 s,
      contador de conectados 5 s; la X y Atrás vuelven a vertical (retienen la vertical hasta que el teléfono queda derecho); barras del sistema ocultas;
      volumen solo para invitados; avisos de conexión y de silencio. Lógica pura en `net/FullscreenLogic.kt` (25 tests).
- [x] **Controles encima del video en vertical** (`05b571f`): play/pausa (host), barra de progreso o de salto e icono de pantalla completa sobre el video;
      debajo, nombre de la cinta y "Cambiar video". Una sola `PlayerControlsBar` y un solo `OverlayController` para las dos vistas.
- [x] **Arreglo de compilación** (`2c5dd21`): `05b571f` no compilaba (`AnimatedVisibility` de un `Box` dentro de un `Column`); el `Box` pasó a `RoomVideoBox`.
- [x] **Parte 2A: comentarios flotantes del chat sobre el video en pantalla completa** (`5c4a99b`), como el "danmaku" de la web: 6 carriles, duración según el
      largo, color del nombre por hash (host cian), solo mensajes en vivo y no de sistema, tope de 12 a la vez (decisión nuestra) y se limpian al salir.
      Lógica pura en `net/DanmakuLogic.kt` (17 tests); `RoomViewModel.liveChat` y `ui/room/RoomDanmaku.kt`. La persona probó los 10 puntos de la lista y
      funcionaron todos.
- [x] **Arreglo del teclado en vertical** (`5fdcb40`): con el teclado abierto el video se achica (tope 38 % del alto visible, piso 90 dp, como la web) en vez de
      esconderse. Lógica pura en `net/PortraitVideoLogic.kt` (9 tests). La persona probó los 5 puntos de la lista en vivo y funcionaron todos.
- [x] **Niveles de ajuste del video en pantalla completa** (`1e769d8`, `ea5379e`, `a9458b2`): un botón junto a la ✕ recorre Entero (el de siempre), Medio, Llenar y Cine
      (quita franjas de cine incrustadas, tratando el cuadro como 2,39:1). Calculado con el tamaño real de la ventana y del video, no con un aparato en particular;
      solo visual y local, se recuerda mientras la sala siga abierta. Lógica pura en `net/VideoFitLogic.kt` (14 tests). La persona dijo que todos los puntos de prueba
      pasaron bien; no detalló punto por punto.
- [ ] **Niveles de ajuste, sin confirmar:** Medio y Cine en una pantalla de otra proporción que la de la persona (la app la van a usar otras personas), y videos con
      rotación sin aplicar (`unappliedRotationDegrees`, no se tiene en cuenta).
- [ ] **Parte 2B: reacciones en pantalla completa** (`5128170`): se escucha y se manda `reaction` (texto suelto con el emoji; la app descarta lo que no sea texto de hasta 16 unidades y solo manda
      los emojis de la lista; no se dibuja nada local, el emoji vuelve del servidor), emojis que suben sobre el video como los `.float-emoji` de la web (0-80 % del ancho, 70 dp de margen, 280 dp
      de subida en 2,5 s, tope de 20 a la vez, decisión nuestra) y un botón 😊 con un panel de 8 emojis que fija el grupo lateral mientras está abierto. Lógica pura en `net/ReactionLogic.kt`
      (`ReactionLogicTest`); `RoomViewModel.liveReactions` y `ui/room/RoomReactions.kt`. **Sin confirmar uno por uno:** la lista de 13 puntos. La 2D cubrió parte (mandar y recibir entre la web y el
      teléfono, el panel de 8 en horizontal, sin efecto en la sincronización); falta el grupo lateral fijo con el panel abierto, tocar el video con el panel abierto, la lista de conectados con el
      panel abierto, salir con el panel abierto y emojis a medias, y el silenciado dentro de pantalla completa.
- [x] **Parte 2D: emojis en vertical, barra de reacciones y emojis + comentarios en PiP** (`ebef887`): en vertical, los emojis de los demás suben sobre la caja del video (recorrido adaptado a su alto) y una
      fila de los 14 emojis de la web, encima del campo de chat, para mandarlos (se esconde con el teclado, como la web; sigue visible si estás silenciado); los comentarios no se muestran en vertical. En la ventana
      flotante se ven emojis (18 sp, hasta 8) y comentarios (11 sp, 3 carriles, hasta 4), solo para ver. Lógica pura en `net/ReactionLogic.kt` (`ReactionSurface`, `reactionTravel`) y `DanmakuProfile` en
      `net/DanmakuLogic.kt` (`ReactionLogicTest` 20 tests, `DanmakuLogicTest` 21). La persona probó los 11 puntos de la lista en vivo y funcionaron todos, y dijo que los tamaños de PiP le quedaron bien.
- [ ] **Parte 2D, sin confirmar:** otros teléfonos y tamaños de ventana de PiP (los tamaños son estimaciones), el recorrido en otras alturas de video, y un aviso si se toca un emoji sin conexión (hoy no
      hace nada). El panel de pantalla completa ofrece 8 de los 14 emojis por decisión de la persona.
- [ ] **Sin confirmar uno por uno:** host e invitado en vertical, arrastrar la barra sin que se escondan los controles, sin video o con error,
      el volumen del invitado en pantalla completa, PiP desde pantalla completa, y `gradlew testDebugUnitTest`.
- [ ] **Decisiones abiertas (la persona no pidió cambiarlas):** los controles se esconden también con el video en pausa; en vertical no hay botón de
      volumen para invitados.

**Hecho cuando:** la sala se usa en vertical y en horizontal sin que los controles ni el chat queden fuera de vista. *(La persona confirmó el 2026-10-11 que
funcionó, sin detallar punto por punto; ver la lista de arriba para lo que queda sin confirmar.)*

**Reglas de trabajo de esta fase** (las mismas de siempre): no se modifica el servidor; un solo patch de código por
subfase y otro de docs aparte, tras la prueba de la persona; no se tilda nada como probado sin que ella lo confirme; no se
agregan dependencias que exijan un Kotlin más nuevo (Kotlin 2.0.21, Media3 1.10.1, AGP 8.13.2) sin avisar.

---

## Fase 8 — Rediseño visual de la app (en curso: pasos 1 a 4 hechos y confirmados; el paso 5 (sala) hecho de 5a a 5p, con 5a a 5l, 5n y 5o confirmados «hasta ahora» y 5m, el ajuste de 5o y 5p sin confirmar; el 6 empezado: el logo como icono de la app (sin confirmar del todo) y la intro con el logo a pantalla completa (hecha y confirmada por la persona))

> **Estado (2026-10-18): pasos 1 a 4 hechos y confirmados por la persona en el teléfono; el paso 5 (sala) hecho de 5a a 5p: 5a a 5l (`f6e4ee0` a `e89b8e6`), 5n y 5o confirmados «hasta ahora»; 5m (`ae9b16e`), el ajuste de 5o (`74fbf74`), los textos 5p (`35388dc` a `b926ec3`) y el arreglo `35359a7` sin confirmar; el paso 6 sin empezar. **Actualización (2026-10-20): el paso 6 empezó por el logo como icono de la app (`3928427`) y siguió con la intro con el logo a pantalla completa (`ae636a5` y `7d565fe`, confirmada por la persona); ver el paso 6.** Aparte del orden, la pestaña «Mis salas» de Home, el nombre elegido en Perfil y los avatares de cuenta quedaron confirmados (ver «Mejoras de las pestañas de Home»).** Esta fase recoge lo conversado: qué decidió la persona, qué es todavía una propuesta del asistente y qué
> falta decidir. Los pasos 1 a 5 del orden de trabajo no dependen de la temática y se hacen con colores provisionales; el paso 6 (paleta final, tipografías e imagen de fondo; el logo ya está elegido) espera a que se elija el resto. Hasta el paso 1 la app usaba el tema Material 3 por defecto de Android Studio (`ui/theme/`), con color dinámico en Android 12+
> y tipografía del sistema; desde el paso 1 el color dinámico está apagado y la paleta es **provisional** (la tipografía sigue siendo la del sistema); la web usa su propio sistema "Videoclub" (VHS/vaporwave de los 80/90, morado oscuro con rosa y cian neón; `public/style.css`). Se quiere una identidad
> **distinta a la de la web**. La app la van a usar otras personas con otros teléfonos: nada debe depender de un aparato.

**Decidido por la persona**
- [x] Dos modos, **claro y oscuro**, con un **interruptor dentro de la app** (no seguir automáticamente al teléfono).
- [x] Efecto de **vidrio esmerilado** (fondo nítido con paneles semitransparentes que difuminan lo que hay detrás), como el de una web que vio y de la que no recuerda el nombre.
- [x] Dirección de referencia: naturaleza luminosa (dos imágenes de Pinterest de copas de árboles vistas desde abajo, con verdes lima, azul cielo y acentos rosa y naranja). Solo como inspiración.
- [x] El **fondo empieza quieto**; el movimiento (árboles vivos, como en la web que vio) va en un paso aparte, que se prueba en el teléfono antes de decidir.
- [x] Intro: animación de **tiempo fijo** (~1 s) al abrir la app desde cero; si el servidor aún no respondió la comprobación de sesión, pasar a la pantalla de "cargando" que ya existe.

**Propuestas del asistente, sin decidir**
- Aplicar el vidrio en login, registro, inicio y diálogos, **no sobre el video** en la sala (desenfoque sobre video en movimiento: batería, rendimiento y legibilidad); ahí los controles
  siguen con su fondo oscuro semitransparente.
- Tres técnicas para el vidrio: (1) panel semitransparente con borde fino sobre el fondo, sin dependencias; (2) desenfoque nativo `Modifier.blur`, que de memoria solo funciona desde Android 12
  (API 31) y el `minSdk` es 26, con alternativa para teléfonos viejos; (3) una librería de vidrio, que sería una dependencia nueva y se avisa antes si exige un Kotlin más nuevo que el 2.0.21. *Sin verificar en la documentación.*
- Apagar el color dinámico para que todos vean la misma paleta.
- Orden de trabajo sugerido: brief de 3 palabras, moodboard de ~20 imágenes, elegir 2 o 3 direcciones, logo, paleta y tipografías, y recién entonces las pantallas.
- Para el logo, conceptos con significado: luna con play; dos mitades que se juntan; dos pantallas superpuestas cuya intersección es un play; ramas que parten de un mismo tronco ("gente distinta, un mismo plan"); hoja o flor con un play al centro. **Elegido por la persona (2026-10-20): `branding/logo.svg` (luna con un play, nubes y horizonte; descripción del asistente).**
  Hay un prompt para un generador de imágenes en la conversación; no hay resultado elegido.

- Con fondo **quieto**, el vidrio puede hacerse con una copia borrosa del fondo, preparada de antemano y mostrada dentro de cada panel (sin dependencias, funciona en cualquier Android, poca batería). Con fondo
  **en movimiento** el vidrio de verdad cuesta bastante más. Formas de mover el fondo, a decidir después: video corto en bucle con Media3 (sin dependencia nueva, pero más batería y peso, y no debe interferir con
  el reproductor de la sala), imagen animada (con minSdk 26 puede pedir una librería; no se sabe de memoria), capas que se mueven poco con la propia app, o una librería de animaciones (dependencia nueva; comprobar
  Kotlin 2.0.21). Siempre solo en login, registro e inicio (no en la sala), pausando cuando la app no se ve y respetando el ajuste de reducir animaciones. *Sin verificar.*

**Orden de trabajo (propuesto por la sesión guía, un patch y una sesión por paso; la memoria compartida es este repo)**
- [x] Paso 1: base del tema: modo claro y oscuro con interruptor en el encabezado de Home, preferencia con `SharedPreferences` (patrón de `NotificationPrefs`), color dinámico apagado y todos los colores en
  `Color.kt` con una paleta **provisional**. Primer arranque en oscuro: propuesta, la persona no la confirmó. Revisar el posible destello blanco al abrir en oscuro (`themes.xml` hereda de un tema claro).
  **Hecho (`1ef8f16`) y confirmado por la persona (2026-10-13):** probó en el teléfono los 8 puntos de la lista (primer arranque, interruptor, iconos de las barras, que se recuerde, destello al arrancar, login y sala en ambos modos, giro, tarjetas sin violeta) y dijo que todos funcionaron como debían; no dio detalle por punto ni dijo qué teléfono ni qué versión de Android usó. Primer arranque en oscuro (`DEFAULT_THEME_MODE`); preferencia en `ui/theme/ThemePrefs.kt`; lógica pura en `net/ThemeModeLogic.kt` (10 tests, pasados con kotlinc y un arnés propio, no con Gradle). No se agregó `windowSplashScreenBackground` (`values-v31`).
- [x] Paso 2: intro de tiempo fijo y decisión de pantalla como función pura con tests; la animación es un marcador simple; no debe repetirse al girar ni al volver de segundo plano.
  **Hecho (`a082342`) y confirmado por la persona (2026-10-13):** probó en el teléfono los 8 puntos de la lista (arranque con sesión, sin sesión, servidor inalcanzable, servidor lento, girar durante la intro, segundo plano, Atrás y abrir otra vez, cambiar el modo) y dijo que todos funcionaron bien; sin detalle por punto. Decisión de pantalla en `net/StartupLogic.kt` (`startupScreenFor`, 11 tests con kotlinc y arnés propio, no con Gradle); el reloj de 1000 ms vive en `ui/IntroViewModel.kt` (por eso no se repite al girar ni al volver de segundo plano; salir con Atrás y abrir de nuevo sí la repite); `ui/IntroScreen.kt` es un **marcador** (el nombre con fundido y escala) que se reemplaza en el paso 6. No se tocaron `SessionViewModel` ni `SessionManager`.
- [x] Paso 3: login, registro y recuperar contraseña, con vidrio sobre un fondo provisional quieto.
  **Hecho y confirmado por la persona (2026-10-14):** probó en el teléfono las dos últimas listas de puntos (borde blanco con foco, sin sombra de texto, enlaces más juntos; aviso de mínimo de contraseña; texto blanco en el modo oscuro; login, registro y recuperar contraseña en ambos modos) y dijo que todos los puntos funcionaron bien y que le gustó cómo quedó, y que con eso la pantalla de login está terminada. No dio detalle por punto ni dijo qué teléfono ni qué versión de Android usó. Las ~18 iteraciones anteriores (`4b47d6b` a `0593cf2`, con un revert en `dc0ec43`) se ajustaron con lo que la persona veía, sin listas de puntos; el diseño final es el de `0593cf2` más los dos ajustes finales (`b880f89`, `3e3d3e5`).
  Diseño final: fondo **quieto** (`ui/auth/AuthBackdrop.kt`: copia borrosa de la imagen del modo, recorte centrado, velo oscuro de 10 %), un panel de vidrio (`GlassPanel`, esquinas de 28 dp, copia muy borrosa trasladada según la posición real del panel, tinte blanco de 45 % en claro y oscuro de 55 % en oscuro, borde fino), una barra de campos de cristal (`GlassFieldGroup`, borde de un pelo; con foco, 2 dp **blancos**) y un botón de cristal (`GlassButton`); título en Playfair Display (provisional, OFL, `third_party/fonts/playfair-display/`). Dentro del panel el texto va **sin sombra** y con letra fina: oscuro en el modo claro (`GlassBarInk`) y **blanco** en el oscuro (el verde ya no se usa ahí). "Mínimo 8 caracteres." (`AuthValidation.newPasswordHint`) solo se ve con algo escrito y menos de 8 caracteres; tras tocar "Crear cuenta" manda el error de siempre. Los enlaces (`GlassLinkButton`) miden 32 dp de alto, y el login junta los dos en una columna. Lógica pura: `net/GlassLogic.kt` (`GlassLogicTest`, 24 tests) y `AuthValidation` (`AuthValidationTest`, 9), pasados con kotlinc 2.0.21 y un arnés propio, no con Gradle. Sin servidor, sin tocar `RoomPlayer`, `RoomViewModel`, `AuthViewModel`, `SessionViewModel` ni dependencias.
  **Sin confirmar:** `gradlew testDebugUnitTest`; otros teléfonos y tamaños de pantalla; el contraste del texto blanco del modo oscuro (no se midió; el del modo claro se midió antes de quitar la sombra del panel y no se repitió). **Imagen de fondo: origen y permiso de uso NO informados** (`auth_bg_light.webp`, `auth_bg_dark.webp` y sus copias en `res/drawable-nodpi/`, commit `4b47d6b`): hay que aclararlo antes de publicar, y el paso 6 las reemplaza.
- [x] Paso 4: Home.
  **Hecho y confirmado por la persona (2026-10-15):** La persona dijo que probó todos los puntos y que las pruebas salieron positivas; no dio detalle por punto ni qué teléfono ni versión de Android usó. Son 14 commits en `origin/main`, de `07e6ce7` a `9b52270` (pasos "4", 4b a 4k; los cuerpos de varios commits están vacíos y esto se armó con sus títulos y el código). Se fue ajustando con lo que la persona veía en el teléfono, sin una lista de puntos por commit.
  Diseño final: Home con fondo **quieto** propio (`HomeBackdrop` en `ui/auth/AuthBackdrop.kt`, que envuelve solo Home en `MainNavHost`; la sala no lo lleva; `GlassBackdropImages.Home` con `home_bg_{light,dark}.webp`, sus copias borrosas y `home_panel_*.webp`, generadas con `tools/glass/backdrop_tools.py prepare --prefijo home --panel`; desde 4g la misma imagen en claro y oscuro), **barra inferior de vidrio con cuatro pestañas** (Salas, Biblioteca, Subidas, Perfil; se esconde con el teclado abierto y la pestaña se guarda al girar), tarjeta de encabezado con nombre, lema y avatar (lleva a Perfil; la imagen se pone copiando `res/drawable-nodpi/home_header.*` sin tocar código, y sin ella hay un degradado oscuro provisional), todas las tarjetas con vidrio y tinte de 0,70, y ventanas flotantes de vidrio (`ui/auth/GlassDialog.kt`: crear sala y el aviso de notificaciones de `NotificationPermissionGate`). Salas = unirse por código o link; Biblioteca = subida y videos (iconos de subir, actualizar y play); Subidas = estado de la subida en curso (**provisional**); Perfil = lo que era el menú "Cuenta" (modo oscuro, administración, cerrar sesión), ahora a la vista. Las barras del sistema son transparentes en los dos modos (`MainActivity.applyAppearance`, 4k: `SystemBarStyle.dark`/`light` en la de navegación, para quitar el velo que Android ponía con navegación de 3 botones). Lógica pura: `net/HomeTabLogic.kt` (`HomeTab`, `DEFAULT_HOME_TAB`, `homeTabAt`, `showHomeBottomBar`) con `HomeTabLogicTest` (5 tests, según el commit `9351914`). Sin servidor, sin tocar `RoomPlayer`, `RoomViewModel` ni dependencias.
  **Sin confirmar:** `gradlew testDebugUnitTest` (los 5 tests de `HomeTabLogicTest` no se volvieron a correr al escribir este patch de docs); otros teléfonos y tamaños; la pestaña con la que abre Home (`DEFAULT_HOME_TAB` = Salas, propuesta nuestra, la persona no la eligió expresamente); con las barras transparentes, el contraste de los botones del sistema sobre lo que haya detrás no está garantizado ni medido; la pestaña Subidas es provisional. **Imágenes de fondo de Home: origen y permiso de uso NO informados** (`home_bg_*`, `home_panel_*`, commits `07e6ce7`, `37da0fa` y `ac54ef4`; en el primero se nota que los nombres de archivo vienen de generadores de imágenes): hay que aclararlo antes de publicar, y el paso 6 las reemplaza.
- [ ] Paso 5: sala (colores, tipografía y diálogos; los controles sobre el video no llevan vidrio). Se empezó **sin esperar** a que la 7B estuviera probada (la 7B sigue sin confirmar al 100%, ver su sección); no se tocaron `RoomPlayer`, `RoomViewModel` ni el servicio de reproducción.
  **Hecho en lo principal y confirmado «hasta ahora» por la persona (2026-10-16):** La persona dijo (2026-10-16) que probó todos los puntos de la sala y que «han funcionado bien hasta ahora», y del ajuste del teclado que «ahí quedó mejor»; no dio detalle por punto ni qué teléfono ni versión de Android usó, ni si el video con el teclado abierto llega a verse en 16:9. Son 6 commits en `origin/main`, ajustados con lo que la persona veía, sin una lista de puntos por commit (los cuerpos de los commits y el código son la fuente de lo que sigue):
  `f6e4ee0` (5a) fondo de vidrio de Home en la sala y cabecera, fila de la cinta y avisos de vidrio (`ui/room/RoomGlass.kt`: `RoomGlassBar`, `RoomSolidZone`); `c94d1b6` (5b) chat de vidrio: una hoja detrás de la lista, reacciones, aviso de silenciado y campo, con burbujas de tinte liso (`RoomChatColors`, tokens provisionales en `Color.kt`; contraste medido con `tools/glass/measure_chat.py`); `2030b37` (5c, «bloque») y `510ef7a` (5d) la sala en vertical como un bloque de vidrio de borde a borde, bajo la barra de estado y la de navegación; `be1c432` (5e) **una sola hoja de vidrio** (`RoomGlassSheet`) a pantalla completa detrás de todo, con las piezas transparentes (`RoomGlassBar(flat = true)`) y la caja del video opaca por el medio.
  **Ajuste del teclado (`10d7d83`):** con el teclado abierto, dentro de la sala, `RoomHeader` se esconde y queda solo el espacio de la barra de estado (`showRoomHeader` en `net/PortraitVideoLogic.kt`, con un test nuevo; `PortraitVideoLogicTest` pasó con kotlinc 2.0.21 y un arnés propio, no con `gradlew`). El video sigue con su tope de 38 % del alto visible con teclado (decisión copiada de la web, sin cambiar).
  **Sin hacer / sin confirmar (a 2026-10-16; casi todo se resolvió en 5f a 5l, ver «Continuación» abajo):** los estados que no son el chat (comprobando, entrando, contraseña, expulsado, fallo) **siguen sobre fondo liso interino** (`RoomSolidZone`); pantalla completa, ventana flotante, fichas de reacción y el diálogo de conectados no se tocaron en este paso; `gradlew testDebugUnitTest`; otros teléfonos y tamaños; contraste de los colores del chat solo medido sobre `home_panel_*`; la cabecera con el teclado abierto deja «Salir» y «conectados» fuera de alcance hasta cerrarlo (decisión del asistente al pedirse el ajuste, la persona no la discutió); se quita sin animación; si el video con teclado llega a 16:9 no se informó. El origen y permiso de las imágenes de fondo siguen sin informarse.
  **Continuación (2026-10-18): 5f a 5p, 16 commits de `66730f8` a `b926ec3` (con los dos de «Mis salas», `61d9974` y `8c4bba7`, son 18 desde `3ed6c19`).** La persona dijo de 5f a 5l que «esta bien todo», con una objeción (el diálogo «En la sala» desordenado), y de 5n y 5o que «funcionó bien»; no dio detalle por punto ni qué teléfono ni versión de Android usó.
  - **5f a 5l (confirmados «hasta ahora»):** 5f `66730f8` diálogos de la sala con `GlassDialog`; 5g `4974e5c` la cinta y «Cambiar video» se mudan a la lista de conectados; 5h `485dbc1` los estados de la sala en una tarjeta de vidrio (ya no existe `RoomSolidZone`; `tools/glass/measure_state_card.py`); 5i `5b1603e` con el teclado abierto la cabecera pasa a una fila compacta con «Salir» y «conectados» (`RoomHeaderMode`, `roomHeaderModeFor` en `net/PortraitVideoLogic.kt`); 5j `4a522b5` con el teclado el video llega al 50 % del alto visible (era 38 %, valor copiado de la web); 5k `f4b5aa0` campo de chat fino y sin etiqueta (`GlassCompactField`) y botón «Enviar» más fino; 5l `e89b8e6` animación de la cabecera al cambiar de modo. `35359a7` arregla un import de `TextButton` que 5f había dejado sin querer y rompía la compilación.
  - **5m (`ae9b16e`, sin confirmar):** ordena el diálogo «En la sala»: cinta en dos líneas, «Cambiar video» como botón, separador y «Conectados · N».
  - **5n y 5o (confirmados «hasta ahora»; el ajuste, sin confirmar):** `d7b99b9` color por nombre en el chat (`net/ChatNameColorLogic.kt`, 8 colores por modo, provisionales; medido con `tools/glass/measure_names.py`); `5c61c57` responder deslizando, con barra de cita y recuadro de cita (`net/ChatReplyLogic.kt`); `74fbf74` los mensajes propios se deslizan a la izquierda y los ajenos a la derecha.
  - **5p (sin confirmar):** `35388dc`, `108f7c8`, `a5b3339` y `b926ec3`: español neutro con «tú», sin tecnicismos ni códigos, mensajes repetidos juntos en `net/CommonMessages.kt`, `UserMessagesGuardTest` como guarda, y topes con elipsis en los nombres. Los textos que manda el servidor no se tocan.
  - **Decisión de alcance:** pantalla completa, ventana flotante y fichas de reacción **no se tocan**: van encima del video y siguen con el fondo oscuro semitransparente (regla de esta fase).
  - **Excepciones a «no tocar `RoomViewModel`», autorizadas por la persona una por una:** `sendChat(text, replyTo)` y `RoomSocket.sendChat` (`5c61c57`), solo literales de texto (`a5b3339`) y una llamada en la rama `RoomData` para «Mis salas» (`61d9974`).
  - **Sin confirmar:** `gradlew testDebugUnitTest`; otros teléfonos y tamaños; en oscuro siete de los ocho colores de nombre bajan de 4,5 de contraste en el peor píxel de la burbuja propia (el peor es 4,24; medido solo sobre `home_panel_*`); los textos que manda el servidor siguen en voseo. Se tilda el paso 5 cuando la persona confirme 5m, el ajuste de 5o y 5p.
- [ ] Paso 6: temática final: logo, paleta, tipografías e imagen de fondo con derechos claros; el movimiento del fondo, aparte.
  **Parte del logo hecha (`3928427`, 2026-10-20), sin confirmar del todo:** el logo elegido es ahora el icono de la app (icono adaptable con fondo `#75A38F` y primer plano `ic_launcher_foreground.xml` a escala 0,80, elegida por la persona entre 0,80, 0,85 y 0,90 con una simulación; 10 `.webp` heredados; `branding/logo.svg`). **Sin capa monocromo** (decisión de la persona, tras ver una simulación de dos opciones), sin icono de notificaciones y sin versión simplificada. Paquete `web-icons/` entregado para el servidor (fuera del repo; no se sabe si se aplicó). Solo recursos y `branding/`: sin servidor, `RoomPlayer`, `RoomViewModel`, servicio 7B, dependencias, paleta, tipografías ni intro.
  **Lo que dijo la persona (2026-10-20):** en el emulador «me parece que no encaja» (el lanzador lo dibuja dentro de un anillo menta `#94E4CA` que no es de la app, medido por el asistente en una captura de 181 x 205 px) y «en los otros teléfonos si se ve bien»; sin detalle por teléfono ni versión de Android, y sin resultado de las 4 pruebas pedidas. La causa del anillo **no está confirmada** (hipótesis: iconos sin capa monocromo; prueba pendiente: apagar «Iconos temáticos» en el emulador y anotar su versión de Android).
  **Sin confirmar:** esas pruebas, otros lanzadores, fabricantes y versiones, los iconos temáticos de Android 13 o superior, `gradlew testDebugUnitTest` (sin código nuevo), y el **origen y permiso de uso del logo** (no informados; el SVG traía un bloque `<metadata>` de procedencia que ya estaba quitado). El asistente no compiló nada de Android.
  **Parte de la intro hecha (`ae636a5` y `7d565fe`, 2026-10-20) y confirmada por la persona:** «el despeje» a pantalla completa. La pantalla arranca llena de nubes con las formas del logo, se abren hacia los lados y dejan ver el logo de `branding/logo.svg` (luna, play, horizonte y reflejo, sin el cuadro redondeado: el verde es toda la pantalla); el nombre sube y aparece bajo el reflejo, después el eslogan, y la escena se acerca un poco hasta el final. **Fondo verde `#75A38F` en los dos modos** (decisión de la persona; no sale del tema); nombre y eslogan en crema `#F4FAED`. Son 2 commits: A (`ae636a5`, lógica pura `net/IntroAnimationLogic.kt` con 17 tests) y B (`7d565fe`, la pantalla: `ui/MovieNightLogo.kt`, `ui/IntroScreen.kt`, `net/IntroSceneLogic.kt` con 11 tests, y el eslogan movido de `HomeScreen.kt` a `strings.xml` como `home_tagline`, con el mismo texto). Sin servidor, `RoomPlayer`, `RoomViewModel`, `HomeViewModel`, servicio 7B, dependencias, paleta, tipografías, `themes.xml`, `startupScreenFor`, `IntroViewModel`, `INTRO_DURATION_MS` (1000) ni `INTRO_ANIMATION_MS` (700).
  **Cómo se llegó al diseño:** la sesión A se pensó como el logo dentro de su cuadro con el nombre debajo. La persona dijo que esperaba el logo «fusionado» con el nombre y las nubes moviéndose «como despejándose para que nosotros entremos», a **pantalla completa**; el asistente dibujó un boceto y la persona decidió: verde en los dos modos, «bien» al acercamiento de salida y unas 10 nubes extra. **No se hizo el fundido de salida** hacia la pantalla siguiente: exige un `Crossfade` en `AppRoot`, que cambiaría también las demás transiciones; queda como propuesta.
  **Lo que dijo la persona (2026-10-20):** que todas las pruebas de la lista pedida por el asistente (arranque en oscuro y en claro, último cuadro igual al logo, cinco aperturas seguidas, animaciones del sistema en 0, letra al máximo y horizontal, servidor lento o sin red, cambio de modo, barras del sistema) «dieron resultados positivos»; sin detalle por prueba, ni teléfono ni versión de Android.
  **Sin confirmar:** `gradlew testDebugUnitTest` (los 28 tests de la intro se corrieron con kotlinc 2.0.21 y un arnés propio, no con Gradle); el **contraste** del texto crema sobre el verde (2,67:1 calculado por el asistente: el nombre es grande, pero el eslogan de 16 sp queda por debajo de 4,5:1; la persona no pidió cambiarlo); otros teléfonos y versiones de Android; si `IntroAnimationLogic` conserva cosas que la pantalla ya no usa (`IntroAdvance.logo` e `INTRO_LOGO_FADE_*`). El asistente no compiló nada de Android; que compila sale de que la persona probó la intro en el teléfono (interpretación).
  **Falta del paso 6:** paleta final, tipografías, imagen de fondo con derechos claros y el movimiento del fondo, aparte; el origen y permiso de uso del logo y las pruebas del icono siguen sin informar. Se tilda cuando la persona confirme.
  **Arreglo menor (`9a118aa`, 2026-10-20), confirmado por la persona:** el botón «Actualizar» del diálogo «Cambiar el video de la sala» se partía en «Actualiz / ar» porque el título de su fila no tenía weight; ahora lleva `weight(1f)` y `padding(end = 8.dp)` (`ChangeVideoDialog.kt`, una línea de lógica visual). No se compiló nada de Android; la persona dijo que las pruebas «dieron resultados positivos», sin detalle. Otras filas parecidas (Mis salas y Biblioteca en `HomeScreen.kt`, `ProfileRow`) se reportaron y no se cambiaron.

**Mejoras de las pestañas de Home (fuera del orden de la Fase 8; propuestas el 2026-10-18)**
- [x] **Salas: «Mis salas».** Lista local por cuenta de las salas a las que se entró (máximo 20), comprobada con `GET /api/room/:id` (404 la quita; una sala vacía que sigue existiendo se queda). Sin servidor: no existe un endpoint de «mis salas». `61d9974` y `8c4bba7`. **Hecho y confirmado por la persona (2026-10-18):** «todas pruebas dieron resultados positivos»; sin detalle por punto. Vive en el teléfono: no se sincroniza entre dispositivos y se pierde al borrar los datos. El vencimiento de las salas es por inactividad (`ROOM_TTL_HOURS`, ver MEMORIA).
- [x] **Perfil: nombre elegido por la persona.** `debad00` (lógica pura, `ProfilePrefs` y la declaración de `username` en `RoomViewModel`) y `42d8bfe` (fila «Editar nombre» y `EditNameDialog` en la pestaña Perfil). **Hecho y confirmado por la persona (2026-10-18):** «todas las pruebas dieron positivo»; sin detalle por punto ni qué teléfono ni versión de Android usó. Se guarda solo en el teléfono, por cuenta, y se manda como `username` al entrar (el servidor lo usa tal cual, recortado a 40); se aplica la próxima vez que se entra a una sala, no en vivo. No es único ni está protegido. Único cambio en `RoomViewModel`, autorizado por la persona: la declaración de `username`. Lógica pura en `auth/ProfileNameLogic.kt` (12 tests, con kotlinc y un arnés propio, no con Gradle). `gradlew testDebugUnitTest` completo pasó el 2026-10-18 (530 tests, 0 fallos). **Sin confirmar:** otros teléfonos y tamaños; el mensaje «No se pudo guardar…» (no se vio).
- [x] **Avatares de cuenta (reemplazan a la foto de perfil).** El servidor está hecho (repo `movienight`, `7ac4edb` a `62aadf8`, 6 iconos reales) y la app los usa: `5e076af` (lógica pura, modelos, red y caché de imágenes), `cdf3640` (elegir en Perfil) y `28c73d0` (avatar en el chat y en la lista de conectados). **Hecho y confirmado por la persona (2026-10-19):** probó los 11 puntos de la lista y «dieron resultados positivos»; sin detalle por punto ni qué teléfono ni versión de Android usó. Sin librerías nuevas (OkHttp y `BitmapFactory`, caché en memoria y en disco). Se ve en Perfil, el chat y los conectados; **no** en «Mis salas», el encabezado de Home ni la cita de una respuesta. Un cambio de avatar lo ven los demás al volver a entrar a una sala. Sin `RoomViewModel`, `RoomPlayer` ni servidor tocados. Al probar, el servidor de la persona aún no tenía el código (`Cannot GET /api/avatars`) y hubo que reconstruir su imagen de Docker; ver MEMORIA. **Sin confirmar:** `gradlew testDebugUnitTest` con estos commits (146 tests de lógica pura pasaron con kotlinc y un arnés propio, no con Gradle); otros teléfonos y tamaños; el contraste de la inicial y cuánto crecen las burbujas. Contrato en `docs/API-CONTRATO.md`, «Avatares». Pendiente del servidor: origen y licencia de los iconos sin informar.
- [x] **Home: avatar en el círculo del encabezado y Perfil con portada propia y tarjetas por tema.** `18758b5` (el círculo del encabezado muestra el avatar de la cuenta, con área táctil de 48 dp; la pestaña Perfil ya no lleva encabezado: `showHomeHeader`) y `7f84643` (portada `ProfileCover` con el avatar de 88 dp, nombre y correo; tarjetas «Cuenta», «Preferencias» y «Cerrar sesión»; `ProfileSection` reemplaza a `ProfileCard`). **Hecho y confirmado por la persona (2026-10-19):** de la lista de 12 puntos de la portada dijo que «todos los puntos dieron resultados positivos»; sin detalle por punto ni qué teléfono ni versión de Android usó. De la lista de 10 puntos de `18758b5` no dio un resultado aparte (la de la portada cubre que Salas, Biblioteca y Subidas se vean igual con el avatar en el círculo). Solo `HomeScreen.kt`, `HomeTabLogic.kt` y `HomeTabLogicTest` (6 tests, con kotlinc y un arnés propio, no con Gradle); sin servidor, `RoomViewModel`, `HomeViewModel` ni dependencias. Contraste del texto blanco de la portada **calculado**, no medido sobre una imagen: 5,74:1 sobre blanco puro (peor caso) y 16,85:1 sobre el degradado de respaldo, porque `home_header.*` no existe en el repo. **Sin confirmar:** `gradlew testDebugUnitTest`; otros teléfonos y tamaños; el glifo «✎» fuera del teléfono de la persona; el ripple del círculo del encabezado queda casi tapado por el avatar (observación del asistente, sin cambiar).
- [ ] **Subidas: historial local de las últimas 10 subidas** con «Crear sala» en las terminadas: propuesta del asistente, sin decidir.

**Por decidir**
- [ ] Temática final, **paleta de cada modo** (claro y oscuro), tipografías y logo (con búsqueda inversa de imágenes para comprobar que no se parezca a otro; términos de uso del generador, sin revisar). **El logo ya se eligió (2026-10-20); esa comprobación de origen y permiso sigue sin informarse.**
- [ ] Imagen de fondo: **hoy el repo trae `auth_bg_*.webp` de origen y permiso NO informados** (paso 3); las de Pinterest **no se usan en la app** (no se sabe su origen ni si hay derecho de uso, y no se suben al repo). Hace falta una propia, generada (revisando sus términos) o de un banco libre de derechos; con un peso razonable para móvil.
- [x] Qué modo arranca la primera vez y **dónde se guarda** la preferencia (la app ya guarda ajustes en `notify/NotificationPrefs.kt` y `auth/UserIdStore.kt`; no se revisó cómo).
  **Resuelto en el paso 1:** arranca en oscuro y se guarda en `SharedPreferences` (`ThemePrefs`). El primer arranque en oscuro estaba entre los puntos que la persona dijo que funcionaron.
- [ ] Qué hace la intro con la pantalla de arranque propia de Android 12+ (no investigado) y que no se repita al girar el teléfono ni al volver de segundo plano.
  **Parcial (2026-10-13):** se leyó la documentación oficial (guía de splash screens de Android): desde Android 12 el sistema muestra su pantalla de arranque en arranques en frío y templados, con el `windowBackground` del tema (si es un color liso) y el icono del launcher por defecto, y no la muestra en arranque caliente; se puede cambiar con `windowSplashScreenBackground` y `windowSplashScreenAnimatedIcon` sin librería. Sigue **sin comprobar**: si un icono transparente empalma mejor con la intro, y si el icono del sistema se ve antes de la animación en el teléfono de la persona (el punto se pidió observar, pero ella no dio detalle). En modo claro la pantalla de arranque usa el color oscuro del tema XML (el XML no puede leer la preferencia). Lo de no repetir al girar ni al volver de segundo plano quedó resuelto y confirmado en el paso 2.
- [ ] **Textos del servidor en voseo.** Los errores y avisos que manda el servidor (`room-error`, avisos del chat como «X se unió a la sala») no se pueden cambiar sin tocar el servidor; algunos están en voseo («Esperá … y volvé a intentar»). Decidir si se trata aparte.

**Pantallas a cubrir (verificadas en `AppRoot.kt` y `ui/`):** carga, error de conexión (con "Reintentar"), iniciar sesión, registro, recuperar contraseña (no funciona de punta a punta: falta el envío de emails en el servidor, y esta fase no lo arregla), inicio
(biblioteca, unirse con código o link, subir video, acceso a administración, cerrar sesión) y sala (vertical, pantalla completa horizontal y ventana flotante, con los diálogos de cambiar video, conectados y progreso de subida). Prioridad de pulido: login, inicio y sala.

**Reglas al implementar (las de siempre):** el servidor no se toca; el rediseño es visual y no toca `RoomPlayer` ni `RoomViewModel`, salvo las excepciones que la persona autorice una por una (hasta ahora: `sendChat` con la cita, los textos, el registro de «Mis salas» y la declaración de `username` para el nombre de Perfil; ver el paso 5 y «Mejoras de las pestañas de Home»); sin dependencias que exijan un Kotlin más nuevo que el 2.0.21 sin avisar; la decisión de a qué pantalla ir tras la
intro, como función pura con tests; lo que dependa de Android (animación, arranque, modo claro y oscuro, el vidrio) solo se confirma en el teléfono.

---

## Resumen — por dónde empezar

1. **Fase 1** (spike técnico) — es la que más barato prueba si el enfoque es
   viable, antes de construir ninguna pantalla real.
2. **Fase 2** (pantallas base) — una vez confirmada la conexión, es trabajo
   más mecánico y de menor riesgo.
3. **Fase 3** (reproductor sincronizado) — la fase de mayor riesgo técnico
   real del proyecto, dejarla para cuando el resto ya esté sólido.
4. **Fase 4 y 5** — completan el paralelo funcional con la web.
5. **Fase 6** — solo si hace falta, no es parte del alcance mínimo.
6. **Fase 7** — mejoras de reproducción fuera de la sala: primero 7A (PiP), y 7B solo si hace falta. 7C (sala en horizontal y controles sobre el video) ya está hecha, con su parte 2A (comentarios flotantes), los niveles de ajuste del video y la parte 2 de reacciones (2B en pantalla completa y 2D en vertical y PiP).
7. **Fase 8** — rediseño visual de la app, distinto al de la web: pasos 1 (tema claro/oscuro), 2 (intro), 3 (login, registro y recuperar contraseña) y 4 (Home) hechos y confirmados; el 5 (sala) hecho de 5a a 5p (5a a 5l, 5n y 5o confirmados «hasta ahora»; 5m, el ajuste de 5o y los textos 5p sin confirmar); el 6 (temática final) empezó por el logo como icono de la app (hecho, sin confirmar del todo) y la intro con el logo a pantalla completa (hecha y confirmada); faltan paleta, tipografías e imagen de fondo. «Mis salas» (Home) y los avatares de cuenta hechos y confirmados.

---

*Este documento es un plan, no un estado — a medida que se resuelva cada
punto, tacharlo acá y reflejar el cambio en `docs/MEMORIA.md` (estado actual,
para lectura rápida) y en `docs/CHANGELOG.md` (nuevas entradas), mismo
criterio que ya usa `movienight` (la web) para su propio plan.*
