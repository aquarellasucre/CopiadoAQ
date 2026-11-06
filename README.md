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
