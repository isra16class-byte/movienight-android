# Changelog — MovieNight Android

Historial de cambios activo. Entradas nuevas van arriba de todo. El detalle
histórico archivado, cuando exista, va a vivir en `docs/historico/`.

---

## 2026-10-19 — Fase 8, Home: avatar en el círculo del encabezado y Perfil con portada propia y tarjetas por tema (portada confirmada)

- **Qué:** la persona dijo (2026-10-19) de la lista de 12 puntos de la portada que «todos los puntos dieron resultados positivos»; no dio detalle por punto ni qué teléfono ni versión de Android usó. Dos commits en `origin/main`, los dos solo de la app (sin servidor, `RoomViewModel`, `HomeViewModel`, `RoomPlayer` ni dependencias nuevas):
  - `18758b5` — el círculo del encabezado de Home muestra el avatar de la cuenta (`AvatarImage` de 40 dp dentro del mismo borde blanco de 2 dp, 44 dp en total) y **la pestaña Perfil ya no lleva encabezado** (`showHomeHeader(tab)` en `net/HomeTabLogic.kt`, 1 test nuevo; `HomeTabLogicTest` pasa de 5 a 6). Sin nombre queda el icono de persona (en la práctica no ocurre: `defaultUsername` cae a «Anónimo»). Área táctil de 48 dp con una caja explícita (margen de 12 dp, el círculo visible no se mueve); el botón se anuncia «Perfil» con rol de botón. Tocarlo sigue yendo a Perfil.
  - `7f84643` — Perfil con **portada** (`ProfileCover`) y tarjetas separadas. La portada usa la forma, el fondo de respaldo y el borde del encabezado, 200 dp de alto mínimo, el avatar en un círculo de 92 dp (`AvatarImage` de 88 dp, borde blanco y fondo `0x66FFFFFF`), el nombre y el correo en blanco (una línea con elipsis) y un lápiz «✎» (glifo de texto, sin iconos nuevos) como pista; tocar el avatar abre el selector y se anuncia «Cambiar avatar». Debajo: «Cuenta» (Cambiar avatar y Editar nombre), «Preferencias» (Modo oscuro y, solo para admin, Administración) y una tarjeta con «Cerrar sesión» / «Saliendo…». `ProfileSection` reemplaza a `ProfileCard` (mismos parámetros y llamadas al ViewModel, más la imagen) y guarda los diálogos de avatar y de nombre con `rememberSaveable`; el item lleva `key = "profile"` para que ese estado no dependa de su posición cuando aparece `adminError`. Los diálogos no cambiaron. Se quitó el título «Perfil» y la fila avatar + nombre + correo.
- **Contraste de la portada (calculado, no medido sobre una imagen):** el oscurecido (`ProfileCoverScrim`) es `0x33` arriba, `0x99` desde el 45 % de la altura y `0xB3` abajo. Con la fórmula WCAG y mezcla en sRGB, el texto blanco da **5,74:1 en el peor punto sobre una imagen blanca pura** (la peor posible) y **16,85:1 sobre el degradado de respaldo**. **En el repo no existe `home_header.*`**: hoy el encabezado y la portada se ven sobre el degradado `HomeHeaderFallback`, así que no hay imagen real contra la cual medir. No se agregó script a `tools/glass`.
- **Confirmado por la persona (2026-10-19):** la lista de la portada (portada con imagen o degradado, tarjetas, tocar el avatar y la fila, editar nombre, modo oscuro, administración, cerrar sesión, textos largos, fuente al máximo y horizontal, giro con un diálogo abierto, avatar transparente, TalkBack, y que Salas, Biblioteca y Subidas con su encabezado y el avatar en el círculo se vean igual que antes). Eso cubre en la práctica lo visible de `18758b5` (interpretación del asistente), pero **la persona no dio un resultado aparte de la lista de 10 puntos de `18758b5`** (por ejemplo, el área de 48 dp, el anuncio «Perfil» del círculo, el círculo con la app recién abierta, ni la inicial al editar el nombre en ese círculo): eso no se registra como confirmado uno por uno.
- **Verificado por el asistente:** `HomeTabLogicTest` (6 tests) con kotlinc 2.0.21 y un arnés propio con stubs de JUnit (**no `gradlew`**); 3 mutaciones sobre copias (invertir la condición, devolver siempre `true`, ocultar también Subidas) fallaron como debían. Repaso con grep de los imports de `HomeScreen.kt`: lo nuevo está importado (`TextAlign`, `clearAndSetSemantics`, `showHomeHeader`) y no quedó ningún import muerto; `ProfileCard(` tenía un solo uso (`HomeScreen.kt`), ninguno en tests; `HeaderCard(` una sola llamada. Cada patch se aplicó con `git am` en un clon fresco, y al escribir este patch de docs se comprobó que los árboles de `18758b5` y `7f84643` son idénticos a los de esos patches. **El asistente no compiló nada de Android** (`HomeScreen.kt`): que la app compila y se ve así sale de que la persona la probó en el teléfono (interpretación).
- **Observación del asistente (no confirmada, no cambiada):** en el círculo del encabezado el efecto de toque (ripple) queda casi tapado por el avatar, porque el `clickable` está en la caja de 48 dp y el avatar opaco va encima; el toque funciona. En la portada se evitó con una capa de toque encima del avatar.
- **Sin confirmar:** `gradlew testDebugUnitTest` (la última corrida completa es la de `522e8fc`, 530 tests; desde entonces hay tests nuevos); otros teléfonos y tamaños; el glifo «✎» y el halo del avatar transparente en otros teléfonos o fabricantes; el contraste sobre una imagen real (no existe `home_header.*`). El texto «MovieNight» y el lema del encabezado no se tocaron (el paso 6 los revisa).
- **Solo documentación en este commit:** `MEMORIA.md` (entrada nueva en «Por dónde seguir», estructura de archivos, datos vigentes y pendientes), `PLAN-PRODUCCION.md` (ítem nuevo en «Mejoras de las pestañas de Home») y esta entrada. Las entradas históricas no se tocan.

---

## 2026-10-19 — Avatares de cuenta en la app (elegir en Perfil, verlos en el chat y en los conectados), hechos y confirmados

- **Qué:** la persona dijo (2026-10-19) que probó los 11 puntos de la lista y que «dieron resultados positivos»; no dio detalle por punto ni qué teléfono ni versión de Android usó. Ahora se puede elegir un avatar de los 6 del servidor desde Perfil y se ve junto al nombre en el chat y en la lista de conectados; sin avatar, un círculo con la inicial.
- **Commits (en `origin/main`):** `5e076af` (lógica pura, modelos, red y caché de imágenes), `cdf3640` (elegir avatar en Perfil) y `28c73d0` (chat y conectados). Sin servidor, sin `RoomViewModel`, `RoomPlayer` ni dependencias nuevas.
- **Qué hay:** `net/AvatarLogic.kt` (JVM puro, `AvatarLogicTest`, 30 tests), `net/AvatarParsing.kt` (`AvatarParsingTest`, 9), `net/AvatarDirectory.kt`, `net/AvatarImageLoader.kt`, `avatarId` en `ChatMessage` y `Viewer` (4 tests nuevos en `RoomEventsTest`, ahora 24), `MovieNightApi.putJson`, `AppContainer.avatars` y `avatarImages`, `ui/common/AvatarImage.kt`, `ui/home/AvatarPickerDialog.kt` y cambios en `HomeViewModel` (`myAvatarId`, `refreshMyAvatar`, `chooseAvatar`), `HomeScreen`, `RoomScreen` y `ViewersDialog`. Se muestra en Perfil (64 dp), el chat (20 dp) y los conectados (32 dp); no en «Mis salas», el encabezado de Home ni la cita de una respuesta. El cambio lo ven los demás al volver a entrar a una sala.
- **Lo que pasó al probar:** el selector mostró «El servidor respondió algo inesperado…» porque el servidor de la persona no tenía el código (`Cannot GET /api/avatars`). Ella reconstruyó su imagen de Docker y los avatares cargaron. No hizo falta migración a mano (el servidor crea `avatar_id` al arrancar, según `lib/db.js`; no se verificó en su base). Un `pm2 restart` dio «not found»: ese servidor corre con Docker.
- **Verificado por el asistente:** 146 tests de lógica pura con kotlinc 2.0.21 y un arnés propio (**no `gradlew`**) sobre un clon fresco con el patch aplicado (los nuevos y los que construyen `ChatMessage(` y `Viewer(`); 5 mutaciones sobre copias fallaron como debían; `origin/main` tiene el mismo árbol que se probó. El asistente vio, en una captura de la persona, avatares con imagen y una inicial en el chat (no es una prueba formal). **No se compiló nada de Android.**
- **Sin confirmar:** `gradlew testDebugUnitTest` con estos commits (la última corrida completa es la de `522e8fc`); otros teléfonos y tamaños; contraste de la inicial; cuánto crecen las burbujas; los mensajes de error del cambio (400, 404 y 429). **Pendiente del servidor:** origen y licencia de los 6 iconos (`NO INFORMADO`); `pajaro-rojo` se parece a un personaje de marca.
- **Solo documentación en este commit:** `API-CONTRATO.md` (la sección «Avatares» ya no dice que la app no los usa), `MEMORIA.md` (primera entrada de «Por dónde seguir», estructura de archivos, datos vigentes y pendientes), `PLAN-PRODUCCION.md` (el ítem de avatares pasa a hecho) y esta entrada. Las entradas históricas no se tocan.

---

## 2026-10-18 — Docs: el servidor ya tiene avatares; la app todavía no los usa (avatares del servidor confirmados por la persona)

- **Qué:** en el repo del servidor (`movienight`, rama `plan-produccion`, `7ac4edb` a `62aadf8`) se agregaron los avatares de cuenta con 6 iconos reales. Esta entrada solo deja constancia en los docs de la app; **no hay cambios de código**.
- **Contrato:** `docs/API-CONTRATO.md` suma `GET /api/avatars`, `PUT /auth/avatar`, el campo `avatar` de `/auth/me` y `avatarId` en `viewer-list` y `chat-message` (opcionales; la app actual los ignora), y una sección «Avatares». Leído en el código del servidor el 2026-10-18; no es una prueba contra el servidor en marcha.
- **Confirmado por la persona (2026-10-18):** dijo «si hice todas las pruebas» (las que se le pasaron: las de los avatares del servidor, la web de siempre y la app Android actual); no dio detalle por punto ni qué navegador, teléfono ni versión de Android usó. **Pendiente:** que la app los use (elegir en Perfil y mostrarlos; decidir cómo cargar las imágenes sin dependencias nuevas). Origen y licencia de los 6 iconos sin informar en el servidor; `pajaro-rojo` se parece a un personaje de marca.
- **Solo documentación:** `API-CONTRATO.md`, `MEMORIA.md` (entrada nueva y el pendiente de Perfil), `PLAN-PRODUCCION.md` (el ítem de la foto de perfil pasa a «Avatares de cuenta») y esta entrada.

