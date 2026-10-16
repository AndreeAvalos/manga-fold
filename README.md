# MangaFold

Lector de manga para Android pensado para el **OnePlus Open** (y plegables en
general): detecta la postura del pliegue y muestra una o dos páginas según
cómo tengas el teléfono, con una animación de "hoja pasando página" al
deslizar.

## Características

- **Biblioteca local**: añade una carpeta de imágenes (jpg/png/webp) o un
  archivo `.cbz`/`.zip` desde el selector de archivos del sistema (Storage
  Access Framework, sin pedir permisos amplios de almacenamiento).
- **Animación de hoja**: al arrastrar horizontalmente, la página gira en 3D
  desde el borde correspondiente con una sombra dinámica en el pliegue,
  simulando una hoja de papel pasando — no es solo un deslizamiento plano.
  También puedes tocar el tercio izquierdo/derecho de la pantalla para pasar
  de página al instante.
- **Consciente del pliegue**: usa `androidx.window` (Jetpack WindowManager)
  para leer la `FoldingFeature` del dispositivo.
  - Plegado / en postura de libro → una sola página, pantalla completa.
  - Desplegado en postura de "libro" (bisagra vertical) → vista de dos
    páginas (spread), como un cómic físico abierto.
- Zoom y desplazamiento por página (pellizcar para hacer zoom) con
  [Telephoto](https://github.com/saket/telephoto).
- Las páginas se recuerdan entre sesiones (persistencia simple con
  `SharedPreferences` + permisos persistentes de Uri).

## Estructura del proyecto

```
app/src/main/java/com/mangafold/reader/
├── MainActivity.kt            # Navegación simple Biblioteca ↔ Lector
├── data/                      # Modelo, repositorio y extracción de .cbz
├── fold/FoldState.kt          # Detección de la postura del plegable
└── ui/
    ├── theme/                 # Tema Material 3
    ├── library/                # Pantalla de biblioteca
    └── reader/
        ├── PageCurlPager.kt    # La animación de "hoja" (el corazón de la app)
        ├── ReaderScreen.kt
        └── ReaderViewModel.kt
```

## Cómo abrir y ejecutar

Este proyecto se generó como código fuente; **no se compiló ni se probó en
un dispositivo/emulador real** desde este entorno porque no tiene el SDK de
Android ni un emulador disponible. Pasos para probarlo tú:

1. Abre la carpeta `MangaFold/` con **Android Studio** (Koala o más
   reciente recomendado). Al abrirlo, Android Studio generará automáticamente
   el Gradle Wrapper y descargará las dependencias.
2. Deja que termine el "Gradle Sync".
3. Conecta tu OnePlus Open (con depuración USB activada) o usa el emulador de
   plegables de Android Studio (Device Manager → crea un dispositivo con
   perfil "7.6 Fold-in with outer display" o similar) y pulsa **Run**.
4. En la app, toca el botón **+** para añadir una carpeta con imágenes de un
   capítulo, o un archivo `.cbz`/`.zip`.
5. Pliega/despliega el teléfono (o cambia la postura en el emulador con
   *Extended controls → Virtual sensors → Hinge angle*) para ver el cambio
   entre una página y la vista de dos páginas.

### Alternativa: compilar en un contenedor Docker (sin Android Studio)

Si solo quieres generar el APK sin instalar Android Studio, hay un
`Dockerfile` listo en la raíz del proyecto, basado en la imagen
`mingc/android-build-box` (JDK 17 + SDK + Gradle ya preinstalados).

1. Abre **Docker Desktop** (tiene que estar corriendo el daemon).
2. Desde la carpeta `MangaFold/`, ejecuta:
   ```bash
   ./docker-build.sh
   ```
   La primera vez descargará la imagen base (pesa varios GB) y tardará unos
   minutos; las siguientes compilaciones son mucho más rápidas porque el
   script reutiliza un volumen con la caché de Gradle.
3. Al terminar, el APK de debug queda en
   `app/build/outputs/apk/debug/app-debug.apk`, listo para instalar con
   `adb install app/build/outputs/apk/debug/app-debug.apk`.

Este camino solo sirve para **compilar**; no reemplaza el emulador/dispositivo
real para probar la animación de hoja ni la detección del pliegue.

### Requisitos
- Android Studio con Kotlin 1.9.23 / AGP 8.3.2 (o versiones compatibles que
  Android Studio te proponga actualizar automáticamente).
- `compileSdk`/`targetSdk` 34, `minSdk` 26.

## Posibles mejoras futuras
- Animación de curl más realista (deformación de la imagen tipo "cono de
  papel", no solo rotación 3D).
- Soporte para `.rar`/`.cbr` (requiere una librería de descompresión RAR).
- Modo "tabletop" (bisagra horizontal) con la mitad superior como página y la
  inferior como controles, aprovechando la postura de mesa del Open.
- Marcadores de progreso de lectura por manga.
