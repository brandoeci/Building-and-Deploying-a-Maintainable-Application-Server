# Evidencias y pruebas

Ejecución local del artefacto empaquetado (`java -jar tdse-webframework.jar`),
29 de septiembre de 2026.

* Transcripción completa de `curl`: [local-evidence.txt](local-evidence.txt)
* Peticiones vistas en el navegador: [network-requests.txt](network-requests.txt)
* Capturas: [img/](img)

## 1. Pruebas automáticas

```
$ mvn test
...
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Clase | Pruebas | Qué verifica |
|---|---|---|
| `ServerIntegrationTest` | 11 | Rutas lambda, valores del query, tipo de contenido, página y recursos estáticos, imagen byte a byte, 404, 405, lambda que falla, petición malformada, muchas peticiones seguidas y apagado ordenado |
| `RequestTest` | 7 | Línea de petición, varios parámetros, parámetro ausente, decodificación, línea malformada |
| `RouterTest` | 7 | Registro y búsqueda de lambdas, método como parte de la llave, barra final, re-registro, validaciones |
| `StaticFileServiceTest` | 7 | Archivo de bienvenida, binarios, archivo inexistente, rutas inseguras, carpeta configurable |
| `WebFrameworkTest` | 5 | La API `staticfiles()`, `get()`, el puerto tomado de `PORT` |

## 2. Lista de verificación del enunciado

| Requisito | Resultado | Evidencia |
|---|---|---|
| El proyecto compila con Maven | `BUILD SUCCESS` | sección 1 |
| Sirve HTML, CSS, JavaScript y una imagen | 200 con el tipo correcto en cada uno | [01](img/01-home-page.png), [local-evidence.txt](local-evidence.txt) §2 |
| Al menos dos rutas GET con lambda | `/hello`, `/pi`, `/square`, `/time`, `/env` | [02](img/02-hello-service.png), [03](img/03-pi-service.png) |
| Se leen valores del query string | `/hello?name=Pedro` → `Hello Pedro` | [local-evidence.txt](local-evidence.txt) §3 |
| Varios parámetros | `/hello?name=Pedro&language=es` → `Hola Pedro` | [local-evidence.txt](local-evidence.txt) §3 |
| Un parámetro que falta no tumba el servidor | `/hello` → `Hello world`; `/square` → 400 con mensaje | [local-evidence.txt](local-evidence.txt) §3 y §4 |
| Un recurso desconocido responde 404 | `/unknown` → `404 Not Found` en `text/plain` | [06](img/06-404-unknown-route.png) |
| El puerto se lee de `PORT` | Arrancó en 9090 con `PORT=9090` | [07](img/07-env-in-production.png) |
| Al menos una variable de ambiente adicional | `GREETING_PREFIX`, `APP_ENV`, `STATIC_FILES_PATH` | [07](img/07-env-in-production.png) |
| `/shutdown` apaga el servidor localmente | Responde 200 y luego *Server stopped gracefully* | [local-evidence.txt](local-evidence.txt) §5 |
| El servidor sigue siendo secuencial | Un `accept()` a la vez, sin hilos en el código de la aplicación | `HttpServer.acceptLoop()` |
| `/shutdown` no está publicado en producción | `APP_ENV=production` → `/shutdown` responde 404 | [08](img/08-shutdown-404-in-production.png) |

## 3. Capturas

| Evidencia | Captura |
|---|---|
| Página de la aplicación con sus dos imágenes y el script corriendo | ![Inicio](img/01-home-page.png) |
| `/hello?name=Pedro` sin recargar la página | ![Hello](img/02-hello-service.png) |
| `/pi` | ![Pi](img/03-pi-service.png) |
| `/square?value=abc` → 400 convertido en mensaje | ![Error](img/04-square-error.png) |
| `/env` en desarrollo | ![Env](img/05-env-service.png) |
| Ruta desconocida → 404 | ![404](img/06-404-unknown-route.png) |
| Variables de ambiente en producción (`PORT=9090`) | ![Env produccion](img/07-env-in-production.png) |
| `/shutdown` no existe en producción | ![Shutdown 404](img/08-shutdown-404-in-production.png) |

## 4. Apagado ordenado

```
$ curl -i http://localhost:8080/shutdown
HTTP/1.1 200 OK
Content-Type: text/plain; charset=utf-8
Content-Length: 37

Server will stop after this response.
```

Log del servidor:

```
GET /shutdown -> 200 text/plain; charset=utf-8 (37 bytes, 0 ms)
Server stopped gracefully.
```

El orden importa: la lambda solo apaga la bandera, la respuesta se escribe
completa, el socket del cliente se cierra normalmente, el bucle termina porque
`while (running)` ya es falso, y al final se cierra el `ServerSocket`. El
proceso salió con código 0, sin matar la petición que estaba atendiendo.

La prueba `shutdownIsGraceful` deja esto fijado: verifica que la respuesta
llegó completa y que después el hilo del bucle terminó.

## 5. Pendiente

| Evidencia | Estado |
|---|---|
| Aplicación corriendo en una URL pública | Pendiente: la cuenta de AWS del curso no tiene créditos disponibles. El `Dockerfile` y el procedimiento están en el README, y el mismo artefacto ya se verificó localmente en modo `production`. |
| Captura del panel *Network* de las herramientas de desarrollo | La lista de peticiones está en [network-requests.txt](network-requests.txt); la captura se puede tomar con F12 → Network recargando la página. |
