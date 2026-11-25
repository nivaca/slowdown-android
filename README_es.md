Slowdown para Android
=====================

Slowdown toca audio más lento o más rápido sin cambiar su altura. Está hecho
para la música: para practicar una pieza y aprenderla de oído, oyendo un
pasaje una y otra vez a una velocidad que se pueda seguir. Es la versión para
Android de [py-slowdown](https://github.com/nivaca/py-slowdown), con el mismo
motor y los mismos archivos `.slx`.

*Read in English: [README.md](README.md).*

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
  altura, y el panel de los marcadores y los bucles, con sus dos pestañas. Un
  toque en la onda lleva la posición allí; arrastrar desplaza la vista;
  pellizcar acerca o aleja alrededor de los dedos. Una perilla se gira
  arrastrando hacia arriba o a la derecha (hacia abajo o a la izquierda, al
  revés), y vuelve a su valor de siempre con un doble toque.
- Tocar, pausar, detener, ir al principio o al final, atrás y adelante según una
  longitud del salto que se elige en el menú (de 1 a 60 segundos; mantenga
  apretado el botón para seguir).
- Velocidad de 0,25× a 3×, que se cambia mientras suena, sin cambiar la
  altura. El estiramiento lo hace la [biblioteca Rubber Band](https://breakfastquay.com/rubberband/),
  con su motor R3 («finer»). A 1× el archivo suena tal cual, muestra por
  muestra. El botón Velocidades tiene las de py-slowdown (50 %, 75 %,
  100 %…). En el menú se puede elegir una calidad ligera (la mitad del
  procesador) o el motor anterior, R2 (ataques nítidos).
- Sube o baja la altura en semitonos (de −12 a +12) y cents (de −50 a +50),
  también mientras suena; con «Conservar los formantes», una voz o un
  instrumento conserva su timbre.
- Repite una selección sin fin y sin hueco, o el archivo entero si no hay:
  mantenga un dedo sobre la onda y arrástrelo, o ponga su principio y su
  final con A y B en la posición. Las selecciones se pueden guardar como
  bucles con un nombre (Añadir, en la pestaña Bucles).
- El volumen, hasta 100 % (el de la grabación), o más con el Refuerzo (el
  sonido por 1,5, la perilla en rojo donde pasa el de la grabación).
- Marcadores en la posición, o en cualquier punto manteniendo un dedo sobre
  la onda sin moverlo; cada uno puede tener un nombre (manteniendo un dedo
  sobre su fila), y uno puede ser el primer pulso de la grabación. Se pueden
  exportar como archivo de texto, como en py-slowdown.
- Recuerda, de cada archivo, sus marcadores, sus bucles, el punto donde se
  dejó y cómo se tocó, en un archivo `.slx` como los de py-slowdown, y abre
  otra vez el último archivo al empezar.
- Un teclado, o un pedal que mande teclas, funciona con las teclas de
  py-slowdown (Espacio, ←/→, ↑/↓, B, I, O, L…), y el botón de los
  auriculares toca y pausa.
- Interfaz en inglés y en español, según el idioma del teléfono.

Todavía no están en esta versión (sí en py-slowdown): la cuenta previa, las
maneras de oír (un canal, sin el centro, los graves), la exportación del
audio y tocar con la pantalla apagada: por ahora la pantalla no se apaga
mientras suena, y se pausa al salir de la aplicación.

Compilar y correr
-----------------

Hace falta el SDK de Android con el NDK (29) y CMake (3.31), y Java 17 o
posterior. Con el teléfono conectado y la depuración por USB activada:

    ./gradlew installDebug
    adb shell am start -n io.github.nivaca.slowdown/.MainActivity

`tools/test.sh` corre todas las pruebas, en paralelo: las del motor,
compiladas para el ordenador, y las del código en Kotlin, en la JVM.

Necesita Android 12 o posterior, en un teléfono ARM de 64 bits.

Licencia
--------

Slowdown es software libre, bajo la Licencia Pública General de GNU, versión
3 o posterior (véase [LICENSE](LICENSE)). Incluye la biblioteca Rubber Band
4.0.0 (GPL, versión 2 o posterior) y los decodificadores minimp3 (CC0) y
dr_flac y dr_wav (dominio público, o MIT sin atribución), en `third_party/`.

Nicolas Vaughan, nicolasvaughan@proton.me