---

## 2026-10-18 — `gradlew testDebugUnitTest` completo pasa (530 tests, 0 fallos)

- **Qué:** La persona corrió `./gradlew testDebugUnitTest` sobre `522e8fc` (`origin/main`) y mostró `BUILD SUCCESSFUL in 23s` y el reporte de Gradle 8.13 (`app/build/reports/tests/testDebugUnitTest/index.html`): **530 tests, 0 fallos, 0 ignorados**, 100 % de éxito. El asistente no lo vio correr: lo sabe por lo que la persona pegó.
- **Qué confirma:** que el proyecto compila (código principal y de tests, también la UI que el asistente nunca pudo compilar) y que pasan todos los tests, incluidos `ModerationLogicTest`, `UploadLogicTest`, `VideoUrlTest` y `UserMessagesGuardTest`, que el arnés del asistente no podía correr, y `PlaybackServiceLogicTest`. Los 530 son todos los de la carpeta de tests, no solo los del nombre de Perfil.
- **Qué no confirma:** nada visual ni del comportamiento en el teléfono, otros teléfonos y tamaños, ni las pruebas de la 7B en teléfono real.
- **Solo documentación en este commit:** MEMORIA (entrada del nombre de Perfil y pendientes), plan (casilla de `gradlew` de la 7B y línea de Perfil) y esta entrada. Los «sin confirmar `gradlew`» de las entradas históricas no se tocan: eran ciertos cuando se escribieron. Sin cambios de código.

---

## 2026-10-18 — Pestañas de Home, parte 2: nombre elegido por la persona (Perfil), hecha y confirmada

- **Qué:** La persona dijo (2026-10-18) que «todas las pruebas dieron positivo» (las 10 de la lista); no dio detalle por punto ni qué teléfono ni versión de Android usó. Ahora se puede cambiar, desde Perfil, el nombre con el que apareces en las salas; antes era siempre la parte del correo antes de la `@`.
- **Commits (en `origin/main`):** `debad00` (lógica, almacenamiento y `RoomViewModel`) y `42d8bfe` (pantalla). Los docs no se habían actualizado desde `551de4a`.
- **Qué hay:** `auth/ProfileNameLogic.kt` (JVM puro), `auth/ProfilePrefs.kt` (una clave por cuenta, solo en el teléfono; `AppContainer.profilePrefs`), `HomeViewModel` (`displayName`, `hasCustomName`, `saveDisplayName`, `resetDisplayName`) y, en Perfil, el nombre y el correo, la fila «Editar nombre» y `EditNameDialog` (campo con tope de 40, error bajo el campo, nota, «Guardar», «Cancelar» y «Usar el nombre de mi correo» si hay nombre elegido). Modo oscuro, administración y cerrar sesión siguen igual; el encabezado de Home no cambió.
- **Cómo se comporta:** el cambio vale desde la próxima vez que se entra a una sala (el `username` se calcula al crear el `RoomViewModel`); el nombre no es único ni está protegido y no se sincroniza entre teléfonos; «mis» mensajes se identifican por `userId`, no por nombre. Reglas de limpieza (decisiones del asistente): saltos de línea y tabuladores pasan a un espacio, tope de 40 unidades UTF-16 sin partir un emoji, mínimo de 2 caracteres reales.
- **Excepción a «no tocar `RoomViewModel`» (autorizada por la persona, solo esto):** la declaración `private val username` y su import.
- **Verificado por el asistente:** con grep, que `username` solo sirve para `join-room` y que «mis» mensajes van por `userId`; 15 tests de lógica pura (12 nuevos y los 3 de `UsernameTest`) con kotlinc 2.0.21 y un arnés propio (**no `gradlew`**), con mutaciones que fallaron como debían; que `origin/main` tiene el código que se probó. **Nada de la UI se compiló** (sin Android SDK).
- **Sin confirmar:** `gradlew testDebugUnitTest`; otros teléfonos y tamaños; el mensaje «No se pudo guardar…» (no se vio); `UserMessagesGuardTest` no cubre los mensajes nuevos (llevan un dígito); dos personas con el mismo nombre comparten color en el chat; los avisos del servidor siguen en voseo.
- **Solo documentación en este commit:** plan (Perfil tildado, regla de las excepciones), MEMORIA (entrada nueva, estructura de archivos, dato vigente, pendientes) y esta entrada. Sin cambios de código.

---

## 2026-10-18 — Fase 8, paso 5 (sala: diálogos, estados, teclado, chat con colores y respuestas) y textos en español neutro, hechos; pestañas de Home: «Mis salas» confirmada

- **Qué:** La persona dijo (2026-10-18) de «Mis salas» que «todas pruebas dieron resultados positivos». Antes, en la conversación, dijo de los cambios de la sala 5f a 5l que «esta bien todo», con una objeción (el diálogo «En la sala» se veía desordenado; lo arregló 5m), y de 5n y 5o que «funcionó bien», con un cambio pedido (que los mensajes propios se respondan deslizando a la izquierda; `74fbf74`). No dio detalle por punto ni qué teléfono ni versión de Android usó. **No confirmó** 5m, el ajuste de 5o (`74fbf74`), los textos nuevos (5p) ni el arreglo de compilación `35359a7`.
- **Commits (todos en `origin/main`):** 18, de `66730f8` a `8c4bba7`; los docs no se habían actualizado desde `3ed6c19`. Sala: 5f `66730f8`, 5g `4974e5c`, 5h `485dbc1`, 5i `5b1603e`, 5j `4a522b5`, 5k `f4b5aa0`, 5l `e89b8e6`, arreglo de compilación `35359a7` (faltaba el import de `TextButton` en `ChangeVideoDialog.kt` tras 5f), 5m `ae9b16e`. Chat: 5n `d7b99b9`, 5o `5c61c57` y su ajuste `74fbf74`. Textos: 5p-1 `35388dc`, 5p-2 `108f7c8`, 5p-3 `a5b3339`, 5p-4 `b926ec3`. Home: «Mis salas» `61d9974` y `8c4bba7`.
- **Sala (5f a 5m):** los diálogos de conectados y de cambiar video, con sus confirmaciones, usan `GlassDialog`; la cinta («Viendo: …») y «Cambiar video» se mudaron a la lista de conectados (`ViewersDialog`), y la fila que había bajo el video ya no existe; los estados (comprobando, entrando, contraseña, expulsado, fallo) van en una tarjeta de vidrio centrada (`RoomSolidZone` ya no existe; `tools/glass/measure_state_card.py`); con el teclado abierto la cabecera pasa a una fila compacta con «Salir» y «conectados» (`RoomHeaderMode`, `roomHeaderModeFor`) y se anima al cambiar; con el teclado el video puede llegar al 50 % del alto visible (`VIDEO_KEYBOARD_MAX_FRACTION`, antes 38 %, que era un valor copiado de la web); el campo del chat es más fino y sin etiqueta (`GlassCompactField`) y el botón «Enviar» también; 5m ordena el diálogo «En la sala» (cinta en dos líneas, «Cambiar video» como botón, separador y «Conectados · N»).
- **Chat (5n y 5o):** el nombre de cada persona lleva un color (`chatNameColorIndex`, 8 colores por modo en `Color.kt`; la clave es el nombre); se responde a un mensaje deslizándolo (los de los demás a la derecha, los propios a la izquierda: `replySwipeDirection`) con barra de cita sobre el campo y recuadro de cita en el mensaje (`net/ChatReplyLogic.kt`, cita de hasta 100 caracteres). El servidor ya aceptaba `replyTo`; la app antes solo lo mostraba.
- **Textos (5p):** español neutro con «tú» en toda la app, mensajes más cortos y sin códigos ni tecnicismos (`net/CommonMessages.kt` junta los repetidos; `UserMessagesGuardTest` vigila voseo, códigos y largo), y topes con elipsis en los nombres de personas, videos y salas. Se decidió «correo» y «enlace» y mantener «host».
- **Home, «Mis salas»:** lista local por cuenta de las salas a las que se entró (máximo 20, `RecentRoomsStore`), comprobada con `GET /api/room/:id`: un 404 la quita; una sala vacía que sigue existiendo se queda. Se registra al llegar `room-data` (no al fallar una contraseña).
- **Excepciones a «no tocar `RoomViewModel`» (autorizadas por la persona, una por una):** `5c61c57` (`sendChat(text, replyTo)` y `RoomSocket.sendChat`), `a5b3339` (solo literales de texto) y `61d9974` (una llamada en la rama `RoomData` para registrar la sala). Sin servidor, sin tocar `RoomPlayer`, el servicio 7B ni dependencias.
- **Verificado por el asistente al escribir este patch:** que los nombres citados existen en el código; 88 tests de lógica pura pasan con kotlinc 2.0.21 y un arnés propio (**no `gradlew`**): `ApiErrorsTest` 6, `AuthErrorsTest` 10, `AuthValidationTest` 9, `ChatNameColorLogicTest` 8, `ChatReplyLogicTest` 18, `PortraitVideoLogicTest` 11, `RecentRoomsLogicTest` 24 y `UsernameTest` 3. **No se pudieron correr** (necesitan `org.json` o piezas de Android que el arnés no tiene): `ModerationLogicTest`, `UploadLogicTest`, `VideoUrlTest` y `UserMessagesGuardTest`, que cambiaron con los textos. `tools/glass/measure_names.py` se corrió: contraste de los colores de nombre sobre `home_panel_*` (106 820 px; no mide la sombra ni el teléfono): en claro, burbuja propia peor 4,91 a 4,98 y ajena peor 10,41 a 10,56, todos con 100 % de píxeles >= 4,5; en oscuro, propia peor 4,24 a 4,85 (95,3 % a 100 % >= 4,5; solo `FFFF00` pasa 4,5 en el peor píxel) y ajena peor 4,92 a 5,63 (100 %); piso 3,0 cumplido y distancia mínima entre colores de 28,3 (claro) y 28,5 (oscuro) en Lab CIE76. Se leyó `server.js` (repo `movienight`, rama `plan-produccion`, commit `a9bca2a`): ver «Datos que siguen vigentes» en MEMORIA. **Nada de la UI se compiló en la sesión del asistente** (sin Android SDK).
- **Sin confirmar:** `gradlew testDebugUnitTest` (en particular los cuatro tests que no corrieron en el arnés); otros teléfonos y tamaños; 5m, el ajuste de 5o, 5p y `35359a7` por la persona; en oscuro, siete de los ocho colores de nombre quedan por debajo de 4,5 en el peor píxel de la burbuja propia (compromiso que recomendó el asistente al ver que cumplir 4,5 en todos los píxeles dejaba los nombres casi grises; la paleta es provisional); pantalla completa, ventana flotante y fichas de reacción **no se tocaron** (siguen con el fondo oscuro semitransparente por ir encima del video); los avisos del servidor (errores y avisos del chat) siguen en voseo; la 7B sigue sin probarse al 100 %. **Origen y permiso de las imágenes de fondo NO informados.**
- **Solo documentación en este commit:** plan (Fase 8, estado del paso 5 y sus sub-pasos, nueva sección de mejoras de las pestañas de Home, regla de las excepciones), MEMORIA (entrada nueva, estructura de archivos, datos vigentes y pendientes) y esta entrada. Sin cambios de código.

