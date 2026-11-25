# Slowdown 1.0: descripción

## Análisis

Slowdown toca audio más lento o más rápido sin cambiar su altura, en un teléfono Android. Sirve para practicar música y aprenderla de oído: oír un pasaje una y otra vez a una velocidad que se pueda seguir. Es la versión para Android de py-slowdown, el programa de escritorio de Nicolas Vaughan, y de él vienen los requisitos.

Debía conservar lo esencial de py-slowdown: el estiramiento de Rubber Band con su motor R3, la copia exacta del archivo a 1×, el cambio de velocidad y de altura (semitonos y cents) mientras suena, la selección que se repite sin hueco, los marcadores, los bucles con nombre y el archivo `.slx` en que se guarda cómo se tocó cada grabación. Ese archivo debía ser el mismo del escritorio, para que una grabación pase del teléfono al ordenador y vuelva con sus marcadores. La interfaz tenía que hacerse para el dedo (tocar, arrastrar, pellizcar, mantener) sin perder el teclado ni el pedal, y hablar inglés y español.

Quedan para después la cuenta previa, las maneras de oír (un canal, sin el centro, los graves), la exportación del audio y tocar con la pantalla apagada.

## Diseño

El motor está en C++ y la ventana en Kotlin. El motor tiene cuatro partes. `audio.cpp` guarda el audio decodificado en memoria, en trozos de 65 536 cuadros que no se mueven una vez escritos, con el mínimo y el máximo de cada 256 cuadros para la onda. `engine.cpp` es el motor de py-slowdown (`engine.py`) traducido casi línea por línea: Renderer hace el audio de salida por bloques, y Player lo lleva a la salida con un hilo productor y una cola de 0,15 segundos. `output.cpp` es la salida de AAudio. `decoder.cpp` y `codec.cpp` decodifican el archivo.

La ventana (`MainActivity.kt`) une los mandos con el motor, por medio de `Engine.kt` y de una sola biblioteca nativa, y está ordenada como la de py-slowdown 1.3: la onda; los botones de la reproducción (con Detener y Repetir, que sin selección repite el archivo entero) y el tiempo; las perillas de la velocidad, el volumen (con el Refuerzo, que multiplica el sonido por 1,8 o por lo que se elija en el menú, de ×1,2 a ×3, como en la configuración de py-slowdown) y la altura, y el panel de los marcadores y los bucles, con dos pestañas, una tabla y los botones Añadir, Quitar y Exportar…. `WaveformView.kt` dibuja la onda, la regla, la selección y los marcadores con los colores de py-slowdown (`Palette.kt`), claros u oscuros según el teléfono. `Knob.kt` es la perilla de py-slowdown; con el dedo se gira arrastrando hacia arriba o a la derecha, porque una perilla pequeña queda bajo el dedo, y vuelve a su valor con un doble toque. `Sidecar.kt` lee y escribe el `.slx`.

Se prefirió C++ a conservar Python porque PortAudio, la salida de sonido de py-slowdown, no existe en Android, y porque un hilo de Python que alimenta la salida cada pocos milisegundos es un riesgo en un teléfono. La ventana usa los controles de Android, sin Jetpack Compose ni otras bibliotecas: el programa no depende de nada fuera de Android y de lo que trae en `third_party/`.

## Implementación

Sin las bibliotecas de `third_party/`, los textos de `LICENSES/` ni la documentación, el programa tiene 72 archivos propios (código, disposiciones, textos, archivos de compilación, los textos de las tiendas, pruebas y herramientas), con unas 6300 líneas: 1870 de C++ en el motor, 2020 de Kotlin en la ventana, 730 en las pruebas y 680 en las herramientas de las versiones. Se compila con Gradle 9.9, el complemento de Android 9.5, el NDK 29 y CMake 3.31.

### Rubber Band

Rubber Band 4.0.0 se compila desde su archivo único (`single/RubberBandSingle.cpp`, con su propia FFT), el mismo de py-slowdown, con el compilador del NDK y siempre optimizado. En el Moto g55 su calidad más alta va entre 4,6 y 5,8 veces el tiempo real, en un solo núcleo.

### La decodificación

