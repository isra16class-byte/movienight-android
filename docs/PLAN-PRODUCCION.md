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
- [ ] Probar `reaction` (enviar y recibir) y confirmar en la web el `typing`
      enviado desde la app — el código ya los emite, no se probaron todavía.

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
Tunnel). Solo queda pendiente probar `reaction` (ver checklist arriba); no bloquea
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

- [ ] Manejar `server-restarting` (graceful shutdown del backend) con un
      aviso en pantalla, igual que hace `room.html`. *(Adelantado en la Sesión B. La reconexión
      automática tras cortar y devolver la red se probó en el emulador el 2026-10-02; el aviso de
      `server-restarting` en sí no se provocó.)*
- [ ] Evaluar si tiene sentido un acceso al panel de administración
      (`/admin/*`) desde la app — no es prioritario, el panel web ya cubre
      ese caso de uso.
- [ ] PWA/notificaciones push si en algún momento hace falta avisar fuera de
      la app (ej. "tu sala está por expirar").

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

---

*Este documento es un plan, no un estado — a medida que se resuelva cada
punto, tacharlo acá y reflejar el cambio en `docs/MEMORIA.md` (estado actual,
para lectura rápida) y en `docs/CHANGELOG.md` (nuevas entradas), mismo
criterio que ya usa `movienight` (la web) para su propio plan.*