---

## 2026-10-16 — Fase 8, paso 5 (sala con vidrio) y cabecera escondida con el teclado, confirmados «hasta ahora» por la persona

- **Qué:** La persona dijo (2026-10-16) que probó todos los puntos de la sala y que «han funcionado bien hasta ahora», y del ajuste del teclado que «ahí quedó mejor»; no dio detalle por punto ni qué teléfono ni versión de Android usó, ni si el video con el teclado abierto llega a verse en 16:9.
- **Commits (todos en `origin/main`):** 6, de `f6e4ee0` a `10d7d83`: 5a, 5b, 5c («bloque»), 5d, 5e y el ajuste del teclado; los docs no se habían actualizado. El paso 5 se empezó sin esperar a que la 7B estuviera probada.
- **Diseño final:** una sola hoja de vidrio a pantalla completa detrás de la sala (`RoomGlassSheet`), piezas transparentes (`RoomGlassBar(flat = true)`), caja del video opaca, chat con burbujas de tinte liso (`RoomChatColors`, provisionales), y con el teclado abierto la cabecera se esconde (`showRoomHeader`, `PortraitVideoLogicTest` con 10 tests, pasados con kotlinc 2.0.21 y un arnés propio, no con Gradle).
- **Sin confirmar:** `gradlew testDebugUnitTest`; otros teléfonos y tamaños; si el video con el teclado llega a 16:9 (tope de 38 % sin cambiar); los estados de la sala siguen sobre fondo liso interino; pantalla completa, ventana flotante, fichas de reacción y diálogo de conectados no se tocaron; la 7B sigue sin probarse al 100%. **Origen y permiso de las imágenes de fondo NO informados.**
- **Solo documentación en este commit:** plan (Fase 8, paso 5 con su avance y estado; el paso queda sin tildar porque faltan los estados), MEMORIA (entrada nueva, dato vigente del teclado, pendiente y estructura de archivos) y esta entrada. Sin cambios de código.

---

## 2026-10-15 — Fase 8, paso 4 (Home con vidrio, barra inferior de cuatro pestañas y ventanas de vidrio), confirmado por la persona

- **Qué:** La persona dijo que probó todos los puntos y que las pruebas salieron positivas; no dio detalle por punto ni qué teléfono ni versión de Android usó.
- **Commits (todos en `origin/main`):** 14, de `07e6ce7` a `9b52270` (pasos "4", 4b a 4k, con arreglos en `ec16727` y `53ee707`); los docs no se habían actualizado.
- **Diseño final:** fondo quieto propio de Home (`HomeBackdrop`), barra inferior de vidrio con Salas, Biblioteca, Subidas (provisional) y Perfil, tarjeta de encabezado, tarjetas de vidrio con tinte 0,70, `GlassDialog` para crear sala y el aviso de notificaciones, iconos nuevos y barras del sistema transparentes en los dos modos. Lógica pura en `net/HomeTabLogic.kt` (`HomeTabLogicTest`, 5 tests según el commit `9351914`).
- **Sin confirmar:** `gradlew testDebugUnitTest` (los tests no se volvieron a correr al escribir este patch); otros teléfonos y tamaños; pestaña de arranque (propuesta nuestra); contraste de los botones del sistema sobre las barras transparentes (no medido). **Origen y permiso de las imágenes `home_bg_*` NO informados**: aclarar antes de publicar.
- **Solo documentación en este commit:** plan (Fase 8, paso 4 tildado y estado), MEMORIA (entrada nueva, dato vigente, pendiente y estructura de archivos) y esta entrada. Sin cambios de código.

---

## 2026-10-14 — Fase 8, paso 3 (login, registro y recuperar contraseña con vidrio sobre fondo quieto), confirmado por la persona

- **Qué:** la persona probó en el teléfono las dos últimas listas de puntos y dijo que todos funcionaron bien, que le gustó cómo quedó y que la pantalla de login está terminada. No dio detalle por punto ni dijo qué teléfono ni qué versión de Android usó.
- **Commits (todos en `origin/main`):** de `4b47d6b` a `3e3d3e5`, unas 20 iteraciones sin lista de puntos (con un revert en `dc0ec43`); el diseño final es el de `0593cf2` más `b880f89` y `3e3d3e5`.
- **Diseño final:** fondo quieto con copia borrosa y velo de 10 %, panel de vidrio, barra de campos y botón de cristal, título en Playfair Display (provisional), texto sin sombra dentro del panel (oscuro en claro, blanco en oscuro), borde blanco con foco, enlaces de 32 dp (los dos del login juntos) y "Mínimo 8 caracteres." solo con algo escrito y menos de 8 (`AuthValidation.newPasswordHint`).
- **Verificado por el asistente:** `GlassLogicTest` (24) y `AuthValidationTest` (9) pasan con kotlinc 2.0.21 y un arnés propio (no con `gradlew`); una mutación sobre una copia falló como debía. La causa de la sombra que se colaba (el `merge` de `ProvideTextStyle` conserva la sombra de afuera si la de adentro es `null`) se leyó en el fuente de Compose. Nada de la UI se compiló en la sesión del asistente.
- **Sin confirmar:** `gradlew testDebugUnitTest`; otros teléfonos y tamaños; contraste del texto blanco del modo oscuro (no medido). **Origen y permiso de las imágenes de fondo NO informados**: aclarar antes de publicar. Comentarios de código desactualizados sobre "sin panel" (patch de código aparte). Recuperar contraseña sigue sin funcionar de punta a punta (el servidor no envía emails).
- **Solo documentación en este commit:** plan (Fase 8, paso 3 tildado, "Por decidir" de la imagen de fondo), MEMORIA (entrada nueva, dato vigente, pendiente y estructura de archivos) y esta entrada. Sin cambios de código.

---

## 2026-10-13 — Fase 8, pasos 1 y 2 (tema claro/oscuro e intro de tiempo fijo), confirmados por la persona

- **Qué:** la persona probó en el teléfono la lista de cada paso y dijo que todos los puntos funcionaron como debían. No dio detalle por punto ni dijo qué teléfono ni qué versión de Android usó.
- **Paso 1 (`1ef8f16`):** modo claro y oscuro con interruptor en el encabezado de Home (no sigue al teléfono), primer arranque en oscuro, preferencia en `SharedPreferences` (`ui/theme/ThemePrefs.kt`), color dinámico apagado, todos los colores en `ui/theme/Color.kt` con paleta **provisional**, iconos de las barras y fondo de la ventana según el modo (`MainActivity.applyAppearance`), `windowBackground` de arranque fijado al color oscuro. Lógica pura: `net/ThemeModeLogic.kt` con `ThemeModeLogicTest` (10 tests).
- **Paso 2 (`a082342`):** intro de 1000 ms al abrir la app, a la vez que la comprobación de sesión y sin cortarse si el servidor responde antes; después, carga, error de conexión, login o Home según `SessionState`. Lógica pura: `net/StartupLogic.kt` con `StartupLogicTest` (11 tests). `ui/IntroViewModel.kt` (reloj; no se repite al girar ni al volver de segundo plano; salir con Atrás y abrir de nuevo sí la repite), `ui/IntroScreen.kt` (marcador, a reemplazar en el paso 6), `ui/AppRoot.kt`.
- **Verificado por el asistente:** los tests pasan con kotlinc 2.0.21 y un arnés propio (no con `gradlew`); mutaciones sobre copias fallaron como debían; se leyó la documentación oficial de la pantalla de arranque de Android 12+ (el sistema la muestra en arranques en frío y templados, con el `windowBackground` del tema y el icono del launcher). No se compiló la UI (sin Android SDK).
- **Sin confirmar:** si el icono del sistema se ve antes de la intro y si en modo claro se nota un instante oscuro al abrir; `gradlew testDebugUnitTest`. Decisiones sin cambiar: paleta e intro provisionales hasta el paso 6.
- **Solo documentación en este commit:** plan (Fase 8, pasos 1 y 2 tildados, "Por decidir" actualizado), MEMORIA y esta entrada. Sin cambios de código.

---

## 2026-10-12 — Estado de la 7B (compila, pruebas pendientes) y ajustes a la Fase 8 (solo documentación)

- **7B:** el commit `5164d5b` la dejó escrita sin compilar ni probar. La persona confirmó que **compila**. Dijo que la ha usado y cree que hasta ahora no hay problemas, pero no hizo las pruebas completas y no
  detalló qué usó: no se marca como probada. Las pruebas pendientes quedaron en la sección 7B del plan.
- **Verificado por el asistente:** en el fuente de Media3 1.10.1, `media3-session` es Java y no declara dependencia de Kotlin; trae `lifecycle-service` 2.5.1, mientras la app usa lifecycle 2.10.0. Interpretación (sin
  comprobar): no debería traer un problema nuevo con Kotlin 2.0.21; lo que lo confirmó fue compilar, hecho por la persona. No se leyó el código de la 7B ni se corrieron sus tests.
- **Fase 8:** se anotó que el **fondo empieza quieto** y el movimiento va aparte (decisión de la persona), las opciones para el vidrio y el movimiento (sin verificar) y el orden de trabajo en 6 pasos. Los pasos 1 a 5 no
  dependen de la temática; el paso 5 (sala) espera a que la 7B esté probada. Se corrigieron dos frases que decían que la 7B estaba sin empezar.
- **Nada probado ni implementado en la Fase 8.**

---

## 2026-10-12 — Fase 7B: audio de la sala con la pantalla apagada o en otra app (escrita, sin compilar ni probar)

- **Qué:** servicio de reproducción (`MediaSessionService`) con notificación de reproducir/pausar; el audio sigue con la pantalla apagada o en otra app y se detiene solo al salir de la sala.
- **Archivos nuevos:** `net/PlaybackServiceLogic.kt` (lógica pura), `notify/PlaybackBridge.kt`, `notify/PlaybackService.kt`, `PlaybackServiceLogicTest.kt`.
- **Archivos tocados:** `RoomPlayer` (foco de audio, auriculares, `onSystemPause`, `pausedMessage`, `playLocal`), `RoomViewModel` (controles de la notificación, evaluación del servicio, `onCleared`), `PauseOnStopTracker` (parámetro `backgroundAudioActive`), `RoomScreen`, `AppContainer`, `SessionViewModel` (cerrar sesión detiene el servicio), manifiesto (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, servicio `mediaPlayback`), `strings.xml`, `libs.versions.toml` y `build.gradle.kts` (`media3-session` 1.10.1).
- **No hecho / sin comprobar:** no se pudo compilar ni correr tests (sin Android SDK ni acceso a Maven); ver la primera entrada de "Por dónde seguir" para la lista de lo que hay que confirmar.

