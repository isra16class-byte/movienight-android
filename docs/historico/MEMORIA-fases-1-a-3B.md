# Histórico de MEMORIA.md — Fases 1, 2 (Sesión B), 3A y 3B

Archivado el 2026-10-08 desde `docs/MEMORIA.md` para que ese archivo se lea rápido. El texto está
**tal cual estaba** (no se reescribió). Si algo de acá contradice a `docs/MEMORIA.md`, manda
`docs/MEMORIA.md`: es lo vigente. Está en orden de más nuevo a más viejo.

Ojo con dos datos de este texto que después se corrigieron: (1) varias entradas dicen que Logcat con
`MovieNightSync` muestra cada `sync` **recibido**; ese log nunca existió en el código (hay log de lo
que la app emite, del buffering y de la alineación al reconectar). (2) La 3B dice que la app todavía
no emitía nada: desde la 3C emite si es host.

---

**Fase 3B hecha y probada en el emulador (2026-10-03).** Con la app como invitada y la web como host,
play, pausa y seek desde la web se reflejan en la app, y la barra de progreso del invitado es de solo
lectura (sin play/pause ni seek). En la 3B la app todavía no emitía nada (desde la 3C emite si es host, ver arriba).
**Cómo sigue al host** (`net/SyncLogic.kt` + `room/RoomPlayer.kt`): lo que viene del server entra solo
por `RoomPlayer.applySync` (y por `load` con la `position` de `room-data`); lo que hace la persona va por
`togglePlay`. Ningún listener del ExoPlayer emite hacia el server, así no hace falta una bandera
`ignoreSync` y en la 3C el emitir se engancha solo al camino de la persona. Forma de los eventos
(verificada en `server.js`): `sync` = `{ type: play|pause|seek|heartbeat, time (segundos), paused? }`, que el
server retransmite tal cual y sin validar, por eso el parser es estricto; `room-data.position` =
`{ time, paused }`; `host-status` = `{ isHost, hostToken }`.
**Corrección de desfase** (umbrales en `SyncLogic.kt`, distintos a propósito de `room.html`, que salta a los 4 s):
heartbeat con el host reproduciendo → hasta 0.5 s no se toca, entre 0.5 y 1 s velocidad 1.06 / 0.94, más de 1 s
salto; `play` salta si hay más de 1 s; con el host en pausa (`pause`, `seek`, heartbeat con `paused`) el video
se alinea si hay más de 150 ms. Dos reglas contra los saltos en cadena: con el reproductor cargando el heartbeat
no corrige, y tras un salto por heartbeat hay 8 s sin nuevos saltos (salvo desfase de más de 10 s). Al volver a
estar lista tras un salto o la carga, una corrección única (`planReadyResync`) salta a donde se estima que está
el host ahora.
**Estado de la prueba:** quedó un desfase residual de **1 a 2 s** tras un seek del host o al entrar con el video en
marcha, que se corrige solo en unos segundos; se aceptó así. Tras el ajuste de la corrección al quedar lista la
persona notó la pausa "un poco retrasada"; el último ajuste (pausa exacta a 150 ms + log) **no se confirmó en el
emulador**: se dejó así. No se probó en la 3B la app como host (se probó en la 3C), ni red lenta con muchos saltos. Si el desfase
molesta, ojo: esta versión del archivo decía que Logcat con `MovieNightSync` mostraba cada `sync` recibido, pero
ese log nunca estuvo en el código (ver 3C arriba); habría que agregarlo. Límite conocido: al entrar con el video en marcha, la `position` de `room-data` puede tener hasta
4 s de antigüedad (el server la guarda en cada heartbeat) y no hay evento para pedir la actual; el siguiente
heartbeat la corrige.
(Lo que seguía de la 3B —ser host, `subtitle-changed`, `buffering-status`— se hizo en la 3C, ver arriba.)

**Fase 3A hecha y probada en el emulador (2026-10-02).** La app reproduce el video de la sala con
Media3 (`room/RoomPlayer.kt`, `net/VideoUrl.kt`): carga en pausa y en el segundo 0, play/pause local,
se pausa al pasar a segundo plano, sigue donde iba al girar el teléfono, recarga al llegar
`video-changed` y no se recarga de cero al reconectar. Probado con éxito el checklist completo de la
3A (carga, play/pause, fin del video, sala creada en la app y en la web, segundo plano, giro, cambio
de video desde la web, corte de red, salir de la sala y error con reintento). (En la 3A no escuchaba
`sync`; desde la 3B sí, ver arriba.)
**Gotcha de versiones:** el primer build de la 3A falló por Media3 `1.11.1` (Kotlin 2.2 vs el 2.0.21
del proyecto); se fijó en `1.10.1`. Detalle en el apartado de Reproductor de video.

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
(La Sesión B quedó cerrada; lo siguiente está arriba, en la Fase 3B.)

**Fase 1 completada (2026-09-28), salvo probar `reaction`.** El spike corre en un
dispositivo Android real contra el servidor de producción: login por cookie, sesión
persistente, `join-room` por Socket.IO (la cookie viaja en el handshake y el server
reconoce a la cuenta dueña como host) y chat en ambos sentidos con la web. Quedó validado
que **no hace falta tocar el servidor**. La capa `net/` sirve de base para la Fase 2.

Siguiente paso: **Fase 2** de `docs/PLAN-PRODUCCION.md` (pantallas base: login/registro
reales, biblioteca, crear sala). Cosas a tener presentes: `GET /api/room/:id` solo devuelve
`{ passwordProtected }`; solo puede haber un host a la vez (la app le quita el host a la web
si entra con la cuenta dueña); el servidor destino tiene que correr `plan-produccion`.
