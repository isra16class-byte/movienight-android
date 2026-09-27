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
- [x] **¿Un solo repo o repos separados?** → **Repos separados.**
      `movienight` (web + servidor) y `movienight-android` (esta app) no
      comparten código ni historial de git — solo se comunican por red, igual
      que cualquier otro cliente.

---

## Fase 1 — Spike técnico: validar la conexión

Antes de invertir tiempo en pantallas o en el reproductor, confirmar que lo
más riesgoso del proyecto (sesión + Socket.io desde Android) funciona.

- [ ] Login (`POST /auth/login`) + confirmar que el `CookieJar` persiste la
      cookie de sesión entre requests.
- [ ] `GET /auth/me` — confirmar que el server reconoce la sesión.
- [ ] Conectar a una sala ya creada desde la web vía Socket.io, con
      `join-room` (roomId, username, hostToken/userId, password).
- [ ] Confirmar que el handshake de Socket.io manda la cookie correctamente
      (el server la lee vía `io.engine.use(sessionMiddleware)` — punto crítico
      a validar, es la parte más fácil de romper sin darse cuenta).
- [ ] Recibir `room-data`, `chat-history`, `viewer-list`, `viewer-count`.
- [ ] Mandar y recibir `chat-message`, `typing`, `reaction`.

### Riesgos técnicos a confirmar antes/durante esta fase (detectados el 2026-09-26, sin resolver todavía)

- [ ] **Compatibilidad de versión de Socket.io.** El server usa
      `socket.io ^4.7.5` (confirmado en `movienight/package.json`). El
      cliente Android (`socket.io-client-java` o equivalente) necesita una
      versión compatible con el protocolo Engine.IO v4 — no cualquier
      versión de la librería cliente sirve. Confirmar la versión correcta
      **antes** de escribir el primer código de conexión, no después: si la
      versión no matchea, el handshake ni siquiera conecta y el error puede
      no ser obvio.
- [ ] **Tráfico "cleartext" (HTTP sin TLS) para pruebas locales.** Android
      bloquea por defecto conexiones HTTP sin cifrar desde API 28+. Contra el
      túnel de Cloudflare (HTTPS) no hay problema, pero si en algún momento
      se prueba contra `http://localhost` o una IP local de la red (sin pasar
      por el túnel), la conexión se va a rechazar silenciosamente salvo que
      se configure un `network_security_config.xml` permitiendo cleartext
      para esa IP específica. Definir esto antes de la primera prueba local.
- [ ] **Confirmar que `requireSameOrigin` no bloquea las rutas que la app
      necesita.** Se vio en `server.js` que algunas rutas (las de
      `/admin/*`) chequean `Origin`/`Referer` contra el host propio. Por
      revisión rápida del código, las rutas que la app sí usa
      (`/auth/login`, `/create-room-from-upload`, etc.) no pasan por ese
      middleware — pero no se confirmó ruta por ruta de forma exhaustiva.
      Verificar esto con una request real desde la app antes de asumir que
      no hay fricción.

**Criterio de éxito de esta fase**: dos clientes en la misma sala (uno web,
uno Android) viéndose el chat en tiempo real, sin tocar nada del servidor.

---

## Fase 2 — Pantallas base (CRUD estándar)

- [ ] Login / registro (`/auth/register`, `/auth/login`, `/auth/logout`).
- [ ] Recuperación de contraseña (`/auth/forgot-password`,
      `/auth/reset-password`) — evaluar si conviene resolverlo con un
      WebView apuntando a `reset-password.html` en vez de reconstruir la
      pantalla nativa, dado que es un flujo de uso raro (una vez cada tanto).
- [ ] Crear sala reusando un video de la biblioteca
      (`POST /create-room-from-upload`).
- [ ] Ver detalle de una sala (`GET /api/room/:id`).
- [ ] Listar biblioteca (`GET /api/uploads`, alcanza con sesión iniciada,
      sin necesitar `LIBRARY_PASSWORD`).

---

## Fase 3 — Reproductor sincronizado (la parte crítica)

- [ ] Integrar Media3/ExoPlayer.
- [ ] Escuchar `sync` (play/pause/seek + heartbeat cada 4s del host) y
      aplicarlo al `ExoPlayer`.
- [ ] Emitir `sync` solo si el rol actual es host (confirmado por
      `host-status`).
- [ ] UI de controles propia (no la nativa de ExoPlayer) para poder bloquear
      el seek en invitados, igual que hace `room.html` con
      `video.controls = false`.
- [ ] Manejar `video-changed` y `subtitle-changed`.
- [ ] Reportar `buffering-status` — tener en cuenta que buffering
      intermitente con archivos grandes servidos desde R2 es **esperable**
      (confirmado como tráfico real en `movienight/docs/MEMORIA.md`,
      2026-09-10), no asumir que es un bug de la implementación Android antes
      de descartar esa causa.

---

## Fase 4 — Subida de video

- [ ] Camino con R2 (recomendado): `POST /api/uploads/presign` → `PUT`
      directo al bucket desde el dispositivo → confirmar con
      `/create-room-from-upload` o `/room/:id/change-video-from-upload`.
- [ ] Fallback sin R2: `POST /create-room` / `/room/:id/change-video`
      (multipart directo) — mismo límite de `413` de Cloudflare que ya se
      conoce del lado web si se comparte por Tunnel proxied.
- [ ] Validar el mismo límite de 5GB por archivo del PUT simple prefirmado
      (limitación ya conocida y documentada en el plan de la web, Fase 2.7).

---

## Fase 5 — Rol de host y moderación

- [ ] Consumir `make-host`, `kick-user`, `toggle-mute` (eventos ya existen en
      el server, del lado Android solo hay que dispararlos/escucharlos).
- [ ] Reaccionar a `kicked`, `mute-status`, `room-error`.

---

## Fase 6 — Extras (no bloqueante, evaluar más adelante)

- [ ] Manejar `server-restarting` (graceful shutdown del backend) con un
      aviso en pantalla, igual que hace `room.html`.
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
