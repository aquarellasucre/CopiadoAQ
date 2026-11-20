// Copiar AQ - API de Registro de Pedidos (pruebas)
// Mantén API_TOKEN igual al token guardado en el secreto de GitHub:
// COPIAR_AQ_PEDIDOS_TOKEN

const API_TOKEN = "COLOCA_AQUI_EL_MISMO_TOKEN";

function doGet() {
  return respuesta({
    ok: true,
    mensaje: "Copiar AQ API activa",
    version: "1.3"
  });
}

function doPost(e) {
  try {
    const datos = JSON.parse(e.postData.contents || "{}");

    if (datos.token !== API_TOKEN) {
      return respuesta({ ok: false, mensaje: "Token no válido" });
    }

    switch (datos.accion) {
      case "ping":
        return respuesta({ ok: true, mensaje: "Conexión correcta con Google Sheets", version: "1.3" });
      case "registrarPedido":
        return registrarPedido(datos);
      case "agregarDetalle":
        return agregarDetalle(datos);
      case "buscarCatalogo":
        return buscarCatalogo(datos);
      default:
        return respuesta({ ok: false, mensaje: "Acción desconocida: " + datos.accion });
    }
  } catch (error) {
    return respuesta({ ok: false, mensaje: error.message });
  }
}

function registrarPedido(datos) {
  if (!datos.Cliente || String(datos.Cliente).trim() === "") {
    return respuesta({ ok: false, mensaje: "El cliente es obligatorio" });
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const hoja = ss.getSheetByName("Pedidos_OH");
    if (!hoja) throw new Error("No existe la pestaña Pedidos_OH");

    const pedidoID = generarPedidoID();
    hoja.appendRow([
      pedidoID,
      new Date(),
      String(datos.Cliente).trim(),
      datos.TipoVenta || "",
      datos.Estado || "Solicitado",
      datos.Descripcion || "",
      0,
      datos.UsuarioRegistro || ""
    ]);

    return respuesta({
      ok: true,
      mensaje: "Pedido registrado correctamente",
      PedidoID: pedidoID,
      Cliente: String(datos.Cliente).trim(),
      Estado: datos.Estado || "Solicitado",
      TotalPedido: 0
    });
  } finally {
    lock.releaseLock();
  }
}

function agregarDetalle(datos) {
  const pedidoID = String(datos.PedidoID || "").trim();
  const articulo = String(datos.Articulo || "").trim();
  const cantidad = Number(datos.Cantidad);
  const precio = Number(datos.Precio);
  const descuento = Number(datos.Descuento || 0);

  if (!pedidoID) return respuesta({ ok: false, mensaje: "PedidoID es obligatorio" });
  if (!articulo) return respuesta({ ok: false, mensaje: "El artículo es obligatorio" });
  if (!Number.isFinite(cantidad) || cantidad <= 0) return respuesta({ ok: false, mensaje: "Cantidad debe ser mayor a cero" });
  if (!Number.isFinite(precio) || precio < 0) return respuesta({ ok: false, mensaje: "Precio no válido" });
  if (!Number.isFinite(descuento) || descuento < 0) return respuesta({ ok: false, mensaje: "Descuento no válido" });

  const bruto = cantidad * precio;
  if (descuento > bruto) return respuesta({ ok: false, mensaje: "El descuento no puede superar el importe de la línea" });
  const subtotal = Math.round((bruto - descuento) * 100) / 100;

  const lock = LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const hojaOH = ss.getSheetByName("Pedidos_OH");
    const hojaOD = ss.getSheetByName("Pedidos_ODs");
    if (!hojaOH) throw new Error("No existe la pestaña Pedidos_OH");
    if (!hojaOD) throw new Error("No existe la pestaña Pedidos_ODs");

    const ultimaOH = hojaOH.getLastRow();
    if (ultimaOH < 2) throw new Error("No hay pedidos registrados");
    const ids = hojaOH.getRange(2, 1, ultimaOH - 1, 1).getValues().flat();
    const indice = ids.findIndex(id => String(id).trim() === pedidoID);
    if (indice === -1) throw new Error("No se encontró el pedido " + pedidoID);
    const filaOH = indice + 2;

    // Si el mismo código ya existe en este pedido, acumula la cantidad en esa fila.
    // La comparación es por PedidoID + Articulo, sin distinguir mayúsculas/minúsculas.
    const ultimaODAntes = hojaOD.getLastRow();
    let pedidoOD_ID = "";
    let cantidadFinal = cantidad;
    let subtotalFinal = subtotal;
    let seAcumulo = false;

    if (ultimaODAntes >= 2) {
      const existentes = hojaOD.getRange(2, 1, ultimaODAntes - 1, 9).getValues();
      const filaExistente = existentes.findIndex(fila =>
        String(fila[1]).trim() === pedidoID &&
        String(fila[2]).trim().toLowerCase() === articulo.toLowerCase()
      );

      if (filaExistente >= 0) {
        const numeroFila = filaExistente + 2;
        const fila = existentes[filaExistente];
        const cantidadAnterior = Number(fila[5]) || 0;
        const precioAnterior = Number(fila[6]) || precio;
        const descuentoAnterior = Number(fila[7]) || 0;
        cantidadFinal = cantidadAnterior + cantidad;
        const descuentoFinal = descuentoAnterior + descuento;
        subtotalFinal = Math.round((cantidadFinal * precioAnterior - descuentoFinal) * 100) / 100;
        if (subtotalFinal < 0) throw new Error("El descuento acumulado supera el importe de la línea");

        pedidoOD_ID = String(fila[0] || "");
        hojaOD.getRange(numeroFila, 6).setValue(cantidadFinal);
        hojaOD.getRange(numeroFila, 8).setValue(descuentoFinal);
        hojaOD.getRange(numeroFila, 9).setValue(subtotalFinal);
        seAcumulo = true;
      }
    }

    if (!seAcumulo) {
      pedidoOD_ID = "POD-" + Utilities.getUuid().replace(/-/g, "").substring(0, 12).toUpperCase();
      hojaOD.appendRow([
        pedidoOD_ID,
        pedidoID,
        articulo,
        String(datos.Descripcion || "").trim(),
        String(datos.Talla || "").trim(),
        cantidad,
        precio,
        descuento,
        subtotal
      ]);
    }

    // Recalcula el total sumando todos los subtotales guardados para este PedidoID.
    const ultimaOD = hojaOD.getLastRow();
    let total = 0;
    if (ultimaOD >= 2) {
      const filas = hojaOD.getRange(2, 2, ultimaOD - 1, 8).getValues();
      filas.forEach(fila => {
        if (String(fila[0]).trim() === pedidoID) {
          total += Number(fila[7]) || 0; // Subtotal, columna I
        }
      });
    }
    total = Math.round(total * 100) / 100;
    hojaOH.getRange(filaOH, 7).setValue(total); // Total, columna G

    return respuesta({
      ok: true,
      mensaje: "Artículo agregado al pedido",
      PedidoID: pedidoID,
      PedidoOD_ID: pedidoOD_ID,
      SubtotalLinea: subtotalFinal,
      CantidadLinea: cantidadFinal,
      Acumulado: seAcumulo,
      TotalPedido: total
    });
  } finally {
    lock.releaseLock();
  }
}


