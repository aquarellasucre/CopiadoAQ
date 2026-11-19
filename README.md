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

La lectura de stock identifica dinámicamente las columnas de tallas a partir de sus valores numéricos, sin limitar los encabezados a números o prefijos de letras específicos. Se excluyen las columnas descriptivas y de totales conocidas. El popup de resultados incluye además un botón circular rojo con una X en la esquina superior derecha para cerrarlo.
