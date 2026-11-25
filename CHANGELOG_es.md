# Registro de cambios

Los cambios de Slowdown, la versión para Android de
[py-slowdown](https://github.com/nivaca/py-slowdown). El código está en
[GitHub](https://github.com/nivaca/slowdown-android) y
[Codeberg](https://codeberg.org/nivaca/slowdown-android).

## Sin publicar

- El `.slx` ya puede salir del teléfono, y entrar: menú > Exportar el .slx
  (con el nombre que le da py-slowdown, canción.slx), y Cargar un .slx, que
  toma los marcadores, los bucles y los ajustes de un `.slx` hecho en el
  ordenador (pregunta antes si es de otra grabación). Menú > Carpeta de los
  .slx puede guardarlos en una carpeta elegida, como la de la música, para
  que cada uno quede junto a su grabación, como en el ordenador, y viaje con
  ella; la copia de la aplicación se queda también.
- Sigue sonando con la pantalla apagada, o con otra aplicación delante: un
  servicio de reproducción con su notificación (atrás, tocar/pausa,
  adelante) en la pantalla de bloqueo y en las notificaciones, y los botones
  de los auriculares. La aplicación ya no se pausa al salir de ella; al
  volver, muestra lo que suena. Sigue pausándose si se desconectan los
  auriculares, o si otra aplicación toma el sonido (una llamada).
- La cuenta previa de py-slowdown: golpes antes de tocar (el botón del
  metrónomo; manténgalo apretado para sus ajustes: cuántos golpes, el tempo,
  que puede seguir la velocidad, y el desfase de la grabación). El tempo se
  puede medir marcando el pulso (Marcar, o T); con el primer pulso de la
  grabación marcado (Mayús+C), la grabación entra en el pulso más cercano
  donde sea que empiece a sonar, e Ir al principio va a ese pulso. El pulso
  que suena se ve grande sobre la onda. Cada archivo guarda su tempo en su
  `.slx`.
- Las maneras de oír de py-slowdown (menú > Oír), para sacar una parte de
  una mezcla: un canal, los dos en uno (mono), sin el centro (donde suelen
  estar la voz y el solo), solo los graves o todo menos los graves. Los
  filtros son los mismos, de fase lineal, a 250 Hz; cada archivo guarda su
  manera en su `.slx`.
- Exporta la grabación, o la selección, como suena (menú > Exportar el
  audio): a la velocidad y la altura elegidas y, si se pide, como se oye
  (los canales, el filtro, el volumen). WAV (16 bits) o M4A (AAC, con el
  codificador de Android). El FLAC, el MP3 y el Opus de py-slowdown todavía
  no están: Android no puede escribir un archivo FLAC, no tiene codificador
  de MP3, y su codificador de Opus solo toma 48 kHz.
- La pantalla de las licencias, en español, lo dice todo en español (dos
  notas estaban en inglés); capturas nuevas, con el metrónomo.

## 1.1 — 2026-11-25

- En español, la fila de la velocidad y el volumen ya no se sale de la
  pantalla («Velocidad» quedaba cortada y «Refuerzo» partido en dos): las
  perillas y los botones de al lado son un poco más pequeños.
- El idioma de la aplicación se puede elegir aparte del del teléfono, en
  los ajustes de Android de la aplicación (inglés o español).
- Capturas de pantalla para F-Droid, en inglés y en español.

## 1.0 — 2026-11-25

La primera versión.

- Toca audio más lento o más rápido (de 0,25× a 3×) sin cambiar su altura,
  con el motor R3 de la biblioteca Rubber Band, como py-slowdown; a 1× el
  archivo suena tal cual, muestra por muestra.
- Sube o baja la altura en semitonos y cents, con los formantes
  conservados si se pide; una perilla de volumen con Refuerzo (de ×1,2 a
  ×3, elegido en el menú).
- La ventana de py-slowdown 1.3, para un teléfono: la onda con sus colores,
  los botones de la reproducción, las perillas y el panel de los
  marcadores y los bucles.
- Repite una selección, o el archivo entero, sin hueco; marcadores con
  nombre, y bucles guardados con un nombre.
- Lee por sí misma MP3, FLAC, WAV y AIFF (una canción de cinco minutos en
  medio segundo), y lo demás con los códecs de Android.
- Guarda cómo se tocó cada archivo en un `.slx`, el mismo de py-slowdown.
- Interfaz en inglés y en español.
