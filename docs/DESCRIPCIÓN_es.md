# Slowdown 1.3: descripción

## Análisis

Slowdown toca audio más lento o más rápido sin cambiar su altura, en un teléfono Android. Sirve para practicar música y aprenderla de oído: oír un pasaje una y otra vez a una velocidad que se pueda seguir. Es la versión para Android de py-slowdown, el programa de escritorio de Nicolas Vaughan, y de él vienen los requisitos.

Debía conservar lo esencial de py-slowdown: el estiramiento de Rubber Band con su motor R3, la copia exacta del archivo a 1×, el cambio de velocidad y de altura (semitonos y cents) mientras suena, la selección que se repite sin hueco, los marcadores, los bucles con nombre y el archivo `.slx` en que se guarda cómo se tocó cada grabación. Ese archivo debía ser el mismo del escritorio, para que una grabación pase del teléfono al ordenador y vuelva con sus marcadores. La interfaz tenía que hacerse para el dedo (tocar, arrastrar, pellizcar, mantener) sin perder el teclado ni el pedal, y hablar inglés y español.

Después de las primeras versiones se añadió lo que faltaba de py-slowdown: el `.slx` fuera del teléfono (exportarlo, cargarlo y guardarlo junto a la grabación), tocar con la pantalla apagada, la cuenta previa, las maneras de oír y la exportación del audio. Luego vino lo que py-slowdown ganó en sus versiones 1.4 a 1.6, donde cabía en un teléfono: el ecualizador de cinco bandas, los bucles dibujados al pie de la onda, la franja del archivo entero, una pantalla de inicio con las grabaciones recientes, el menú de cada perilla, el reloj que cambia, deshacer los cambios del sonido y los archivos de Transcribe! (`.xsc`). Del ordenador quedan fuera el FLAC, el MP3 y el Opus al exportar, el modo compacto (una ventana siempre encima, que un teléfono no tiene) y la pregunta antes de cerrar un archivo, porque aquí todo se guarda al hacerse.

## Diseño

El motor está en C++ y la ventana en Kotlin. El motor tiene cuatro partes. `audio.cpp` guarda el audio decodificado en memoria, en trozos de 65 536 cuadros que no se mueven una vez escritos, con el mínimo y el máximo de cada 256 cuadros para la onda. `engine.cpp` es el motor de py-slowdown (`engine.py`) traducido casi línea por línea: Renderer hace el audio de salida por bloques, y Player lo lleva a la salida con un hilo productor y una cola de 0,15 segundos. `output.cpp` es la salida de AAudio. `decoder.cpp` y `codec.cpp` decodifican el archivo.

La ventana (`MainActivity.kt`) une los mandos con el motor, por medio de `Engine.kt` y de una sola biblioteca nativa, y está ordenada como la de py-slowdown 1.3: la onda; los botones de la reproducción (con Detener y Repetir, que sin selección repite el archivo entero) y el tiempo; las perillas de la velocidad, el volumen (con el Refuerzo, que multiplica el sonido por 1,8 o por lo que se elija en el menú, de ×1,2 a ×3, como en la configuración de py-slowdown) y la altura, y el panel de los marcadores, los bucles y el sonido, con tres pestañas: las dos primeras con una tabla y los botones Añadir, Quitar y Exportar…, la tercera con los canales, el filtro y el ecualizador (cinco perillas bajo su curva, `EqCurve.kt`). `WaveformView.kt` dibuja la onda, la regla, la selección, los marcadores, los bucles al pie y, de cerca, la franja del archivo entero, con los colores de py-slowdown (`Palette.kt`), claros u oscuros según el teléfono. `Knob.kt` es la perilla de py-slowdown; con el dedo se gira arrastrando hacia arriba o a la derecha, porque una perilla pequeña queda bajo el dedo, vuelve a su valor con un doble toque y, con el dedo quieto, abre el menú de sus valores de costumbre. `Sidecar.kt` lee y escribe el `.slx`, en cualquier `SlxFolder`: la carpeta de la aplicación o una elegida por el usuario (`FolderTree.kt`). `Playback.kt` guarda lo que suena fuera de la ventana, y `PlaybackService.kt` es el servicio de reproducción, con su sesión de medios y su notificación. `CountInRules.kt` tiene las reglas de la cuenta previa (y el compás y el pulso del reloj); `hearing.cpp`, las maneras de oír; `equalizer.cpp` y `Equalizer.kt`, el ecualizador en el motor y en la ventana; `Transcribe.kt`, la lectura de los `.xsc`; `Recents.kt`, las grabaciones de la pantalla de inicio; `exporter.cpp`, la exportación.

Cargar un `.slx` sin una grabación abierta pide la suya, como un `.xsc`. Las opciones del menú que no se pueden usar se pintan en gris desde el código, porque el tema del sistema de algunos teléfonos (los Motorola) las dibuja como las demás.

