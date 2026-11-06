# Música (coche)

Reproductor de música para la unidad Witson TS10, con el mismo estilo que la app Radio.

- Fuentes: USB (varios: USB1, USB2…), tarjeta SD, memoria interna (carpeta Music) y Bluetooth (abre la app de fábrica).
- Biblioteca por carpetas (lectura directa) y por artistas, álbumes, canciones y favoritas (índice propio en segundo plano).
- Recuerda la canción y el segundo de cada fuente.
- Colores según las luces del coche: contraste alto con luces apagadas, colores oscuros con luces encendidas.

## Compilar

GitHub la compila sola al subir cambios (pestaña **Actions**) y publica `Musica.apk` en **Releases**.

Necesita el archivo `app/radio.keystore` copiado del repositorio de la Radio, para que todas las apps
del coche lleven la misma firma.