// Busca artículos en la pestaña Catalogo.
// Prioriza coincidencias exactas en "Artículo" y luego busca coincidencias parciales
// en Artículo, BRV_DESCRIPTION y Referencia.
function buscarCatalogo(datos) {
  const consulta = String(datos.consulta || datos.Articulo || "").trim().toLowerCase();
  if (!consulta) return respuesta({ ok: false, mensaje: "Ingresa un código o texto para buscar" });

  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const hoja = ss.getSheetByName("Catalogo");
  if (!hoja) throw new Error("No existe la pestaña Catalogo");

  const ultimaFila = hoja.getLastRow();
  const ultimaColumna = hoja.getLastColumn();
  if (ultimaFila < 2) return respuesta({ ok: true, resultados: [] });

  const encabezados = hoja.getRange(1, 1, 1, ultimaColumna).getDisplayValues()[0]
    .map(h => String(h).trim().toLowerCase());
  const filas = hoja.getRange(2, 1, ultimaFila - 1, ultimaColumna).getDisplayValues();

  const col = nombre => encabezados.indexOf(nombre.toLowerCase());
  const cArticulo = col("Artículo") >= 0 ? col("Artículo") : col("Articulo");
  const cPrecio = col("Precio");
  const cSock = col("SockID_D");
  const cStock = col("StockID");
  const cFecha = col("Fecha");
  const cCatSup = col("CATEGORIA_SUP");
  const cCategoria = col("CATEGORIA");
  const cSubcategoria = col("SUBCATEGORIA");
  const cDescripcion = col("BRV_DESCRIPTION");
  const cReferencia = col("Referencia");

  if (cArticulo < 0) throw new Error('No se encontró la columna "Artículo" en Catalogo');
  if (cPrecio < 0) throw new Error('No se encontró la columna "Precio" en Catalogo');

  const resultados = [];
  filas.forEach(fila => {
    const articulo = String(fila[cArticulo] || "").trim();
    const descripcion = cDescripcion >= 0 ? String(fila[cDescripcion] || "").trim() : "";
    const referencia = cReferencia >= 0 ? String(fila[cReferencia] || "").trim() : "";
    const exacto = articulo.toLowerCase() === consulta;
    const coincide = exacto ||
      articulo.toLowerCase().includes(consulta) ||
      descripcion.toLowerCase().includes(consulta) ||
      referencia.toLowerCase().includes(consulta);

    if (coincide) {
      const precioTexto = String(fila[cPrecio] || "").replace(",", ".");
      resultados.push({
        SockID_D: cSock >= 0 ? fila[cSock] : "",
        StockID: cStock >= 0 ? fila[cStock] : "",
        Fecha: cFecha >= 0 ? fila[cFecha] : "",
        Articulo: articulo,
        Precio: Number(precioTexto) || 0,
        CATEGORIA_SUP: cCatSup >= 0 ? fila[cCatSup] : "",
        CATEGORIA: cCategoria >= 0 ? fila[cCategoria] : "",
        SUBCATEGORIA: cSubcategoria >= 0 ? fila[cSubcategoria] : "",
        BRV_DESCRIPTION: descripcion,
        Referencia: referencia,
        coincidenciaExacta: exacto
      });
    }
  });

  resultados.sort((a, b) => Number(b.coincidenciaExacta) - Number(a.coincidenciaExacta));
  return respuesta({ ok: true, resultados: resultados.slice(0, 50), totalCoincidencias: resultados.length });
}

function generarPedidoID() {
  const fecha = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyyMMdd");
  const aleatorio = Utilities.getUuid().replace(/-/g, "").substring(0, 8).toUpperCase();
  return "PED-" + fecha + "-" + aleatorio;
}

function respuesta(datos) {
  return ContentService.createTextOutput(JSON.stringify(datos))
    .setMimeType(ContentService.MimeType.JSON);
}
