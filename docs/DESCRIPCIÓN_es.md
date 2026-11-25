# Slowdown 0.1.0: descripción

## Análisis

Slowdown toca audio más lento o más rápido sin cambiar su altura, en un teléfono Android. Sirve para practicar música y aprenderla de oído: oír un pasaje una y otra vez a una velocidad que se pueda seguir. Es la versión para Android de py-slowdown, el programa de escritorio de Nicolas Vaughan, y de él vienen los requisitos.

Debía conservar lo esencial de py-slowdown: el estiramiento de Rubber Band con su motor R3, la copia exacta del archivo a 1×, el cambio de velocidad y de altura (semitonos y cents) mientras suena, la selección que se repite sin hueco, los marcadores, los bucles con nombre y el archivo `.slx` en que se guarda cómo se tocó cada grabación. Ese archivo debía ser el mismo del escritorio, para que una grabación pase del teléfono al ordenador y vuelva con sus marcadores. La interfaz tenía que hacerse para el dedo (tocar, arrastrar, pellizcar, mantener) sin perder el teclado ni el pedal, y hablar inglés y español.

Quedan para después la cuenta previa, las maneras de oír (un canal, sin el centro, los graves), la exportación del audio y tocar con la pantalla apagada.

## Diseño

El motor está en C++ y la ventana en Kotlin. El motor tiene cuatro partes. `audio.cpp` guarda el audio decodificado en memoria, en trozos de 65 536 cuadros que no se mueven una vez escritos, con el mínimo y el máximo de cada 256 cuadros para la onda. `engine.cpp` es el motor de py-slowdown (`engine.py`) traducido casi línea por línea: Renderer hace el audio de salida por bloques, y Player lo lleva a la salida con un hilo productor y una cola de 0,15 segundos. `output.cpp` es la salida de AAudio. `decoder.cpp` y `codec.cpp` decodifican el archivo.

La ventana (`MainActivity.kt`) une los mandos con el motor, por medio de `Engine.kt` y de una sola biblioteca nativa. `WaveformView.kt` dibuja la onda, la regla, la selección y los marcadores, y recibe los gestos. `Sidecar.kt` lee y escribe el `.slx`.

Se prefirió C++ a conservar Python porque PortAudio, la salida de sonido de py-slowdown, no existe en Android, y porque un hilo de Python que alimenta la salida cada pocos milisegundos es un riesgo en un teléfono. La ventana usa los controles de Android, sin Jetpack Compose ni otras bibliotecas: el programa no depende de nada fuera de Android y de lo que trae en `third_party/`.

## Implementación

Sin las bibliotecas de `third_party/` ni la documentación, el programa tiene 55 archivos propios (código, disposiciones, textos, archivos de compilación y pruebas), con unas 4600 líneas: 1860 de C++ en el motor, 1430 de Kotlin en la ventana y 650 en las pruebas. Se compila con Gradle 9.9, el complemento de Android 9.5, el NDK 29 y CMake 3.31.

### Rubber Band

Rubber Band 4.0.0 se compila desde su archivo único (`single/RubberBandSingle.cpp`, con su propia FFT), el mismo de py-slowdown, con el compilador del NDK y siempre optimizado. En el Moto g55 su calidad más alta va entre 4,6 y 5,8 veces el tiempo real, en un solo núcleo.

### La decodificación

Los códecs de Android resultaron lentos: cada búfer va y vuelve de otro proceso, y en este teléfono eso deja un MP3 en unas 13 veces el tiempo real; un FLAC, que el extractor parte en decenas de miles de trozos, quedaba por debajo del tiempo real, y además a medias, porque su códec decía dar muestras en coma flotante y las daba en 16 bits. Por eso MP3, FLAC, WAV y AIFF se decodifican en la aplicación misma, con minimp3, dr_flac y dr_wav (un encabezado cada uno, fijados en un commit de meses antes): una canción de cinco minutos en medio segundo. El formato se reconoce por los primeros bytes. Lo demás (AAC, Opus, Vorbis, el sonido de un video) va a los códecs de Android, y su onda se completa mientras ya suena.

### La salida

AAudio pide los cuadros a Player. Tras medio segundo sin sonar, el flujo se pausa para no gastar batería; si la salida cambia (auriculares, Bluetooth), se abre de nuevo. La posición que se muestra descuenta la latencia de la salida, medida con su marca de tiempo.

## Validación

Hay trece pruebas. Cuatro programas en C++ prueban el motor compilado para el ordenador: el audio en memoria; Renderer (la copia exacta a 1×, la duración y la altura del sonido estirado a tres velocidades y en las tres calidades, el bucle sin hueco, los saltos, la cuenta previa); Player con una salida de prueba, y los decodificadores, con un segundo de La 440 en MP3, FLAC y WAV y un WAV 5.1. Nueve pruebas en Kotlin comprueban el `.slx`, entre ellas que se lee uno escrito por py-slowdown. `tools/test.sh` las corre todas en paralelo, cada una en su proceso.

Las pruebas del motor se compilaron también para el teléfono y pasan allí. La aplicación se probó en un Moto g55 con Android 16: abrir con el selector de Android, tocar, cambiar la velocidad y la altura mientras suena (el sistema de sonido no contó ningún vacío), seleccionar y repetir, y el `.slx` escrito al salir.

## Plataforma

Necesita Android 12 o posterior, en un teléfono ARM de 64 bits. No usa internet ni pide permisos: los archivos se abren con el selector de Android, que da acceso a cada uno. Lee MP3, FLAC, WAV y AIFF por sí mismo, y lo demás que Android decodifique.

### Licencias

Slowdown es software libre, bajo la Licencia Pública General de GNU, versión 3 o posterior. Incluye Rubber Band 4.0.0 (GPL, versión 2 o posterior), minimp3 (CC0) y dr_flac y dr_wav (dominio público, o MIT sin atribución).

## Ambiente

Fue desarrollado en Fedora 45, con el SDK de Android de línea de órdenes (sin Android Studio), Java 25, Gradle 9.9, el NDK 29 y CMake 3.31, y probado en un Moto g55 5G con Android 16. El código está en `~/Dropbox/dev/Android/slowdown`; todavía no se ha publicado.

Para instalarlo desde el código, con el teléfono conectado y la depuración por USB activada:

    ./gradlew installDebug
    adb shell am start -n io.github.nivaca.slowdown/.MainActivity

Los ajustes (la calidad, la longitud del salto, el último archivo) se guardan en las preferencias de la aplicación; los `.slx`, en su carpeta `files/slx`.

Contacto: nicolasvaughan@proton.me