## 2026-10-12 — Rediseño visual de la app: decisiones y propuestas (solo documentación, sin código)

- **Qué es esta entrada:** no hay cambios en la app. Solo se anota lo conversado para no perderlo; se detalla en la Fase 8 del plan.
- **Origen:** la persona preguntó con qué temática de diseño cuenta la app web y quiso que la app tenga una propia, distinta. Se leyó en el código: la web usa "Videoclub" (VHS/vaporwave, fondo
  morado, rosa y cian neón; fuentes Monoton, Space Grotesk y VT323); la app usa el tema Material 3 por defecto de Android Studio (`Theme.kt`, `Color.kt`, `Type.kt`), con color dinámico en Android 12+.
  Sus colores propios son solo el fondo oscuro semitransparente de la pantalla completa y un aviso ámbar.
- **Decidido por la persona:** modo claro y oscuro con un interruptor dentro de la app; efecto de vidrio esmerilado; referencia de naturaleza luminosa (dos imágenes de Pinterest de copas de árboles);
  intro de tiempo fijo que, si el servidor aún no comprobó la sesión, pasa a la pantalla de carga existente.
- **Visto en las imágenes de referencia (medición automática del asistente, aproximada):** predominan verdes lima y amarillos (32 % a 48 % de lo vivo) y azul cielo (11 % a 30 %), con acentos rosa y naranja;
  azul cerca de `#399DE2`–`#5BA8EB` y lima cerca de `#A6A236`–`#BCC53F`. *Interpretado:* ambiente de primavera, de día, saturado. Las imágenes no están en el repo ni en la app: de Pinterest, origen y derechos sin verificar.
- **Verificado en el código:** las 7 pantallas de la app (carga, error de conexión, iniciar sesión, registro, recuperar contraseña, inicio y sala); la comprobación de sesión (`/auth/me`) corre al crearse `SessionViewModel`,
  o sea al abrir la app desde cero. **Sin verificar:** que `Modifier.blur` solo funcione desde Android 12 (de memoria), el comportamiento con la pantalla de arranque de Android 12+, y las condiciones de uso de cualquier generador de imágenes.
- **Pendiente de decidir:** temática final, paleta de cada modo, tipografías, logo, imagen de fondo con derechos claros, qué modo arranca primero y dónde se guarda la preferencia.
- **Nada probado ni implementado.**

---

## 2026-10-11 — Fase 7, parte 2: reacciones y emojis flotantes (2B en pantalla completa, 2D en vertical y en la ventana flotante), 2D confirmada por la persona

- **Pedido:** la "parte 2" original (chat y espectadores flotantes sobre el video en pantalla completa, como la web) y, después, que los emojis de los demás salgan también en vertical y en la ventana
  flotante (con los comentarios del chat) y una fila para mandarlos cerca del chat en vertical, como la web móvil. Dos patches: `5128170` (2B) y `ebef887` (2D), los dos en `origin/main`.
- **Qué se vio y qué se interpretó:** *visto (grep en `public/room.html`, `public/style.css` y `server.js`, rama `plan-produccion`):* el servidor reenvía `reaction` a toda la sala, incluido quien la manda, con el
  emoji como texto suelto, sin validar, sin limitar y sin mirar el silencio; `.float-emoji` nace entre 0 y 80 % del ancho, a 70 px del borde de abajo, sube 280 px en 2,5 s con ease-out y se desvanece, sin
  tope; el panel `.fs-emoji` tiene 4 columnas y no se cierra al elegir; `.reactions` tiene 14 botones en una fila que se desplaza y se esconde con el teclado abierto; los comentarios solo se crean en pantalla
  completa. *Interpretado, sin abrir la web en un navegador:* los emojis salen también en vertical porque se agregan a `.screen-wrap` sin comprobar pantalla completa.
- **Qué hace:** `reaction` se escucha (`RoomEvent.Reaction`; la app ignora lo que no sea texto de hasta 16 unidades) y se manda (`RoomSocket.sendReaction`, sin cola; `RoomViewModel.sendReaction`, solo los 14
  emojis). No se dibuja nada local: el emoji aparece cuando el servidor lo devuelve. **Pantalla completa (2B):** botón 😊 bajo el contador con un panel de 8 emojis que fija el grupo lateral mientras está abierto;
  los emojis suben sobre el video. **Vertical (2D):** los emojis suben sobre la caja del video (recorrido adaptado a su alto: nace al 15 % del alto, entre 4 y 70 dp, y sube hasta dejar el emoji dentro, máximo
  280 dp) y una fila con los 14 emojis encima del campo de chat, escondida con el teclado abierto y visible si estás silenciado; los comentarios no se muestran. **Ventana flotante (2D):** emojis de 18 sp (hasta 8)
  y comentarios de 11 sp en 3 carriles (hasta 4), solo para ver. Tope de emojis en pantalla completa y vertical: 20 (decisión nuestra, la web no tiene).
- **Código:** `net/ReactionLogic.kt` (nuevo; `ReactionSurface`, `reactionLookFor`, `reactionTravel`) con `ReactionLogicTest` (20 tests); `net/DanmakuLogic.kt` suma `DanmakuProfile` (`FULLSCREEN` sin cambios y `PIP`) y
  `DanmakuLanes` con separación y tope configurables (`DanmakuLogicTest`, 21); `ui/room/RoomReactions.kt` (nuevo: `ReactionLayer`, `ReactionPicker`, `ReactionBar`); cambios en `RoomFullscreen.kt`,
  `RoomScreen.kt`, `RoomDanmaku.kt`, `RoomEvents.kt`, `RoomSocket.kt` y `RoomViewModel.kt`. `REACTION_EMOJIS` pasó a `REACTION_PANEL_EMOJIS` (8) y se agregó `REACTION_BAR_EMOJIS` (14). Sin cambios en el
  servidor, `RoomPlayer`, la sincronización ni las dependencias.
- **Error mío corregido antes de escribir código:** dije que la web tenía 8 emojis; mi lectura de `room.html` cortó la lista y eran 14. La persona había elegido "8 por ahora" con ese dato; ya con el dato
  correcto pidió 14 en la barra y 8 en el panel.
- **Verificado por el asistente:** `ReactionLogicTest` (20), `DanmakuLogicTest` (21) y `RoomEventsTest` (20) pasan con `kotlinc` 2.0.21 y un arnés propio (no con `gradlew`); una mutación de los números de
  `ReactionLogic.kt` hizo fallar 5 tests; sin errores de sintaxis en los archivos de UI tocados; los dos patches aplicaron limpio sobre `origin/main` en un clon nuevo. La UI no se pudo compilar fuera de Android Studio.
- **Confirmado por la persona:** la 2D, los 11 puntos de la lista: "todos los puntos funcionaron perfectamente probado en vivo"; y los tamaños de PiP: "sí quedaron bien". No detalló punto por punto ni dijo si fue
  en teléfono o emulador.
- **Sin confirmar:** la lista de 13 puntos de la 2B (la 2D cubrió parte: mandar y recibir emojis entre la web y el teléfono, el panel de 8 en horizontal y la sincronización; faltan el grupo lateral fijo con el
  panel abierto, tocar el video con el panel abierto, la lista de conectados con el panel abierto, salir con el panel abierto y emojis a medias, y el silenciado dentro de pantalla completa). De la 2D: otros teléfonos y
  tamaños de ventana de PiP (los números son estimaciones), y el recorrido en otras alturas de video.
- **Límites conocidos:** sin conexión, tocar un emoji no hace nada y no avisa; el panel de pantalla completa ofrece 8 de los 14 emojis por decisión de la persona; desde la ventana flotante no se puede mandar nada.

---

## 2026-10-11 — Fase 7C: niveles de ajuste del video en pantalla completa (Entero, Medio, Llenar, Cine), confirmado por la persona

- **Pedido:** la persona mandó una captura con márgenes negros alrededor del video en horizontal y pidió que los videos se adapten a la pantalla. Se hizo en
  dos pasos: primero un botón "Ajustar / Llenar" (`1e769d8`) y después, a pedido suyo, más niveles ("unos 3") para distintos tipos de video, recordando que la
  app la van a usar otras personas con otros teléfonos. Los niveles quedaron en `ea5379e`; un arreglo de una anotación mía, en `a9458b2`.
- **Qué se vio y qué se interpretó (captura):** *visto/medido por el asistente, estimación sobre la imagen:* franjas negras a los lados (unos 86 px por lado de 816) y
  arriba y abajo, con una imagen de cerca de 2,4:1 en una pantalla de unos 2,2:1. *Interpretado, sin confirmar con el archivo:* un video 16:9 con franjas de cine
  metidas en el propio archivo. La app no fijaba ningún modo de escala (grep) y la web tampoco define `object-fit` (grep en `public/*.css` y `public/*.html`, rama
  `plan-produccion`): las dos muestran el video entero. Por eso no era un fallo; los niveles son propios de la app.
- **Investigación de proporciones (guías generales, sin porcentajes de uso):** 16:9 es la más común hoy; en cine, 1.85:1 y 2.39:1 (CinemaScope); 4:3 es el
  contenido viejo o de definición estándar; 2:1 (Univisium) lo popularizó Netflix; 9:16 es el video vertical del celular (ahí conviene Entero).
- **Qué hace:** en pantalla completa, un botón junto a la ✕ (icono + nombre del nivel) recorre cuatro niveles, de menos a más zoom: **Entero** (el video completo,
  el de siempre y el inicial), **Medio** (a mitad de camino entre Entero y Llenar; pensado para 4:3 y videos que Llenar recorta demasiado), **Llenar** (cubre la
  ventana sin deformar y recorta lo que sobre) y **Cine** (cubre la ventana tratando el cuadro como 2,39:1, para quitar las franjas de cine incrustadas; si el
  video ya es igual o más ancho que 2,39:1 queda igual que Llenar). La descripción para el lector de pantalla dice el nivel actual y el siguiente. El nivel se
  recuerda mientras la sala siga abierta (también al girar o pasar a PiP) y se pierde al salir. Es solo visual y local: no se sincroniza.
- **Cuentas del asistente para una pantalla de ~2,22:1 (los tests las comprueban):** un 16:9 con Llenar recorta el 20 % del alto; un 4:3, el 40 % (Medio recorta
  menos); una película 2,39:1 ya recortada, el 7 % del ancho; un 16:9 con franjas incrustadas deja con Llenar unas franjas finas de 3,5 % que Cine quita. En una
  pantalla 16:9, Cine recorta cerca de un cuarto del ancho del cuadro 2,39:1.
