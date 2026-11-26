Slowdown para Android
=====================

Slowdown toca audio más lento o más rápido sin cambiar su altura. Está hecho
para la música: para practicar una pieza y aprenderla de oído, oyendo un
pasaje una y otra vez a una velocidad que se pueda seguir. Es la versión para
Android de [py-slowdown](https://github.com/nivaca/py-slowdown), con el mismo
motor y los mismos archivos `.slx`.

*Read in English: [README.md](README.md).*

Instalación
-----------

Necesita Android 12 o posterior, en un teléfono ARM de 64 bits. El APK
firmado de cada versión está en las versiones publicadas, en
[GitHub](https://github.com/nivaca/slowdown-android/releases) y
[Codeberg](https://codeberg.org/nivaca/slowdown-android/releases).

[![Obtenerla en Obtainium](docs/img/badge_obtainium.png)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.nivaca.slowdown%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fnivaca%2Fslowdown-android%22%2C%22author%22%3A%22nivaca%22%2C%22name%22%3A%22Slowdown%22%7D)

Con [Obtainium](https://github.com/ImranR98/Obtainium) el teléfono instala la
versión más nueva y luego cada nueva sobre la anterior. La insignia añade la
aplicación a Obtainium con un toque, si Obtainium está instalado (el enlace
pasa por la página de redirección de Obtainium, que tiene analítica); sin
ella, añada la aplicación en Obtainium a mano, con la dirección
`https://github.com/nivaca/slowdown-android`.

También está en mi propio repositorio de F-Droid, con los mismos APK
firmados, así que cada uno actualiza al otro: en F-Droid (o Droid-ify, Neo
Store), añada el repositorio `https://nivaca.codeberg.page/fdroid/repo`, cuya
clave tiene la huella `E930B855E234BD2DE4F083D454DA0195549005D91A89309F9A3E5510D71E2E46`; la página <https://nivaca.codeberg.page/fdroid/>
lo añade con un toque.

Qué hace
--------

- Abre archivos de audio y el sonido de los de video: MP3, M4A/AAC, MP4,
  FLAC, WAV, AIFF, Ogg, Opus y lo demás que Android decodifique. MP3, FLAC,
  WAV y AIFF los decodifica la aplicación misma, una canción de cinco minutos
  en medio segundo; lo demás, los códecs de Android, más lentos (unas diez
  veces el tiempo real), de modo que su onda se completa en unos segundos
  mientras ya suena. Los archivos de más de dos canales se mezclan en
  estéreo.
- La ventana está ordenada como la de py-slowdown, y dibujada con sus colores
  (claros u oscuros, según el teléfono): la onda, más oscura donde ya sonó,
  con la posición en rojo y los marcadores en naranja; debajo, los botones de
  la reproducción y el tiempo; las perillas de la velocidad, el volumen y la
  altura, y el panel de los marcadores, los bucles y el sonido, con sus tres
  pestañas. Los bucles guardados se dibujan al pie de la onda, cada uno una
  banda de su color con su nombre (un toque en uno lo repite); de cerca, una
  franja arriba muestra el archivo entero, con la parte que se ve sombreada
  (un toque o un arrastre allí muestra esa parte). Un toque en la onda lleva
  la posición allí; arrastrar desplaza la vista; pellizcar acerca o aleja
  alrededor de los dedos, y menú > Acercar a la selección (Z) muestra solo
  la selección. Una perilla se gira arrastrando hacia arriba o a la derecha
  (hacia abajo o a la izquierda, al revés), vuelve a su valor de siempre con
  un doble toque y, si se mantiene el dedo quieto sobre ella, ofrece sus
  valores de costumbre (las velocidades elegidas, los intervalos de 1, 2, 3,
  5, 7 y 12 semitonos…). La perilla de la velocidad, arrastrada, se detiene
  en las velocidades elegidas.
- Sin un archivo, la onda muestra las grabaciones abiertas hace poco, cada
  una con una figura pequeña de su onda; un toque abre una.
- El reloj cambia con un toque: el tiempo tocado, el que falta y, con un
  primer pulso, el compás y el pulso.
- Tocar, pausar, detener, ir al principio o al final, atrás y adelante según una
  longitud del salto que se elige en el menú (de 1 a 60 segundos; mantenga
  apretado el botón para seguir).
- Velocidad de 0,25× a 3×, que se cambia mientras suena, sin cambiar la
  altura. El estiramiento lo hace la [biblioteca Rubber Band](https://breakfastquay.com/rubberband/),
  con su motor R3 («finer»). A 1× el archivo suena tal cual, muestra por
  muestra. El botón Velocidades tiene las de py-slowdown (50 %, 75 %,
  100 %…). En el menú se puede elegir una calidad ligera (la mitad del
  procesador) o el motor anterior, R2 (ataques nítidos). Una velocidad
  cambiada con el teclado o con un menú, o una altura cambiada con el
  teclado, se muestra en grande sobre la onda por un momento.
- Sube o baja la altura en semitonos (de −12 a +12) y cents (de −50 a +50),
  también mientras suena; con «Conservar los formantes», una voz o un
  instrumento conserva su timbre.
- Repite una selección sin fin y sin hueco, o el archivo entero si no hay:
  mantenga un dedo sobre la onda y arrástrelo, o ponga su principio y su
  final con A y B en la posición, o tome el archivo entero (menú >
  Seleccionar el archivo entero, Ctrl+A). Las selecciones se pueden guardar
  como bucles con un nombre (Añadir, en la pestaña Bucles).
- El volumen, hasta 100 % (el de la grabación), o más con el Refuerzo: el
  sonido por 1,8, o por lo que se elija en el menú (Refuerzo del volumen: de
  ×1,2 a ×3), como en py-slowdown; la perilla se pone roja donde el sonido
  pasa el de la grabación.
- Marcadores en la posición, o en cualquier punto manteniendo un dedo sobre
  la onda sin moverlo; cada uno puede tener un nombre (manteniendo un dedo
  sobre su fila), y uno puede ser el primer pulso de la grabación. Se pueden
  exportar como archivo de texto, como en py-slowdown.
- Recuerda, de cada archivo, sus marcadores, sus bucles, el punto donde se
  dejó y cómo se tocó, en un archivo `.slx` como los de py-slowdown, y abre
  otra vez el último archivo al empezar. El `.slx` se puede exportar y
  cargar (menú), y guardar en una carpeta elegida, como la de la música,
  para que cada uno quede junto a su grabación y viaje con ella al
  ordenador y de vuelta.
- Un teclado, o un pedal que mande teclas, funciona con las teclas de
  py-slowdown (Espacio, ←/→, ↑/↓, B, I, O, L, Ctrl+A, Z, Ctrl+Mayús+N…), y
  el botón de los auriculares toca y pausa.
- Interfaz en inglés y en español, según el idioma del teléfono.
- Sigue sonando con la pantalla apagada, o con otra aplicación delante, con
  una notificación y los mandos de la pantalla de bloqueo.
- Cuenta antes de tocar, como un metrónomo (el botón del metrónomo, o C;
  manténgalo apretado para sus ajustes): se eligen el número de golpes y el
  tempo, que puede seguir la velocidad y medirse marcando el pulso (T); el
  botón Marcar muestra el tempo marcado. Con el primer pulso de la
  grabación marcado (Mayús+C), la grabación entra en el pulso más cercano
  donde sea que empiece a sonar.
- Oye una parte de la mezcla (menú > Oír, o la pestaña Sonido): un canal,
  los dos en uno, la mezcla sin su centro (donde suelen estar la voz y el
  solo), solo los graves o todo menos los graves.
- Un ecualizador de cinco bandas, el de py-slowdown, en la pestaña Sonido
  (o menú > Oír > Ecualizador): 60 Hz, 250 Hz, 1 kHz, 4 kHz y 12 kHz, cada
  una de −12 a +12 dB, en perillas, con una curva de lo que hace, Encendido
  (para comparar), Plano y ajustes para la música (Más graves, Oír la línea
  del bajo, La voz y los solos adelante, Brillante, Cálido, Sin el siseo) o
  propios, guardados con un nombre. Cada archivo lo guarda en su `.slx`.
  Menú > Deshacer los cambios del sonido (Ctrl+Mayús+N) devuelve de una vez
  la velocidad, la altura, los canales, el filtro y el ecualizador de la
  grabación; el volumen se queda.
- Exporta la grabación, o la selección, como suena (menú > Exportar el
  audio): a la velocidad y la altura elegidas, y como se oye, si se pide
  (los canales, el filtro, el ecualizador y el volumen); en WAV o M4A (AAC).
  (py-slowdown escribe además FLAC, MP3 y Opus.)
- Importa los archivos de Transcribe! (menú > Importar .xsc), como
  py-slowdown: sus secciones y sus compases con nombre pasan a marcadores, y
  se toman también su primer pulso y su tempo, sus bucles, su selección, su
  velocidad y su afinación, que el `.slx` guarda desde entonces. La ruta de
  la grabación en un `.xsc` es la del ordenador, así que la aplicación usa
  la grabación abierta, si tiene ese nombre, o la pide.

Compilación
-----------

Hace falta un JDK (17 o posterior) y el SDK de Android con la plataforma 36,
el NDK 29.0.14206865 y CMake 3.31.6. Escriba la ruta del SDK en
`local.properties` (`sdk.dir=/home/yo/Android/Sdk`), o ponga `ANDROID_HOME`.
Con el teléfono conectado y la depuración por USB activada:

    ./gradlew installDebug
    adb shell am start -n io.github.nivaca.slowdown/.MainActivity

`tools/test.sh` corre todas las pruebas, en paralelo: las del motor,
compiladas para el ordenador, y las del código en Kotlin, en la JVM.

Versiones y firmas
------------------

`./gradlew assembleRelease` hace el APK de la versión, reducido con R8, y lo
copia a `dist/slowdown-VERSIÓN.apk`, en lugar de los anteriores. Se firma
solo si existe el archivo `keystore.properties` en la raíz del proyecto, con
las claves `storeFile`, `storePassword`, `keyAlias` y `keyPassword`; la
clave y ese archivo no están en git. Sin ellos, el APK sale sin firmar, como
en el servidor de compilación de F-Droid.

La copia de `dist/` se firma también con OpenPGP, como
`dist/slowdown-VERSIÓN.apk.asc`, cuando gpg tiene la clave secreta del
proyecto (la misma de py-slowdown; su mitad pública es
[GPG-KEY-slowdown.asc](GPG-KEY-slowdown.asc)). La variable
`SLOWDOWN_GPG_KEY` nombra otra clave; sin la clave, la compilación lo dice y
deja solo la firma de Android. Para comprobar una versión:

    gpg --import GPG-KEY-slowdown.asc
    gpg --verify slowdown-VERSIÓN.apk.asc slowdown-VERSIÓN.apk
    gpg --verify SHA256SUMS.asc SHA256SUMS && sha256sum -c SHA256SUMS
    apksigner verify --print-certs slowdown-VERSIÓN.apk

El SHA-256 del certificado de Android es
`59f28011299559e2bc46ed38931327db41bad0b8c75a948689193c704e4e25c1`.

Una versión nueva se publica con una sola orden, después de escribir lo
nuevo bajo `## Unreleased` en `CHANGELOG.md` y `## Sin publicar` en
`CHANGELOG_es.md`:

    python3 tools/release.py [--version X.Y] [--dry-run] [--yes]

Primero corre todas las pruebas, y se detiene si alguna falla. Sube
`versionCode` y `versionName` (de 1.2 a 1.3, salvo que `--version` diga
otra), pone en esas dos secciones la versión y la fecha, pone la versión en
`docs/` y escribe las notas para las tiendas. Confirma (`vX.Y`), firma la
etiqueta y compila el APK firmado desde ella, como lo pide F-Droid; si la
compilación falla, deshace la confirmación y la etiqueta. Escribe
`dist/SHA256SUMS` y su firma `SHA256SUMS.asc`, y comprueba cada firma, y la
de la etiqueta, como lo haría quien la descarga, solo con la clave pública;
si una falla, no publica nada. Luego sube `main` y la etiqueta a `origin`
(GitHub y Codeberg), hace la versión en cada forja con el APK, `SHA256SUMS`,
sus `.asc` y la clave pública, con notas tomadas del registro de cambios, y
pone el APK en mi repositorio de F-Droid. Pregunta antes de cambiar nada, y
`--dry-run` solo dice lo que haría. Necesita `gh`, `tea`, `gpg` con la clave
del proyecto, un `main` limpio y `keystore.properties`. Si se detiene
después de etiquetar, al correrlo de nuevo publica lo que falte.

F-Droid
-------

La descripción, el icono y las notas de cada versión para F-Droid están en
`fastlane/metadata/android/` (en inglés y en español). La receta de
compilación es `fdroid/io.github.nivaca.slowdown.yml`: compila la etiqueta
`vVERSIÓN` desde la carpeta `app`, con el NDK que allí se nombra, y encuentra
las versiones nuevas por sus etiquetas.

La compilación es reproducible: el motor se compila sin las rutas de la
máquina que lo compila y sin «build id», para que F-Droid, al compilar la
misma etiqueta, obtenga el mismo APK salvo la firma, y pueda publicar el APK
firmado de la versión. `tools/check_reproducible.py [VERSIÓN]` lo comprueba
como lo haría F-Droid: clona la etiqueta en otra carpeta, la compila sin la
clave y la compara, entrada por entrada, con el APK de la versión en GitHub.
La versión 1.1 lo pasa. El APK de la versión debe compilarse
desde la confirmación etiquetada, porque el APK guarda la confirmación de la
que sale.

Licencia
--------

Slowdown es software libre: puede redistribuirlo y modificarlo bajo los
términos de la Licencia Pública General de GNU publicada por la Free
Software Foundation, versión 3 o (a su elección) cualquier versión posterior
(`GPL-3.0-or-later`; véase [LICENSE](LICENSE)).

El APK lleva también software de otros, todo bajo licencias que permiten
usarlo en un programa bajo la GPL 3:

| Software | Licencia |
|---|---|
| [Rubber Band Library](https://breakfastquay.com/rubberband/) 4.0.0, © 2007–2024 Particular Programs Ltd. | `GPL-2.0-or-later`, usada aquí bajo la GPL 3 |
| [minimp3](https://github.com/lieff/minimp3), de lieff | `CC0-1.0` |
| [dr_flac](https://github.com/mackron/dr_libs) 0.13.4 y dr_wav 0.14.6, © 2023 David Reid | `Unlicense OR MIT-0` |
| [Kotlin standard library](https://kotlinlang.org) 2.2.10, © JetBrains y colaboradores | `Apache-2.0` |
| [LLVM libc++](https://libcxx.llvm.org) y libc++abi, del NDK de Android r29 | `Apache-2.0 WITH LLVM-exception` |
| [Material Icons](https://github.com/google/material-design-icons) (las formas de los botones), © Google | `Apache-2.0` |

Los textos completos de estas licencias están en [`LICENSES/`](LICENSES), y
en la aplicación misma (menú > Licencias). [docs/TERCEROS_es.md](docs/TERCEROS_es.md)
dice para qué sirve cada uno, y lista las herramientas con que se compila la
aplicación. El código de Rubber Band, minimp3, dr_flac y dr_wav está en
[`third_party/`](third_party), tal como lo publican sus autores; de Rubber
Band solo se compilan sus propios archivos, no el código de otros que trae.

Cómo guardan sus marcadores los archivos de Transcribe! lo aprendió
py-slowdown de
[Transcribe-xsc-file-converter](https://github.com/roryokane/Transcribe-xsc-file-converter),
de Rory O’Kane (BlueOak-1.0.0); no se usa nada de su código.

Nicolas Vaughan, nicolasvaughan@proton.me