Se prefirió C++ a conservar Python porque PortAudio, la salida de sonido de py-slowdown, no existe en Android, y porque un hilo de Python que alimenta la salida cada pocos milisegundos es un riesgo en un teléfono. La ventana usa los controles de Android, sin Jetpack Compose ni otras bibliotecas: el programa no depende de nada fuera de Android y de lo que trae en `third_party/`.

## Implementación

Sin las bibliotecas de `third_party/`, los textos de `LICENSES/` ni la documentación, el programa tiene 106 archivos propios (código, disposiciones, textos, archivos de compilación, los textos de las tiendas, pruebas y herramientas), con unas 12 100 líneas: 2790 de C++ en el motor, 4010 de Kotlin en la ventana, 1770 en las pruebas y 1610 en las herramientas. Se compila con Gradle 9.9, el complemento de Android 9.5, el NDK 29 y CMake 3.31.

### Rubber Band

Rubber Band 4.0.0 se compila desde su archivo único (`single/RubberBandSingle.cpp`, con su propia FFT), el mismo de py-slowdown, con el compilador del NDK y siempre optimizado. En el Moto g55 su calidad más alta va entre 4,6 y 5,8 veces el tiempo real, en un solo núcleo.

### La decodificación

Los códecs de Android resultaron lentos: cada búfer va y vuelve de otro proceso, y en este teléfono eso deja un MP3 en unas 13 veces el tiempo real; un FLAC, que el extractor parte en decenas de miles de trozos, quedaba por debajo del tiempo real, y además a medias, porque su códec decía dar muestras en coma flotante y las daba en 16 bits. Por eso MP3, FLAC, WAV y AIFF se decodifican en la aplicación misma, con minimp3, dr_flac y dr_wav (un encabezado cada uno, fijados en un commit de meses antes): una canción de cinco minutos en medio segundo. El formato se reconoce por los primeros bytes. Lo demás (AAC, Opus, Vorbis, el sonido de un video) va a los códecs de Android, y su onda se completa mientras ya suena.

### Lo que se añadió después

La cuenta previa copia las reglas de `countin.py`; las maneras de oír pasan `hearing.py` a C++, con los mismos filtros de fase lineal, aplicados con la FFT de Rubber Band. La exportación hace el audio aparte, en un hilo, y lo escribe en WAV o, con el codificador AAC de Android, en M4A. Se probó decodificar un AAC en cuatro trozos a la vez, y fue ocho veces más rápido; pero con varios a la vez el servicio de códecs de Android daba muestras que cambiaban de una vez a otra, y a veces negaba un segundo códec, así que el AAC sigue con un solo códec.

### Lo que vino del escritorio, de 1.4 a 1.7

El ecualizador es `equalizer.py` en C++: las cinco ganancias son puntos de una curva (recta entre dos bandas, sobre el logaritmo de la frecuencia), la curva se vuelve un filtro de fase lineal de 80 ms, y el filtro se aplica con la FFT de Rubber Band, bloque tras bloque, con un fundido cuando cambian las ganancias. La `irfft` y la ventana de Hann de NumPy se escribieron a mano. Player toma las ganancias bajo su cerrojo y las aplica después de las maneras de oír, como el escritorio; la exportación descuenta el retraso de los dos filtros. El `.slx` lo guarda en el mismo `<equalizer>` de py-slowdown. La pestaña Sonido y el menú Oír dicen siempre lo mismo.

Un `.xsc` nombra su grabación con una ruta del ordenador, que en el teléfono no sirve: la aplicación toma la grabación abierta, si se llama así, o pide elegirla. La pantalla de inicio guarda en las preferencias un resumen de 120 columnas de la onda de cada grabación, porque la aplicación no tiene, como el escritorio, un caché de picos. Al dibujar esa pantalla apareció un error curioso: dentro de un bloque `apply` de Kotlin, `density` era el campo propio de `TextPaint` (que vale 1) y no el de la pantalla, y los nombres salían diminutos.

De la 1.7 vino Tocar desde la última pausa, con lo que el escritorio hacía desde su 1.3 y el teléfono no: la pausa vuelve adonde empezó a tocar, y una línea de puntos marca dónde fue. Está en `Playback.kt`, así que la notificación y los auriculares hacen lo mismo que el botón; mantener apretado Tocar sigue desde la pausa.

### La salida

AAudio pide los cuadros a Player. Tras medio segundo sin sonar, el flujo se pausa para no gastar batería; si la salida cambia (auriculares, Bluetooth), se abre de nuevo. La posición que se muestra descuenta la latencia de la salida, medida con su marca de tiempo.

## Validación