- **Código:** `net/VideoFitLogic.kt` (lógica pura: `VideoFit`, `videoScale`, nombres y textos) con `VideoFitLogicTest` (14 tests: 5 proporciones de pantalla por 6 de
  video); `VideoSurface` (`RoomScreen.kt`) recibe `fit`; `RoomFullscreen.kt` tiene el botón; dos iconos nuevos (`ic_video_fit`, `ic_video_fill`). Entero y Llenar usan
  `RESIZE_MODE_FIT` / `RESIZE_MODE_ZOOM` de `PlayerView`. Medio y Cine no existen en `PlayerView`: se deja el `PlayerView` en modo FIT con un tamaño propio
  (`requiredSize`, centrado, mayor que la ventana) calculado con el tamaño real de la ventana (`BoxWithConstraints`) y del video (`Player.Listener.onVideoSizeChanged`,
  solo lectura). Mientras el video no informa su tamaño, Medio y Cine se ven como Entero. Sin cambios en el servidor, `RoomPlayer`, `RoomViewModel`, la vista
  vertical ni PiP; sin dependencias nuevas.
- **Error mío corregido (`a9458b2`):** puse el `@OptIn(UnstableApi::class)` de Kotlin y no tiene efecto, porque `UnstableApi` usa el `RequiresOptIn` de AndroidX (lo
  revisa el lint). Android Studio marcaba 4 errores de lint (más el aviso "has no effect") en `RoomScreen.kt`; no impedían compilar. Ahora es
  `@androidx.annotation.OptIn(UnstableApi::class)`. La persona confirmó que los errores se quitaron.
- **Verificado por el asistente:** los 14 tests pasan con `kotlinc` 2.0.21 y un arnés propio (no con `gradlew`); sin errores de sintaxis en `RoomScreen.kt` ni
  `RoomFullscreen.kt`. En el fuente de Media3 1.10.1 (GitHub): `UnstableApi` usa el `RequiresOptIn` de AndroidX; `setResizeMode`, `getResizeMode` y
  `AspectRatioFrameLayout` son `@UnstableApi`; `media3-common` expone `annotation-experimental` como `api`; `VideoSize` (`width`, `height`,
  `pixelWidthHeightRatio`), `Player.getVideoSize()` y `onVideoSizeChanged` son API estable. La UI no se pudo compilar fuera de Android Studio.
- **Confirmado por la persona:** el botón "Ajustar / Llenar" (primer paso): dijo que todas las pruebas dieron resultados positivos. Los niveles: dijo que todos los
  puntos de prueba pasaron bien (la lista de 12). No detalló punto por punto ni dijo si fue en teléfono o emulador (su captura de Android Studio mostraba el
  emulador Medium Phone API 36.1 con la app abierta). El punto 12 (que alguien con otra proporción de pantalla mire Medio y Cine) no se detalló: no se marca como cubierto.
- **Límites conocidos:** Cine en un video 16:9 sin franjas recorta bastante arriba y abajo (es el comportamiento esperado de ese nivel); no se tiene en cuenta la
  rotación sin aplicar del video (`unappliedRotationDegrees`), y no se verificó si algún video llega con ella; el nivel no se guarda entre salas.

---

## 2026-10-11 — Arreglo: con el teclado abierto el video se achica en vez de esconderse (vista vertical), confirmado por la persona

