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
- **Networking**: por definir en la Fase 1 (Retrofit/OkHttp + `socket.io-client`
  para Java/Kotlin son los candidatos naturales, sin decisión cerrada
  todavía).

## Estructura de archivos (estado inicial, sin código propio todavía)

```
movienight-android/
  app/
    build.gradle.kts       # applicationId, minSdk, targetSdk
    src/main/java/com/isra16/movienight/
      MainActivity.kt       # plantilla default de Android Studio, sin modificar
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

## Cómo se trabaja en este repo

Mismo flujo que ya usa `movienight` (la web):

- El asistente (IA) no hace push directo. Clona el repo → hace el cambio →
  commit local (con las credenciales reales de la persona, no las del
  asistente, para que el historial de GitHub quede a su nombre) → genera un
  patch con `git format-patch` → lo entrega como archivo descargable → la
  persona lo aplica con `git am nombre.patch` y hace `git push` ella misma.
- Cada cambio importante se refleja acá (`docs/MEMORIA.md`, si cambia algo
  esencial) y como entrada nueva en `docs/CHANGELOG.md`.

## Por dónde seguir

**Todo por hacer** — el proyecto recién se creó (2026-09-26) con la plantilla
default de Android Studio ("Empty Activity"), sin ningún código propio
todavía. El primer paso real es la **Fase 1** de `docs/PLAN-PRODUCCION.md`:
un spike técnico para confirmar que la conexión (login + sesión + Socket.io)
funciona desde Android, antes de construir ninguna pantalla.