Hay sesenta y cuatro pruebas. Siete programas en C++ prueban el motor compilado para el ordenador: el audio en memoria; Renderer (la copia exacta a 1×, la duración y la altura del sonido estirado a tres velocidades y en las tres calidades, el bucle sin hueco, los saltos, la cuenta previa); Player con una salida de prueba; los decodificadores, con un segundo de La 440 en MP3, FLAC y WAV y un WAV 5.1; las maneras de oír; el ecualizador (su curva, la ganancia de cada banda, que plano no toque nada, que por bloques dé lo mismo que de una vez y que un cambio no chasquee), y la exportación, también con el ecualizador y sin su retraso. Treinta y siete pruebas en Kotlin comprueban el `.slx` (entre ellas, que se lee uno escrito por py-slowdown), la exportación de los marcadores, el formato de los tiempos y de los factores del refuerzo, que las licencias de la aplicación, de `LICENSES/` y de los documentos coincidan, cómo se ajustan sus textos a la pantalla, el `.slx` en cualquier carpeta, las reglas de la cuenta previa y el compás y el pulso, la curva del ecualizador, sus ajustes propios y su lugar en el `.slx`, los archivos de Transcribe!, las grabaciones recientes y adónde vuelve una pausa. Veinte pruebas en Python comprueban las herramientas de la publicación (`tools/zenodo.py`, también la publicación en Zenodo). `tools/check_reproducible.py` compila una versión de nuevo desde su etiqueta y la compara con el APK publicado, como F-Droid; la 1.1 es reproducible. `tools/test.sh` las corre todas en paralelo, cada una en su proceso.

Las pruebas del motor se compilaron también para el teléfono y pasan allí. La aplicación se probó en un Moto g55 con Android 16: abrir con el selector de Android, tocar, cambiar la velocidad y la altura mientras suena (el sistema de sonido no contó ningún vacío), seleccionar y repetir, y el `.slx` escrito al salir. Lo que vino de las versiones 1.4 a 1.6 del escritorio se probó en el emulador de Android (una imagen x86_64 de Android 16, que corre el motor ARM por traducción): el ecualizador y un ajuste mientras suena, su `.slx`, un bucle sobre la onda, acercar a la selección con la franja del archivo entero, el reloj, el menú de una perilla, la pantalla de inicio y un `.xsc` importado con el selector. Lo de la 1.4 (el menú en gris, un `.slx` cargado sin grabación abierta y la pausa) se probó de nuevo en el Moto g55.

## Plataforma

Necesita Android 12 o posterior, en un teléfono ARM de 64 bits. No usa internet. Sus únicos permisos son los del servicio de reproducción en primer plano, que Android concede sin preguntar; los archivos se abren con el selector de Android, que da acceso a cada uno, y la carpeta de los `.slx`, si se elige, con el selector de carpetas. Lee MP3, FLAC, WAV y AIFF por sí mismo, y lo demás que Android decodifique.

### Licencias

Slowdown es software libre, bajo la Licencia Pública General de GNU, versión 3 o posterior. Lleva la biblioteca Rubber Band 4.0.0, de Particular Programs Ltd. (Breakfast Quay), bajo la GPL, versión 2 o posterior (usada aquí bajo la GPL 3); minimp3, de lieff (CC0); dr_flac y dr_wav, de David Reid (Unlicense o MIT sin atribución); la biblioteca estándar de Kotlin (Licencia Apache 2.0); la libc++ de LLVM, enlazada en el motor (Licencia Apache 2.0 con las excepciones de LLVM), y las formas de los Material Icons de Google (Licencia Apache 2.0). De Rubber Band solo se compilan sus 62 archivos propios, todos con el mismo aviso; nada del código de otros que trae en `src/ext/`. Los textos completos están en `LICENSES/`, tal como los publican sus autores, y van también en el APK, donde los muestra la pantalla Licencias, porque la GPL y la Licencia Apache piden que la licencia viaje con el programa; `docs/TERCEROS_es.md` dice para qué sirve cada uno. Una prueba comprueba que la lista de la aplicación, los textos y los documentos coincidan. Cómo guardan sus marcadores los archivos de Transcribe! lo aprendió py-slowdown de Transcribe-xsc-file-converter, de Rory O'Kane (Blue Oak Model License 1.0.0), sin tomar nada de su código.

## Ambiente

Fue desarrollado en Fedora 45, con el SDK de Android de línea de órdenes (sin Android Studio), Java 25, Gradle 9.9, el NDK 29 y CMake 3.31, y probado en un Moto g55 5G con Android 16. El código está en GitHub (`nivaca/slowdown-android`) y en Codeberg (con el mismo nombre). Cada versión se publica con `tools/release.py`, como las otras dos apps de Android del autor: prueba, etiqueta firmada, APK firmado con la clave de Android de la aplicación y con la de OpenPGP de py-slowdown, `SHA256SUMS`, todas las firmas comprobadas solo con la clave pública, y la versión en las dos forjas, en el repositorio de F-Droid propio (`https://nivaca.codeberg.page/fdroid/repo`) y en Zenodo, que da a cada versión su DOI. Se instala desde allí, con Obtainium, o desde el código, con el teléfono conectado y la depuración por USB activada:

    ./gradlew installDebug
    adb shell am start -n io.github.nivaca.slowdown/.MainActivity

Los ajustes (la calidad, la longitud del salto, el último archivo, el modo del reloj, los ajustes propios del ecualizador y las grabaciones recientes con su figura) se guardan en las preferencias de la aplicación; los `.slx`, en su carpeta `files/slx` y, si se eligió una, también en ella.

Contacto: nicolasvaughan@proton.me