- **Problema:** al tocar el campo del chat en vertical el video desaparecía. No era un bug: `RoomContent` no lo dibujaba con el teclado abierto ("dejaría el chat
  aplastado"). La web hace lo contrario a propósito (`public/style.css`, `html.keyboard-open:not(.device-landscape) .screen-wrap`, rama `plan-produccion`): el
  video puede achicarse a un tope de 38 % del alto visible, con piso de 90 px, porque la prioridad es que se siga viendo grande. Commit `5fdcb40`.
- **Ahora:** sin teclado el video es 16:9 como siempre; con teclado es el 16:9 limitado al 38 % del alto que queda sobre el teclado, con piso de 90 dp y sin
  agrandarse nunca. Queda con bandas negras a los lados. Ya no se desmonta al abrir o cerrar el teclado.
- **Código:** `net/PortraitVideoLogic.kt` (`portraitVideoHeightDp`, lógica pura con 9 tests); `RoomScreen.kt`: `RoomContent` mide el espacio con
  `BoxWithConstraints` y pasa el alto a `RoomVideo`/`RoomVideoBox` (`height(videoHeight)` en lugar de `aspectRatio(16/9)`); el cuerpo anterior pasa a
  `RoomContentBody` sin cambios. Sin cambios en el servidor, `RoomPlayer`, `RoomViewModel` ni dependencias.
- **Verificado por el asistente:** los 9 tests pasan con `kotlinc` 2.0.21 y un arnés propio (no con `gradlew`); `kotlinc` da los mismos tipos de error antes y
  después sobre `RoomScreen.kt` (solo referencias sin resolver por no tener Compose) y ningún error de sintaxis. La UI no se pudo compilar fuera de Android Studio.
- **Confirmado por la persona:** probó en vivo los 5 puntos de la lista y funcionaron todos (video visible y más bajo al abrir el teclado, vuelve al cerrarlo,
  sin cortes al abrir y cerrar varias veces con el video reproduciéndose, controles al tocar el video con el teclado abierto, y paso a pantalla completa al girar
  con el teclado abierto). No dijo si fue en teléfono o emulador ni pidió ajustar el 38 % ni el piso de 90.
- **Nota:** el 38 % se calcula sobre el alto que queda bajo la cabecera de la sala, algo menos que el alto de la web, así que el video puede salir un poco más bajo
  que allá. El pendiente de la 7A "girar el teléfono con `configChanges`" no se marca como cubierto: no se probó por separado.

---

## 2026-10-11 — Fase 7, parte 2A: comentarios flotantes del chat sobre el video en pantalla completa, confirmado por la persona

- **Qué es:** como el "danmaku" de la web (`public/room.html`, rama `plan-produccion`). Cada mensaje de chat que llega en vivo cruza el video de derecha a
  izquierda en pantalla completa. Commit `5c4a99b`.
- **Copiado de la web:** 6 carriles a 10 % de altura + 7,5 % por carril; un carril se reserva 1300 ms y, si todos están ocupados, se usa el que se libera
  antes (pueden encimarse); duración `min(15, max(7, largo/10 + 6))` s; los mensajes de sistema se descartan y los propios se muestran; el historial del chat
  no crea flotantes; nombre en negrita con el color por hash de la web (host cian); letra de 16 px hasta 820 dp de ancho y 22 px en adelante; sin toques; al
  salir de pantalla completa se limpia todo.
- **Decisión nuestra (la web no la tiene):** tope de 12 comentarios a la vez; los de más se descartan.
- **Código:** `net/DanmakuLogic.kt` (lógica pura), `ui/room/RoomDanmaku.kt` (la capa, al final de la pantalla completa para quedar sobre los controles),
  `RoomViewModel.liveChat` (un `SharedFlow` que emite solo los `chat-message` en vivo; sin nadie escuchando se descarta) y una línea en `RoomFullscreen.kt`.
  Sin cambios en el servidor, `RoomPlayer`, `MainActivity` ni PiP; sin dependencias nuevas.
- **Verificado por el asistente:** los 17 tests de `DanmakuLogicTest` y los 25 de `FullscreenLogicTest` pasan compilados con `kotlinc` 2.0.21 y un arnés propio
  (no con `gradlew`). Los valores de color, duración y altura salen de ejecutar las funciones de la web en node; cambiar el multiplicador del hash hace
  fallar el test de colores. La UI no se pudo compilar fuera de Android Studio.
- **Confirmado por la persona:** compiló y probó los 10 puntos de la lista (sin flotantes en vertical, comentarios en pantalla completa con el nombre en
  color y el host en cian, mensajes propios y de la web, mensaje largo, muchos seguidos, que no bloqueen los toques sobre los controles, que se limpien al salir,
  que no afecte la sincronización del video y que los avisos de sistema no salgan) y dijo que **todos funcionaron como debían**. El dispositivo (teléfono o
  emulador) no quedó registrado. Que `gradlew testDebugUnitTest` pasa no se confirmó.
- **Reacciones (parte 2B), sin empezar.** Hallazgos para cuando se haga: la app no recibe ni envía `reaction` (no está en `HANDLED_SERVER_EVENTS`); el servidor
  reenvía el emoji suelto a toda la sala, incluido quien lo envía, sin validar ni limitar; la web tiene 14 emojis y los muestra también fuera de pantalla
  completa. El contador de espectadores sobre el video ya existía desde la 7C.

---

## 2026-10-11 — Fase 7C: sala en horizontal (pantalla completa) y controles encima del video, confirmado por la persona

- **Problema:** en horizontal la caja 16:9 del video superaba la altura de la pantalla y empujaba controles y chat fuera de vista, sin scroll.
- **Pantalla completa** (`cc50f27`): en horizontal o con el icono, solo el video; controles que aparecen al tocar y se esconden a los 3 s, contador de conectados
  5 s; la X y Atrás vuelven a vertical (retienen la vertical hasta que el teléfono queda derecho); barras del sistema ocultas; volumen solo para invitados;
  avisos de conexión y silencio. Lógica pura en `net/FullscreenLogic.kt` con 25 tests (`FullscreenLogicTest`). Archivos nuevos: `ui/room/RoomFullscreen.kt` y
  seis iconos (`ic_close`, `ic_fullscreen`, `ic_group`, `ic_pause`, `ic_play`, `ic_volume_up`).
- **Controles sobre el video en vertical** (`05b571f`): play/pausa (host), barra de progreso o de salto e icono de pantalla completa encima del video; debajo,
  el nombre de la cinta y "Cambiar video". Una sola `PlayerControlsBar` y un solo `OverlayController` para las dos vistas.
- **Arreglo de compilación** (`2c5dd21`): `05b571f` no compilaba (`RoomScreen.kt:423` "`ColumnScope.AnimatedVisibility` cannot be called in this context with an
  implicit receiver" y, en cascada, `:429`). Interpretación, no confirmada con el compilador: dentro del `Column`, el `Box` resolvía `AnimatedVisibility` contra el
  `ColumnScope` de afuera. El `Box` pasó a `RoomVideoBox`, sin cambios de lógica.
- **Confirmado** por la persona: funcionó tras compilar con `2c5dd21`, sin detalle punto por punto (el dispositivo no quedó registrado). Sobre la pantalla
  completa, el mensaje de la sesión anterior que pegó dice que "los 12 puntos funcionaron".
- **Sin confirmar uno por uno:** host e invitado en vertical, arrastrar la barra sin esconder los controles, sin video o con error, el volumen del
  invitado en pantalla completa, PiP desde pantalla completa y `gradlew testDebugUnitTest`.
- **Decisiones abiertas** (no se pidió cambiarlas): los controles se esconden también con el video en pausa; en vertical no hay botón de volumen para invitados.
- Sin cambios en el servidor, `RoomPlayer`, `RoomViewModel`, `MainActivity` ni PiP; sin dependencias nuevas.

---

## 2026-10-10 — Fase 7A: la X de la ventana flotante ya pausa (`configChanges`), confirmado en el emulador

- **Defecto:** sin `configChanges`, cerrar la ventana flotante con la X recreaba la actividad y Android no mandaba el cambio de modo de PiP; el video
  (y la sala, si eras host) seguía sonando con la actividad invisible.
- **Arreglo** (commit `fix: la X de la ventana flotante (PiP) debe pausar el video (configChanges)`): `android:configChanges="screenSize|smallestScreenSize|
  screenLayout|orientation"` en `MainActivity`. Entrar y salir de PiP y girar el teléfono ya no recrean la actividad; tema oscuro, idioma y tamaño de
  letra sí. Sin cambios en la lógica de pausa, sin tests nuevos.
- **Confirmado** por la persona ("se pausó bien", dos cierres: host e invitado). Log: `ON_STOP pip=true -> no pauso`, y medio segundo después
  `MODO_PIP=false lifecycle=CREATED -> PAUSO`; como host `emito pause`, como invitado no emite nada. El log también respalda entrar en PiP sin pausar, expandir
  la ventana sin pausar y la pantalla apagada en PiP (pausa y el host emite `pause`).
- **Límite conocido (decidido por la persona):** el invitado que cierra la ventana pausa, pero el siguiente `heartbeat` de un host que sigue reproduciendo
  lo vuelve a poner en play hasta que cae el socket (~5 s). No es nuevo de PiP (pasa igual con la pantalla apagada). No se arregló.
- **Quitado:** los logs temporales `[DEBUG-X]` (patch de código aparte, revierte `5baa7c6`) y el comentario del manifiesto sobre la "hipótesis". Se mantienen
  `MovieNightPip` y `MovieNightSync`.
- **Sin probar:** girar el teléfono y el tema oscuro con `configChanges`, la pantalla principal y el login sin ventana, antes de Android 12, PiP desactivado
  en Ajustes, "Subir uno nuevo" con el video sonando, ser expulsado con la ventana abierta. Sin dependencias nuevas y sin tocar el server.

---

## 2026-10-10 — Fase 7A probada en emulador (mini-reproductor flotante, con un punto por verificar)

- **Funciona** (según la persona, "todas las pruebas funcionaron como debían, sin parpadeos", en el emulador SDK 36): salir de la sala a otra
  app deja el video en una ventana flotante sin pausar a los demás; tocar la ventana vuelve a la sala; sin ventana desde la pantalla principal.
- **Lo que respalda el log** (`MovieNightPip` y `MovieNightSync`): entrar en PiP no pausa y el host sigue emitiendo `heartbeat` cada 4 s sin
  huecos; un invitado en PiP recibe `heartbeat`, `pause` y `play`; dos `ON_STOP` en PiP con `cambioConfig=false` al volver a pantalla completa no
  pausaron (con la condición anterior habrían pausado); con la pantalla apagada se pausa y el host emite `pause`; desde la pantalla principal no
  hay ventana. Entrar en PiP recrea la actividad y no hizo falta `configChanges`.
- **Sin verificar:** el **cierre con la X** (el log no tiene ninguna pausa por esa vía ni líneas `MODO_PIP=`, así que no se ve que pare), qué pasó a
  las 18:27:44 (`inRoom=false` con la ventana abierta), el camino de antes de Android 12, PiP desactivado en Ajustes, "Subir uno nuevo" con el video
  reproduciéndose y ser expulsado con la ventana abierta. No se tildó el cierre con la X en el plan.
- **Decisión de la persona:** en PiP no se pausa nada (host ni invitado); se pausa al cerrar la ventana o al dejar de verse la app del todo.
- **Código** (commit `feat: Fase 7A — mini-reproductor flotante…`): `net/PictureInPictureLogic.kt`, `ui/room/PictureInPicture.kt`, `MainActivity`
  (`supportsPictureInPicture`, `updatePip`, `onUserLeaveHint`), `RoomScreen` (`PauseOnStopTracker` en lugar de la condición vieja de pausa),
  `RoomPlayer` (tamaño del video para el aspecto). Sin dependencias nuevas y sin tocar el server. Sin botones en la ventana.
- **Verificado por el asistente:** los 21 tests de `PictureInPictureLogicTest` pasan (`kotlinc` 2.0.21, arnés propio). `gradlew testDebugUnitTest`
  no se confirmó en esta fase.
- **Pendientes:** repetir la prueba de la X con el log; los logs de depuración `MovieNightPip` y `MovieNightSync` siguen en el código; con la
  pantalla apagada el socket cae (`transport error`, igual que en la 6C), lo que tocaría resolver en la 7B si se hace.

---

## 2026-10-10 — Fase 6C probada en emulador (notificaciones: aviso de subida y servicio en primer plano)

- **Funciona** (según la persona, "todas las pruebas funcionaron como debían", en el emulador contra el server real): con la
  app en segundo plano la subida sigue con una notificación de avance; al terminar llega el aviso; salir de la app justo después
  de elegir el video; pantalla apagada; un archivo inválido no muestra notificación de avance; con el wifi cortado sale el aviso de
  fallo y la notificación de avance desaparece; rotar el teléfono durante la subida.
- **Sin confirmar uno por uno** (checklist de la A): el cuadro de permiso de Android 13+ y su texto, "Ahora no", el permiso negado,
  "Subir y crear sala" y "Cambiar video" desde una sala con su aviso. Tampoco se probó con un video de varios GB ni en un teléfono real.
- **Evaluación sin tocar el server:** `server.js` y `lib/` no tienen push (ni tokens de dispositivo ni envío) ni un aviso previo a la
  expiración de salas (`ROOM_TTL_HOURS`, barrido cada 30 min; el `room-error` sale después de cerrar y solo a los conectados). El
  push real quedó **sin hacer**: exige cambiar el server (tokens, endpoint, envío, programador) y sumar Firebase a la app.
- **Código, A** (commit `feat: Fase 6C — aviso local…`): `net/UploadNotificationLogic.kt`, `notify/UploadNotifier.kt`,
  `notify/NotificationPrefs.kt`, `ui/upload/NotificationPermissionGate.kt`, cuenta de pantallas visibles en `MovieNightApp`,
  `UploadFlow.onFinished`. El permiso `POST_NOTIFICATIONS` se pide una sola vez, antes de abrir el selector de video.
- **Hallazgo y código, B** (commit `feat: Fase 6C (B) — servicio en primer plano…`): con A sola, la subida en segundo plano fallaba
  siempre con "Se cortó la conexión durante la subida" aunque el proceso seguía vivo. Se agregó un servicio en primer plano
  (`dataSync`) que mantiene vivo el proceso y muestra el avance, sin subir nada él mismo: `net/UploadServiceLogic.kt`,
  `notify/UploadKeepAlive.kt`, `notify/UploadService.kt`, `UploadFlow.onStateChanged`, permisos `FOREGROUND_SERVICE` y
  `FOREGROUND_SERVICE_DATA_SYNC`, canal silencioso `upload_progress`. La causa técnica exacta del corte no se registró (el detalle
  del error de red se descarta).
- **Verificado antes de probar:** 11 tests de `UploadNotificationLogicTest` y 11 de `UploadServiceLogicTest` pasan con el arnés del
  asistente (`kotlinc` 2.0.21); el resto del código nuevo pasó el parser sin errores de sintaxis. `gradlew testDebugUnitTest` no se
  confirmó en esta fase; la app compiló y corrió en el emulador. Sin dependencias nuevas y sin tocar el server.
- **Límites y pendientes:** cerrar la app desde recientes (o salir de la sala mientras se cambia el video) cancela la subida;
  Android 15 limita `dataSync` a unas 6 h por día; sin botón de cancelar en la notificación de avance; el error técnico de red sigue
  sin registrarse. Se actualizaron las pistas de subida (ya no piden dejar la app abierta) y las referencias viejas en `MEMORIA.md`
  y en la Fase 7 del plan.

---

## 2026-10-09 — Fase 6B probada en emulador (acceso al panel de administración desde la app)

- **Funciona** (según la persona, "todos los puntos funcionaron perfectamente", en el emulador contra el server real en
  Docker, con una cuenta promovida con `scripts/make-admin.js`): la cuenta admin ve la tarjeta "Administración" y llega al
  panel (el navegador pide iniciar sesión aparte y después se abre `/admin.html`); una cuenta normal no ve nada; al
  quitarle el rol y volver a la pantalla principal desde una sala, la tarjeta desaparece; cerrar sesión en el panel del
  navegador no cierra la sesión de la app.
- **Sin probar:** el aviso "No se encontró un navegador para abrir el panel." (teléfono sin navegador).
- **Alcance elegido** (la persona eligió la opción a entre: a) abrir el panel web en el navegador, b) pantallas nativas,
  c) agregar el rol a `/auth/me` en el server): solo el botón. No se tocó el server ni se agregaron dependencias.
- **Código** (commit `feat: Fase 6B`): `net/AdminAccessLogic.kt` (lógica pura: `AdminAccess`, `adminAccessFrom`,
  `mergeAdminAccess`, `shouldShowAdminAccess`, `adminPanelUrl`), `ui/home/AdminPanelLauncher.kt` (`openInBrowser` con
  `ACTION_VIEW`), `HomeViewModel.refreshAdminAccess()` y la tarjeta `AdminCard` en `HomeScreen`.
- **Verificado contra el server** (`server.js` y `lib/`, rama `plan-produccion`): `GET /auth/me` solo devuelve `loggedIn`, `id`
  y `email`, sin rol; `requireAdmin` (`lib/adminAuth.js`) lee `users.role` en cada request; `admin.html` es estático y
  comprueba el acceso con `/admin/settings`; el panel no tiene login propio (misma cookie `movienight.sid`) y su pantalla de
  inicio de sesión es la de `/`, que no devuelve al panel. Ejecutando el `requireAdmin` real con una base simulada: admin pasa,
  cuenta normal 403, sin sesión 401, sin Postgres 404, base caída 500. La app sondea `/admin/stats` (de solo lectura): 200 con
  `activeRooms` = admin; 401, 403 y 404 = no admin; red, 429 y 5xx = no se sabe y no cambia lo ya sabido.
- **Cookie:** la de la app vive en `PersistentCookieJar` y el `ACTION_VIEW` solo pasa la URL, así que no se filtra al navegador ni
  al revés. El navegador abre una sesión propia; lo único compartido es el límite de intentos de login (IP + email).