Los códecs de Android resultaron lentos: cada búfer va y vuelve de otro proceso, y en este teléfono eso deja un MP3 en unas 13 veces el tiempo real; un FLAC, que el extractor parte en decenas de miles de trozos, quedaba por debajo del tiempo real, y además a medias, porque su códec decía dar muestras en coma flotante y las daba en 16 bits. Por eso MP3, FLAC, WAV y AIFF se decodifican en la aplicación misma, con minimp3, dr_flac y dr_wav (un encabezado cada uno, fijados en un commit de meses antes): una canción de cinco minutos en medio segundo. El formato se reconoce por los primeros bytes. Lo demás (AAC, Opus, Vorbis, el sonido de un video) va a los códecs de Android, y su onda se completa mientras ya suena.

### La salida

AAudio pide los cuadros a Player. Tras medio segundo sin sonar, el flujo se pausa para no gastar batería; si la salida cambia (auriculares, Bluetooth), se abre de nuevo. La posición que se muestra descuenta la latencia de la salida, medida con su marca de tiempo.

## Validación

Hay veintiuna pruebas. Cuatro programas en C++ prueban el motor compilado para el ordenador: el audio en memoria; Renderer (la copia exacta a 1×, la duración y la altura del sonido estirado a tres velocidades y en las tres calidades, el bucle sin hueco, los saltos, la cuenta previa); Player con una salida de prueba, y los decodificadores, con un segundo de La 440 en MP3, FLAC y WAV y un WAV 5.1. Diecisiete pruebas en Kotlin comprueban el `.slx` (entre ellas, que se lee uno escrito por py-slowdown), la exportación de los marcadores, el formato de los tiempos y de los factores del refuerzo, que las licencias de la aplicación, de `LICENSES/` y de los documentos coincidan, y cómo se ajustan sus textos a la pantalla. `tools/test.sh` las corre todas en paralelo, cada una en su proceso.

Las pruebas del motor se compilaron también para el teléfono y pasan allí. La aplicación se probó en un Moto g55 con Android 16: abrir con el selector de Android, tocar, cambiar la velocidad y la altura mientras suena (el sistema de sonido no contó ningún vacío), seleccionar y repetir, y el `.slx` escrito al salir.

## Plataforma

Necesita Android 12 o posterior, en un teléfono ARM de 64 bits. No usa internet ni pide permisos: los archivos se abren con el selector de Android, que da acceso a cada uno. Lee MP3, FLAC, WAV y AIFF por sí mismo, y lo demás que Android decodifique.

### Licencias

Slowdown es software libre, bajo la Licencia Pública General de GNU, versión 3 o posterior. Lleva la biblioteca Rubber Band 4.0.0, de Particular Programs Ltd. (Breakfast Quay), bajo la GPL, versión 2 o posterior (usada aquí bajo la GPL 3); minimp3, de lieff (CC0); dr_flac y dr_wav, de David Reid (Unlicense o MIT sin atribución); la biblioteca estándar de Kotlin (Licencia Apache 2.0); la libc++ de LLVM, enlazada en el motor (Licencia Apache 2.0 con las excepciones de LLVM), y las formas de los Material Icons de Google (Licencia Apache 2.0). De Rubber Band solo se compilan sus 62 archivos propios, todos con el mismo aviso; nada del código de otros que trae en `src/ext/`. Los textos completos están en `LICENSES/`, tal como los publican sus autores, y van también en el APK, donde los muestra la pantalla Licencias, porque la GPL y la Licencia Apache piden que la licencia viaje con el programa; `docs/TERCEROS_es.md` dice para qué sirve cada uno. Una prueba comprueba que la lista de la aplicación, los textos y los documentos coincidan.

## Ambiente

Fue desarrollado en Fedora 45, con el SDK de Android de línea de órdenes (sin Android Studio), Java 25, Gradle 9.9, el NDK 29 y CMake 3.31, y probado en un Moto g55 5G con Android 16. El código está en GitHub (`nivaca/slowdown-android`) y en Codeberg (con el mismo nombre). Cada versión se publica con `tools/release.py`, como las otras dos apps de Android del autor: prueba, etiqueta firmada, APK firmado con la clave de Android de la aplicación y con la de OpenPGP de py-slowdown, `SHA256SUMS`, todas las firmas comprobadas solo con la clave pública, y la versión en las dos forjas y en el repositorio de F-Droid propio (`https://nivaca.codeberg.page/fdroid/repo`). Se instala desde allí, con Obtainium, o desde el código, con el teléfono conectado y la depuración por USB activada:

    ./gradlew installDebug
    adb shell am start -n io.github.nivaca.slowdown/.MainActivity

Los ajustes (la calidad, la longitud del salto, el último archivo) se guardan en las preferencias de la aplicación; los `.slx`, en su carpeta `files/slx`.

Contacto: nicolasvaughan@proton.me
