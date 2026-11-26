# Registro de cambios

Los cambios de Slowdown, la versión para Android de
[py-slowdown](https://github.com/nivaca/py-slowdown). El código está en
[GitHub](https://github.com/nivaca/slowdown-android) y
[Codeberg](https://codeberg.org/nivaca/slowdown-android).

## Sin publicar

- Las opciones del menú que aún no se pueden usar (Cargar un .slx…, Guardar,
  las exportaciones, Cerrar el archivo, mientras no hay una grabación abierta)
  se ven ahora en gris. Algunos teléfonos, los Motorola entre ellos, las
  dibujaban como las demás, y Cargar un .slx… parecía no hacer nada.
- **Cargar un .slx…** funciona sin una grabación abierta, como Importar
  .xsc…: lee de qué grabación es el .slx, la pide y la abre con lo que el .slx
  guarda.

## 1.3 — 2026-11-26

Lo que py-slowdown ganó en sus versiones 1.4 a 1.6, donde cabe en un teléfono.

- **Un ecualizador de cinco bandas**, el de py-slowdown, en una pestaña
  nueva del panel, Sonido (también menú > Oír > Ecualizador): 60 Hz, 250 Hz,
  1 kHz, 4 kHz y 12 kHz, cada una de −12 a +12 dB, en perillas, con una curva
  de lo que hace de 20 Hz a 20 kHz, Encendido (para comparar), Plano y
  Ajustes: los de py-slowdown para la música (Más graves, Oír la línea del
  bajo, La voz y los solos adelante, Brillante, Cálido, Sin el siseo) y los
  propios, guardados con un nombre y quitados otra vez. Es el mismo filtro
  de fase lineal del ordenador, que el motor aplica bloque tras bloque, con
  un fundido cuando cambia. Cada archivo lo guarda en su `.slx`, en el mismo
  `<equalizer>` de py-slowdown; una exportación como se oye lo aplica, sin
  sus 40 ms de retraso. La pestaña Sonido tiene también los canales y el
  filtro del menú Oír, y los dos dicen siempre lo mismo; dice «· EQ»
  mientras el ecualizador cambia el sonido.
- **Deshacer los cambios del sonido** (menú, o Ctrl+Mayús+N): velocidad
  100 %, la altura de la grabación, sin formantes, los dos canales sin
  filtro y el ecualizador plano, de una vez; el volumen se queda.
- **Los bucles sobre la onda**: cada bucle guardado es una banda de su
  color al pie de la onda, con su nombre; un toque en ella lo elige, lo
  repite y va a su principio.
- **El archivo entero sobre una vista de cerca**: con la onda acercada, una
  franja encima muestra el archivo entero, la parte que se ve sombreada y
  la posición en rojo; un toque o un arrastre allí muestra esa parte, sin
  mover la posición. Una vista corrida a mano ya no vuelve de un salto a la
  posición mientras está en pausa.
- **Acercar a la selección** (menú, o Z) y **Seleccionar el archivo
  entero** (menú, o Ctrl+A).
- **Una pantalla de inicio**: sin un archivo, la onda muestra las
  grabaciones abiertas hace poco, cada una con una figura pequeña de su
  onda; un toque abre una; la que ya no está aparece en gris.
- **Un reloj que cambia**: un toque en el tiempo muestra el que falta y, con
  un primer pulso, el compás y el pulso (al tempo de la cuenta previa, con
  tantos pulsos por compás como golpes tiene); se recuerda.
- **Un menú en cada perilla**: con el dedo quieto sobre una perilla,
  aparecen sus valores de costumbre (las velocidades elegidas; los
  intervalos de 1, 2, 3, 5, 7 y 12 semitonos; pasos de cents, de volumen y
  de decibeles), marcado el de ahora, y Volver al de siempre.
- **La perilla de la velocidad se detiene en las velocidades elegidas**:
  arrastrada, se queda en una de la lista (50 %, 75 %, 100 %…) mientras pasa
  a pocos grados de ella.
- **El valor recién cambiado, en grande sobre la onda**: una velocidad
  cambiada con el teclado, con 1× o con el menú de las velocidades, o una
  altura cambiada con el teclado, se muestra en el medio de la onda por un
  momento, y luego se desvanece.
- **Archivos de Transcribe!** (`.xsc`): menú > Importar .xsc toma sus
  secciones y sus compases con nombre como marcadores, su primer pulso y el
  tempo de sus pulsos, sus bucles, su selección, su velocidad y su
  afinación, que el `.slx` guarda desde entonces. El `.xsc` nombra su
  grabación con una ruta del ordenador, así que la aplicación toma la
  grabación abierta, si tiene ese nombre, o la pide.
- **Marcar** (en los ajustes de la cuenta previa) muestra el tempo marcado
  («120 BPM»), hasta que se abre otro archivo.

- `tools/zenodo.py`: la descripción del depósito es ahora el README entero
  de la etiqueta, en HTML. El borrador recibe además el colaborador (el
  director del proyecto), los idiomas, el copyright, el repositorio en
  GitHub, el lenguaje de programación y el estado del desarrollo; estos
  últimos, por la API de registros de Zenodo, porque la de depósitos no los
  admite. El token puede guardarse en el llavero del escritorio
  (`secret-tool store --label="Zenodo token" service zenodo`) en vez de en
  `$ZENODO_TOKEN`; `tools/zenodo.py check-token` dice si hay uno.
- `tools/zenodo.py`: la descripción del depósito deja fuera la línea que
  lleva a los README en otras lenguas y la sección Screenshots, cuyas
  imágenes Zenodo no muestra.

## 1.2 — 2026-11-25

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