- **Verificado antes de probar:** 11 tests nuevos de `AdminAccessLogicTest` pasan con el arnés del asistente (`kotlinc` 2.0.21
  y `org.json` real); el resto del código nuevo (UI y ViewModel) pasó el parser sin errores de sintaxis. `gradlew
  testDebugUnitTest` no se confirmó en esta fase; la app compiló y corrió en el emulador.
- **Pendientes de esta fase:** `/auth/me` podría devolver el rol y evitar el sondeo (requiere tocar el server; no se hizo).
  El rol se vuelve a consultar solo al entrar a la pantalla principal, no al volver del navegador. El navegador no devuelve al
  panel tras iniciar sesión (comportamiento de la web).

---

## 2026-10-09 — Fase 6A probada en emulador (aviso de server-restarting y reconexión coherente)

- **Funciona** (según la persona, en el emulador contra el server real en Docker, con la web como la otra punta;
  reinicio con ⋮ → Restart sobre `app-1` en Docker Desktop): el aviso aparece como debe y la app reconecta sola; con la
  app como host el video **no retrocede ni se adelanta**; el invitado (la web) se vuelve a alinear con el host; **host y
  silencio** (ver abajo); **sala perdida**; **server caído más de 90 s** y **subida en curso**. En las pruebas 6 y 7 la
  persona confirmó "pasaron" sin más detalle.
- **Sin probar:** la recarga automática del video si había quedado en error durante la caída (pasa con videos servidos
  desde disco). Está escrita y compila, pero nunca se vio funcionar. No se tilda como probada.
