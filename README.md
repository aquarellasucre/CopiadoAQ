# Copiar AQ — aplicación auxiliar Android

Aplicación mínima para recibir texto mediante el enlace `aquacopy://copy?text=...`,
copiarlo al portapapeles de Android y cerrarse inmediatamente.

## Requisitos
- Android 6.0 o superior (minSdk 23).
- No requiere Internet ni permisos de almacenamiento.

## Crear el APK mediante GitHub Actions
1. Crea un repositorio nuevo en GitHub, por ejemplo `CopiarAQ`.
2. Sube el contenido completo de este proyecto a la rama `main`.
3. Abre la pestaña **Actions** del repositorio.
4. Espera a que termine **Construir APK Copiar AQ**.
5. En el resultado de la ejecución, descarga el artefacto `Copiar-AQ-debug-apk`.
6. Descomprime el ZIP descargado. Dentro estará `app-debug.apk`.
7. Instala el APK en el teléfono y acepta la instalación desde esa fuente si Android lo solicita.

## Probar manualmente
Después de instalar, abre en el teléfono este enlace:
`aquacopy://copy?text=66110874`

Debe copiar `66110874`, mostrar una confirmación breve y cerrarse.

## Configuración en AppSheet
Crea una acción:
- For a record of this table: la tabla que contiene `[Articulo]`
- Do this: **External: go to a website**
- Target:

```appsheet
CONCATENATE(
  "aquacopy://copy?text=",
  ENCODEURL([Articulo])
)
```

- Position: **Inline**
- Icono: el que prefieras (por ejemplo, copiar).

## Notas
- El identificador de la aplicación es `bo.aquarella.copiar`.
- El esquema de enlace es `aquacopy`.
- Esta es una compilación de depuración para pruebas. No está firmada como versión de distribución.
- Android puede mostrar su propio aviso de portapapeles. La aplicación no puede suprimir ese aviso del sistema.
- La apertura de esquemas personalizados desde AppSheet depende de que Android permita resolver el enlace a esta aplicación. Hay que probarlo en el dispositivo real.

- Versión con firma permanente

## Módulo Consulta de Stock (rama `consulta-stock`)

Se añadió una pantalla interna para consultar el stock desde el sitio oficial de Aquarella:

1. Desde la pantalla de Plantillas, pulsa **Consultar stock Aquarella**.
2. Inicia sesión en el formulario oficial dentro de Copiar AQ.
3. Pulsa **Ir a stock**.
4. Introduce el código del artículo y pulsa el botón **Consultar** de la página.
5. Pulsa **Leer tabla** en la barra superior de Copiar AQ para mostrar el contenido de `gvStock`.
6. Desde el diálogo puedes copiar el resultado.

La pantalla utiliza el `WebView` y las cookies persistentes de Android para mantener la sesión. No guarda el usuario ni la contraseña. La consulta sigue ejecutándose mediante el formulario oficial de Aquarella (incluido su postback ASP.NET UpdatePanel); Copiar AQ solo lee la tabla ya actualizada.

El workflow de GitHub Actions ahora también se activa con cambios en `consulta-stock`. Esta entrega es el código fuente integrado; debe compilarse desde GitHub Actions y probarse con una cuenta real antes de considerarlo terminado.


## Imágenes de artículos en Leer data

Al abrir el resultado de **Leer data**, Copiar AQ intenta cargar la imagen del artículo desde el repositorio público `aquarellasucre/AQ2026`, carpeta `JPG`, usando el código del artículo y la extensión `.jpeg`. La imagen se muestra proporcionalmente encima del selector de vistas. Si no existe o no hay conexión, permanece oculta y los resultados de stock siguen funcionando normalmente. Las imágenes se descargan en tiempo de ejecución y no se incorporan a la APK.

## Reconocimiento dinámico de tallas y cierre del resultado

