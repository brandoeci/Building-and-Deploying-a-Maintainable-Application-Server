/*
 * Cliente de la aplicación de ejemplo.
 *
 * Cada acción evita el envío normal del formulario, arma la URL del servicio con
 * los valores escritos por el usuario y la pide con fetch(). La página no se
 * recarga: solo cambia el área de resultado o la de error.
 */

const ui = {};

document.addEventListener("DOMContentLoaded", () => {
  ui.status = document.getElementById("status");
  ui.result = document.getElementById("result");
  ui.error = document.getElementById("error");
  ui.buttons = Array.from(document.querySelectorAll("button"));

  document.getElementById("script-status").textContent =
    "app.js se pidió en su propia petición HTTP y está corriendo en el navegador.";

  document.getElementById("hello-form").addEventListener("submit", (event) => {
    event.preventDefault();
    greet();
  });

  document.getElementById("square-form").addEventListener("submit", (event) => {
    event.preventDefault();
    square();
  });

  document.getElementById("pi-button").addEventListener("click", () =>
    call("Pidiendo PI", "/pi"));

  document.getElementById("env-button").addEventListener("click", () =>
    call("Leyendo la configuración", "/env"));
});

function greet() {
  const name = document.getElementById("name").value.trim();
  const language = document.getElementById("language").value.trim();
  if (name === "") {
    showError("Escriba un nombre antes de pedir el saludo.");
    return;
  }
  let url = "/hello?name=" + encodeURIComponent(name);
  if (language !== "") {
    url += "&language=" + encodeURIComponent(language);
  }
  call("Pidiendo el saludo", url);
}

function square() {
  const value = document.getElementById("value").value.trim();
  if (value === "") {
    showError("Escriba un número antes de pedir el cuadrado.");
    return;
  }
  // Lo demás lo valida el servicio: su respuesta 400 se muestra como mensaje.
  call("Calculando el cuadrado", "/square?value=" + encodeURIComponent(value));
}

/** Pide un servicio y muestra el resultado o el error. */
async function call(label, url) {
  clearAreas();
  setLoading(true, label);
  const startedAt = performance.now();
  try {
    let response;
    try {
      response = await fetch(url);
    } catch (networkFailure) {
      // fetch solo falla así cuando no hubo respuesta HTTP.
      throw new Error("No se pudo contactar el servidor. Verifique que siga encendido.");
    }

    const text = await response.text();
    if (!response.ok) {
      throw new Error(text + " (HTTP " + response.status + ")");
    }

    const elapsed = Math.round(performance.now() - startedAt);
    showResult(text + "\n\n(" + url + " respondió en " + elapsed + " ms)");
  } catch (failure) {
    showError(failure.message);
  } finally {
    setLoading(false, label);
  }
}

function setLoading(isLoading, label) {
  ui.buttons.forEach((button) => {
    button.disabled = isLoading;
  });
  ui.status.textContent = isLoading ? label + "..." : "";
}

function showResult(text) {
  ui.result.textContent = text;
  ui.result.hidden = false;
  ui.error.hidden = true;
}

function showError(message) {
  ui.error.textContent = message;
  ui.error.hidden = false;
  ui.result.hidden = true;
}

function clearAreas() {
  ui.result.hidden = true;
  ui.error.hidden = true;
}