- **Código** (patch `fase-6A`): `net/ServerRestartLogic.kt` (lógica pura: `RestartState`, `connectionBanner`,
  `roomErrorAfterRestart`), `planRejoin` con `afterRestart`, y cambios en `RoomPlayer`, `RoomViewModel` y `RoomScreen`.
  Lo que cambió respecto de lo que había desde la Sesión B:
  - El aviso ya no lo pisan los `connect_error` que salen mientras el server está caído ni un aviso de límite de mensajes.
    Dice "se va a reiniciar" mientras sigue la conexión y "reconectando en cuanto vuelva" cuando se corta. A los 90 s sin
    volver se deja de mostrar y queda el genérico "Reconectando…".
  - **Tras un reinicio nadie salta de posición al volver.** Redis guarda la posición con hasta ~8 s de atraso (el server
    solo persiste el heartbeat cada >5 s), y antes `planRejoin` hacía retroceder al host a esa posición vieja y arrastraba
    a todos. Ahora el host conserva la suya y el heartbeat alinea al resto en unos 4 s; el invitado toma solo la pausa de
    la sala.
  - Si el video ya cargado había quedado en error durante la caída, se vuelve a preparar al reconectar (ExoPlayer no
    reintenta solo). **Sin probar.**
  - Un `room-error` al volver de un reinicio agrega el contexto ("La sala no existe. El servidor se estaba reiniciando y
    puede que la sala no se haya recuperado.").
- **Verificado contra el server** (`server.js`, rama `plan-produccion`, y con un server real levantado con SIGTERM):
  `server-restarting` se emite con `io.emit` a todos los sockets **sin payload**; el corte llega `SHUTDOWN_GRACE_MS`
  después (5 s por defecto; PM2 tiene `kill_timeout: 8000`) con razón `transport close`, o sea que el cliente reconecta
  solo; mientras el server está caído salen `connect_error` seguidos. Con el Dockerfile (`CMD ["node", "server.js"]`)
  Node recibe el SIGTERM de Docker directamente, y Stop y Restart emiten el aviso por igual.
- **Subidas:** no cambió nada. El PUT va directo a R2; solo `presign` y el paso final contra el server pasan por el
  server caído, y ambos ya eran reintentables.
- **Pendientes que salieron de las pruebas, fuera de la app** (no se tocó el server ni la web):
  - **El navegador (no host) retrocede 5-6 s** justo cuando termina el reinicio y después el heartbeat del host lo corrige
    solo. La app no lo causa. Hipótesis **sin confirmar**: `room.html` solo aplica la posición de `room-data` si
    `player.src.indexOf(videoFile) === -1`; si el nombre del archivo tiene espacios, tildes o símbolos, `player.src`
    queda codificado, no coincide, y la web reasigna el video y aplica la posición vieja.
  - **Tras un reinicio el dueño recupera el host aunque se lo hubiera pasado a otro.** Probado: se le pasó el host a la web,
    se reinició el server y volvió el host a la app (la dueña). `join-room` concede el host a cualquier socket que pruebe
    ser el dueño (`isRoomOwner` → `setHost`) sin mirar quién lo tenía, y el host solo vive en memoria. Del código se
    deduce que pasa también con una reconexión normal del dueño; eso no se probó. La sala queda coherente (un solo host).
- **Verificado antes de probar:** 240 tests de lógica pura pasan con el arnés del asistente (21 nuevos: 16 en
  `ServerRestartLogicTest` y 5 en `SyncLogicTest`; sin `UploadFollowUpTest`, que depende de Android) y `gradlew
  testDebugUnitTest` terminó en BUILD SUCCESSFUL en la máquina de la persona (compila también la UI).

---

## 2026-10-09 — Fase 5 probada en emulador (moderación del host: hacer host, silenciar y expulsar)

- **Funciona** (según la persona, "todo funcionó", y detalló cuatro puntos, en el emulador contra el servidor real
  con la web como la otra punta): el menú **Acciones** de la lista "En la sala" en pantallas chicas; **hacer host**
  (el menú desaparece en la app y aparece en la web); la **web como host contra la app** (expulsar, silenciar y quitar
  silencio, con la app reaccionando bien); y **reconectar siendo silenciado** (el chat vuelve a bloquearse). No se
  detalló caso por caso más allá de eso. Con esto se cierran los dos pendientes de la Sesión B: "te sacó de la sala"
  y el chat bloqueado al silenciar.
- **Código** (commit `0c3e489`): `net/ModerationLogic.kt` (lógica pura), `ui/room/ViewersDialog.kt` (lista con el menú
  Acciones solo para el host), `RoomSocket.sendModeration` / `socketId`, y `RoomViewModel.moderate()` con el seguimiento
  de pedidos. Solo se emite con el rol de host confirmado por `host-status` y con conexión, nunca sobre uno mismo, y
  expulsar / hacer host piden confirmar. Como el server no contesta, la app espera 5 s a ver el cambio en `viewer-list`
  y, si no llega, lo avisa; mientras hay un pedido en curso no acepta otro sobre la misma persona (`toggle-mute` alterna).
- **Lo que hay que saber del servidor** (verificado en `server.js`, rama `plan-produccion`): los tres eventos los autoriza
  solo `socket.isHost` (no el dueño de la sala, a diferencia de `change-video`); el payload es el `id` de socket en texto
  plano; no contestan nada; solo `make-host` rechaza hacerse host a uno mismo y solo deja un mensaje de sistema en el chat;
  ninguno protege al dueño; expulsar no veta.
- **Bugs del lado receptor, corregidos:** `kicked` mostraba "No se pudo entrar a la sala" y ahora tiene su pantalla ("Te
  sacaron de la sala") y deja limpio el rol, la lista y las subidas. Y el silencio local no se borraba al reconectar: el
  server lo limpia a los 15 s y al volver nunca manda `muted:false`, así que la app podía dejar el chat bloqueado sin motivo.
- **Verificado antes de probar:** 227 tests de lógica pura pasan (30 nuevos en `ModerationLogicTest`) y el resto de los
  archivos pasó el compilador solo como chequeo de sintaxis. La UI no se compila fuera de Android Studio, así que se probó
  en el emulador.
- **Sin probar**: el botón deshabilitado mientras hay un pedido en curso y el aviso a los 5 s sin confirmación; silenciar a
  alguien y que vuelva tras más de 15 s sin conexión; ser expulsado mientras se subía un video como ex-host; y una sala sin
  dueño con la app como host (el mismo pendiente de la 4B).
- **Docs**: el plan tilda la Fase 5; `MEMORIA.md` incorpora la Fase 5, los archivos nuevos al árbol y los datos del server.
- **Siguiente**: Fase 6 (extras, no bloqueante).

---

## 2026-10-08 — Fase 4B probada en emulador (crear sala con lo subido y cambiar el video de la sala)

- **Funciona** (según la persona, "todo funcionó" con el checklist de la sesión, en el emulador contra el
  servidor real con R2): "Subir y crear sala" con y sin contraseña (sube, crea la sala y entra sola); como
  host dentro de la sala, **Cambiar video** eligiendo uno de la biblioteca (con confirmación) y subiendo uno
  nuevo, con la web viendo el cambio; el caso de un host que no es el dueño de la sala; cancelar durante la
  subida y ocultar el cuadro (queda el aviso con el avance); modo avión en el último paso con **Reintentar**
  sin volver a subir; y un repaso de la 4A, porque su código se movió. No se detalló caso por caso.
- **Código**: la subida de la 4A pasó de `HomeViewModel` a `net/UploadFlow.kt`, compartida con la sala y con un
  paso siguiente opcional (crear sala / cambiar el video). Lógica pura nueva en `net/RoomVideoLogic.kt`
  (cuerpos y respuestas de `create-room-from-upload` y `change-video-from-upload`) y estados nuevos en
  `UploadLogic.kt` (`Finishing`, `UploadGoal`, `alreadyUploaded`). UI: `ui/upload/UploadStatus.kt` (cuerpo
  compartido y pantalla encendida), `ui/room/ChangeVideoDialog.kt` y el botón en `RoomScreen.kt`.
  `host-status` ahora trae el `hostToken` (solo en memoria).
- **Lo que hay que saber del servidor**: `change-video-from-upload` autoriza por el **dueño** de la sala
  (`isRoomOwner`), no por quién es host. En una sala con dueño solo vale la sesión de esa cuenta y el
  `hostToken` se ignora: un host por traspaso que no la creó recibe 403, y la app lo explica. En una sala sin
  dueño (anónima) vale el `hostToken`. El servidor valida el contenido del video recién en estas rutas y
  borra de la biblioteca el que no pasa (ante un 400 la app recarga la lista).
- **Verificado antes de probar**: 197 tests de lógica pura pasan (24 nuevos), y `UploadFlow` se ejecutó con
  un uploader y una API falsos (reintentos, cancelar, avances tardíos). La UI no se compila fuera de Android
  Studio, así que se probó en el emulador.
- **Sin probar**: una sala sin dueño (anónima de la web) donde el host sea la app, que usa el `hostToken`
  (no estaba en el checklist); archivos grandes (cientos de MB o GB); salir de la sala mientras sube (la
  subida se corta, límite aceptado); y el proceso muerto en segundo plano.
- **Docs**: el plan tilda la Fase 4; `MEMORIA.md` incorpora la 4B y los archivos nuevos al árbol.
- **Siguiente**: Fase 5 (rol de host y moderación: `make-host`, `kick-user`, `toggle-mute`).

---

## 2026-10-08 — Fase 4A probada en emulador (subir un video a la biblioteca)

- **Funciona** (checklist completo en el emulador Medium Phone API 36.1, contra el servidor real con R2):
  elegir un video con el selector del sistema, barra de progreso, aparece en la biblioteca de la app y de la
  web, se puede crear una sala con él y reproducirlo; cancelar corta la subida sin dejar un video a medias;
  con modo avión muestra el error y **Reintentar** vuelve a subir desde cero; nombre con tilde y espacio;
  pantalla encendida durante la subida.
- **Cómo se probó**: sin videos en el emulador, se generaron dos con ffmpeg (un patrón de prueba de 90 s y
  ~47 MB, y uno de 15 s y 1,7 MB con tilde en el nombre) y se copiaron a Descargas del emulador.
- **Sin probar**: un archivo real de cientos de MB o GB, el límite de 5 GiB con un archivo de ese tamaño (solo
  tests unitarios) y el proceso muerto en segundo plano (límite aceptado: la subida se pierde).
- **Docs**: `MEMORIA.md` incorpora la 4A, `UploadLogic.kt` y `VideoUploader.kt` al árbol de archivos y deja de
  decir que la biblioteca es solo lectura; el plan divide la Fase 4 en 4A / 4B y deja el fallback multipart
  fuera de alcance mientras R2 funcione.
- **Siguiente**: Fase 4B (crear sala y cambiar el video de una sala con lo subido).

---

## 2026-10-08 — Docs: se archiva el detalle viejo de MEMORIA.md

- Las entradas de "Por dónde seguir" de la Fase 1, la Sesión B y las Fases 3A y 3B pasaron, **sin
  cambios de texto**, a `docs/historico/MEMORIA-fases-1-a-3B.md` (con un índice en
  `docs/historico/README.md`).
- `docs/MEMORIA.md` queda con la 3C como último estado, un resumen de una línea por fase, los datos de las
  fases viejas que siguen vigentes y la lista de pendientes que se arrastran. Pasó de ~21,7 KB a ~18,5 KB.
- No cambia código ni decisiones.

---

## 2026-10-08 — Fase 3C probada en emulador (la app como host)

- **Funciona** (emulador, app como host y la web como invitada; según la persona, todas las pruebas de la
  lista de la sesión): play, pausa y seek de la app los sigue la web (un solo `seek` al soltar la barra);
  heartbeat cada 4 s; cambio de host; corte de red; pausa al pasar a segundo plano; `subtitle-changed`; la
  app como invitada sigue igual que en la 3B. El log de Logcat de la sesión lo respalda: heartbeats cada 4,0 s
  con la posición avanzando ~4000 ms por latido, una sola línea `emito` por cada acción de la persona y
  ninguna que no hubiera provocado.
- **Nuevo**: la app emite `sync` (play, pause, seek, heartbeat) solo si `host-status` confirma el rol de host y
  el socket está conectado (`canEmitSync`); el rol se da por perdido al desconectarse. Barra de salto para el
  host (un `seek` al soltar). `subtitle-changed` y `room-data.subtitleFile` (WebVTT como `SubtitleConfiguration`;
  cambiarlo con video cargado lo vuelve a preparar en la posición actual). `buffering-status` de todos los roles
  (`shouldReportBuffering`: quiere reproducir y se quedó sin datos; solo cambios; se reenvía al reconectar).
  Lógica pura en `net/SyncLogic.kt` (`toSyncPayload`, `canEmitSync`, `seekTargetMs`, `shouldReportBuffering`,
  `planRejoin`) y `net/VideoUrl.kt` (`resolveSubtitleUrl`), con 19 tests nuevos (143 en total; en Gradle, 144
  con `ExampleUnitTest`). Forma de los eventos verificada en `server.js` antes de escribir código.
- **Bug encontrado en la prueba y corregido**: al volver a la sala con el mismo video ya cargado (el host había
  pasado a la web y la sala avanzó), la app como host ignoraba la posición de `room-data` y su heartbeat
  devolvía a la sala a la posición vieja con la que se había ido. Ahora se alinea con la sala (`alignToRoom` /
  `planRejoin`), de host o de invitado.
- **Corrección de docs anteriores**: la 3B y `MEMORIA.md` decían que Logcat con `MovieNightSync` mostraba cada
  `sync` recibido; ese log nunca estuvo en el código. Desde la 3C hay log de lo que se emite, del buffering y
  de la alineación al reconectar; **los `sync` recibidos siguen sin log.**
- **Buffering con R2**: solo hubo buffering tras un salto a una zona sin descargar (~3,5 s); en más de 5 minutos
  de reproducción continua, ninguno espontáneo. Sin señal de un bug de la app. Para distinguir red lenta de un
  fallo, el log trae `bufferedAhead` (ojo: tras un salto es 0 por fuerza, no prueba nada).
- **Queda así a propósito**: al reconectar, la posición de la sala puede tener hasta ~4 s de antigüedad (último
  heartbeat guardado), así que la app puede quedar un poco atrás; cambiar el subtítulo con video cargado
  produce un instante de carga.
- **Plan**: tildados "Emitir `sync` solo si es host", "Manejar `video-changed` y `subtitle-changed`" y
  "Reportar `buffering-status`". **La Fase 3 queda completa.**
- **Siguiente**: Fase 4 (subida de video).

---

## 2026-10-03 — Fase 3B probada en emulador (la app sigue a la sala)

- **Funciona** (emulador Medium Phone API 36.1, app como invitada y la web como host): play, pausa y seek
  desde la web se reflejan en la app; entrando con el video en marcha arranca cerca del minuto del host; la
  barra de progreso del invitado es de solo lectura (sin play/pause ni seek). La app todavía no emite nada.
- **Nuevo** (`net/SyncLogic.kt`, JVM puro, con tests): parser de `sync` y de `room-data.position`, `planSync`
  (corrección de desfase), `HostReference` / `planReadyResync`. Forma de los eventos verificada en `server.js`:
  el server retransmite `sync` tal cual y sin validar, así que el parser es estricto.
- **Desfase — se ajustó tres veces** a partir de la prueba: el primer diseño copiaba los umbrales de la web
  (salto a 4 s, solo velocidad entre 0.5 y 4 s) y un desfase de 2-3 s tardaba 30-40 s en cerrarse.
  (1) Salto desde 1.5 s, sin corregir con el reproductor cargando y con 8 s sin nuevos saltos tras uno.
  (2) Al quedar lista tras un salto o la carga, una corrección única a donde se estima que está el host ahora;
  el umbral de salto baja a 1 s. (3) La pausa deja el video exacto (150 ms) y se agrega log de diagnóstico
  (`MovieNightSync` en Logcat).
- **Queda así a propósito**: un desfase residual de **1 a 2 s** tras un seek del host o al entrar con el
  video en marcha, que se corrige solo en unos segundos. Al entrar, la `position` de `room-data` puede tener
  hasta 4 s de antigüedad y no hay evento para pedir la actual.
- **Sin probar**: la app como host (solo controla su copia local, con un aviso; el control real es la 3C),
  red lenta con muchos saltos, y el último ajuste (pausa exacta a 150 ms): tras el anterior la pausa se veía
  "un poco retrasada" y no se volvió a confirmar.
- **Evitar bucles**: ningún listener del ExoPlayer emite; lo que viene del server entra solo por
  `RoomPlayer.applySync`. Hay que volver a probarlo en la 3C, cuando la app empiece a emitir.
- **Plan**: tildados "Escuchar `sync`" y "UI de controles propia", con la nota del desfase residual.
  Siguen sin tildar "Emitir `sync` solo si es host", `subtitle-changed` y `buffering-status` (3C).
- **Siguiente**: Fase 3C (ser host).

---

## 2026-10-02 — Fase 3A probada en emulador (el video de la sala carga)

- **Funciona** (checklist completo en el emulador Medium Phone API 36.1): el video de la sala se ve y
  suena, arrancando en pausa y en el segundo 0; play/pause local; al terminar, Reproducir vuelve a
  empezar; funciona en salas creadas desde la app y desde la web; se pausa al pasar a segundo plano;
  sigue por donde iba al girar el teléfono; `video-changed` lo recarga en pausa y desde 0; el corte de
  red no lo recarga de cero; al salir de la sala el sonido se corta; si falla, muestra el error con
  reintento.
- **Arreglo de versión**: la 3A fallaba al compilar con "Internal compiler error" en `MainActivity.kt`
  (`FirIncompatibleClassExpressionChecker`, "source must not be null"). Causa: Media3 `1.11.x` se
  compila con Kotlin 2.2 y el proyecto usa Kotlin 2.0.21. Se fijó Media3 en `1.10.1` (Kotlin 2.0.20).
  Subir Media3 requiere subir Kotlin (y el plugin de Compose) antes.
- **Entorno**: se actualizó Android Studio (instalador nuevo). Pide elegir "Use JVM 21" para Gradle 8.13
  (Java 25 no es compatible). Se dejaron de versionar `.idea/` y `.kotlin/` (ahora van al `.gitignore`).
- **Plan**: tildado "Integrar Media3/ExoPlayer" y dividida la Fase 3 en 3A / 3B / 3C.
  `video-changed` hecho; `subtitle-changed` queda para la 3C.
- **Siguiente**: Fase 3B (seguir a la sala: escuchar `sync`, controles propios, seek bloqueado a invitados).

---

## 2026-10-02 — Fase 2, Sesión B probada en emulador

- **Primer build real**: la Sesión B (escrita sin Android SDK) sincronizó y compiló en Android
  Studio, y corrió en el emulador Medium Phone API 36.1 contra el servidor real. La dependencia
  `org.json:json:20240303` de tests resolvió bien.
- **Funciona**: biblioteca con los videos reales, crear sala (con y sin contraseña, la app queda
  como host), unirse a una sala de la web (con y sin contraseña, incluida una incorrecta), chat en
  ambos sentidos con la web, salir de la sala y reconexión al cortar y devolver la red.
- **Sin prueba explícita**: "te sacó de la sala", chat bloqueado al silenciar y aviso de
  `server-restarting` (hace falta un host que los dispare o reiniciar el server).
- **Plan**: tildados los tres ítems de la Fase 2 que cubría la Sesión B (listar biblioteca,
  crear sala, detalle de sala). Los adelantos de las Fases 5 y 6 quedan sin tildar, con la nota de
  qué se probó y qué no.
- **Sigue pendiente** (no bloquea): recuperar contraseña de punta a punta (emails del server).
- **Siguiente**: Fase 3, reproductor Media3 sincronizado.

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