La lectura de stock identifica dinámicamente las columnas de tallas sin limitar los encabezados a números o prefijos de letras específicos. Primero excluye columnas descriptivas conocidas y analiza sus valores. Cuando Aquarella proporciona una fila `TOTALES`, intenta validar cada columna candidata comparando la suma de las tiendas con el total de esa columna; si al menos una columna se valida, utiliza las columnas validadas. Si no hay fila de totales o no se valida ninguna columna, mantiene el reconocimiento dinámico por valores para evitar bloquear la lectura. El popup incluye un botón circular de cierre con acabado moderno, rojo cálido, borde sutil y efecto táctil.

## Mejoras de sesión y menú de Consulta Stock

- La pantalla principal ya no muestra los botones **Inicio** y **Consulta** por separado.
- Un menú lateral izquierdo se abre con el botón ☰ o deslizando desde el borde izquierdo.
- El menú incluye **Inicio de Aquarella**, **Consulta Stock** y **Credenciales**.
- Cuando la sesión de Aquarella expira y el WebView llega a `LoginForm.aspx`, aparece un formulario nativo con usuario y contraseña.
- Las credenciales se pueden guardar cifradas con AndroidX Security / Android Keystore; el formulario las precarga en próximos inicios.
- Desde el menú **Credenciales** se pueden actualizar o eliminar las credenciales guardadas.
- Al enviar el formulario, Copiar AQ intenta completar el formulario web de Aquarella y activar su botón de acceso. Si no identifica los campos, informa al usuario para completar el login en la página oficial.

**Pruebas recomendadas en el teléfono:** (1) abrir Consulta Stock con sesión vigente; (2) probar con sesión caducada; (3) guardar credenciales y verificar que se precarguen en la siguiente ocasión; (4) probar credenciales incorrectas y corregirlas; (5) abrir Inicio y Consulta desde el menú lateral; (6) comprobar el gesto de deslizar desde la izquierda.

La compilación de este ZIP no se ha ejecutado localmente en este entorno, por lo que se debe verificar mediante Android Studio o GitHub Actions antes de instalar.


### Ajustes de prueba posteriores
- El menú lateral ya no tiene botón visible en la pantalla principal; se abre deslizando desde el borde izquierdo.
- Tras un login exitoso, la app intenta abrir/preparar directamente el formulario Consulta Stock y evita exigir que el usuario navegue manualmente a «Consulta».


## Dos espacios independientes de Aquarella (noviembre 2026)

- **Consulta Stock** conserva su WebView especializado y su automatización de consulta/lectura de resultados.
- **Crear Pedido** abre `https://aq.coreva.com.bo/aquarella/Aquarella/Logistica/panelOrdersCustomer.aspx` en un segundo WebView.
- **Navegar en Aquarella** vuelve al segundo WebView sin recargar la página actual.
- Ambos WebView permanecen creados durante la sesión de la Activity; cambiar de módulo solo alterna su visibilidad y conserva el historial de cada uno.
- Ambos utilizan el `CookieManager` de Android WebView, por lo que comparten las cookies de sesión de Aquarella. La persistencia/validez real de la sesión depende del servidor.
- El botón de menú no aparece en la pantalla principal: se conserva el acceso lateral mediante el gesto desde el borde izquierdo.
- El botón Atrás navega por el historial del WebView que está visible.

### Pruebas recomendadas

1. Abrir Consulta Stock y realizar una consulta; cambiar a Crear Pedido y comprobar que carga el módulo de pedidos.
2. Navegar a otra sección desde el segundo WebView; volver a Consulta Stock y comprobar que la consulta sigue en su página.
3. Volver a Crear Pedido y comprobar que conserva la sección visitada.
4. Iniciar sesión desde cualquiera de los dos módulos y verificar que el otro reconoce la sesión compartida.
5. Cerrar/expirar la sesión y probar el formulario de credenciales desde ambos módulos.
6. Usar Atrás en cada módulo y comprobar que retrocede en el historial correspondiente.

**Nota de compilación:** este entorno no dispone de Gradle/Android SDK; el proyecto debe compilarse mediante el flujo Android Studio o GitHub Actions antes de instalar el APK.
