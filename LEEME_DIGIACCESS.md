# DIGIACCESS PDA: app Android (rostro o cédula)

App basada en el SDK facial HF11 (yunatt), adaptada para DIGIACCESS.
Cada ingreso o salida se valida con **una de dos**: el **rostro** o la **cédula de ciudadanía**.

```
Rostro (SDK en el PDA) ─┐
Cédula amarilla (PDF417)┼─► número de identificación ─► validar_acceso.php ─► autorizaciones ─► INSERT ingresos
Cédula digital (digitada)┘                                (mismas reglas: vigencia, días, lista negra, antipassback)
```

- **En línea:** el PDA envía `identificacion`, `punto`, `tipo` (ENTRADA/SALIDA) y `vehicular` (PEATONAL/VEHICULAR) al mismo `validar_acceso.php` de las demás controladoras, con `formato=lcd`. DIGIACCESS decide y registra en `ingresos`, `ingresosdiarios` y `activos`.
- **Sin conexión:** el PDA decide con la última lista de autorizados vigentes (vigencia, días y permiso vehicular) y guarda la marca. Al volver la red, `pda_sync.php` la registra con la fecha y hora del PDA, sin duplicarla.
- **Sin fotos:** al servidor solo viaja el número de identificación. Los rostros se enrolan en el PDA, y la llave de cada uno es la cédula.

---

## 1. Servidor (5 minutos)

1. Suba `servidor/pda_sync.php` y `servidor/pda_config.php` a la **misma carpeta** de `bdd.php` y `validar_acceso.php`.
2. `pda_config.php` ya trae un token generado para el primer PDA. Para cada PDA adicional, agregue una línea con un token nuevo:
   `php -r 'echo bin2hex(random_bytes(20)), PHP_EOL;'`
3. Si no los tiene, cree estos índices (aceleran la validación y la sincronización):
   ```sql
   CREATE INDEX idx_ident ON autorizaciones (identificacion);
   CREATE INDEX idx_vigencia ON autorizaciones (fechaterminacion);
   CREATE INDEX idx_ident ON listanegra (identificacion);
   CREATE INDEX idx_ident ON activos (identificacion);
   CREATE INDEX idx_ident_fecha ON ingresosdiarios (identificacion, fecha);
   ```
4. La tabla de control `pda_marcas_recibidas` se crea sola la primera vez que un PDA sube marcas.

## 2. Compilar el APK

1. Abra la carpeta `app-android` en **Android Studio**. Si pregunta por actualizar Gradle o el plugin, acepte las versiones que sugiere.
2. Menú **Build › Generate Signed App Bundle / APK › APK**, con la llave `keystore/key.jks` (alias `key0`, la clave está en `app/build.gradle`). También sirve `./gradlew assembleRelease`, que ya firma con esa llave.
3. El resultado es `DIGIACCESS-PDA.apk`.

> **No cambie la llave, el `applicationId` (`com.FaceVerifyM8`) ni los permisos del manifiesto.** La licencia del motor facial está atada al equipo, a la firma de la app y a sus permisos. Si cambia alguno, el código del equipo cambia y yunatt tiene que aprobar una licencia nueva.

## 3. Instalar en el PDA

Antes de instalar, revise qué app trae el PDA y con qué llave está firmada (con depuración USB activada):

```
adb shell pm list packages | grep -i -E "face|yunatt|verify"
adb shell pm path <paquete>                 # ruta del APK instalado
adb pull <ruta>/base.apk original.apk       # guarde una copia
keytool -printcert -jarfile original.apk    # compare la huella SHA256
adb pull /sdcard/FaceSdk  respaldo_FaceSdk  # respaldo de la licencia
```

- La huella SHA-256 de este proyecto es `CB:EF:C6:C0:6F:A0:BD:9E:59:6A:6B:56:37:DD:E9:18:47:CF:93:6A:15:B9:70:2A:CC:B7:A5:79:83:44:99:E1`.
- **Si el paquete original es `com.FaceVerifyM8` con esa misma huella:** `adb install -r DIGIACCESS-PDA.apk` reemplaza la app y conserva la licencia y los rostros.
- **Si la huella es otra:** la licencia actual no sirve para esta app. Al abrirla, DIGIACCESS envía el código del equipo a yunatt (aparece "Licencia pendiente de aprobación") y hay que pedirles que lo aprueben. Mientras tanto, **la validación con cédula funciona igual**: solo el rostro necesita licencia.

## Dos versiones del APK

Cada compilación publica dos archivos. Tienen el mismo paquete y la misma firma: se instala uno encima del otro y la configuración se conserva.

- **DIGIACCESS-PDA.apk** (completa): rostro y cédula. El rostro necesita la licencia del SDK facial.
- **DIGIACCESS-PDA-CEDULA.apk** (solo cédula): no inicia el reconocimiento facial, no pide licencia ni usa la cámara. Úsela mientras llega la licencia.

Descarga directa de la versión solo cédula: `https://github.com/juanpa35/digiaccess-pda/releases/latest/download/DIGIACCESS-PDA-CEDULA.apk`

**Sobre la licencia facial (código del equipo y por qué no se pierde):** el SDK calcula el "código del equipo" a partir de tres cosas: el equipo, la **firma** de la app y el **conjunto de permisos** que pide. Mientras no se restablezca de fábrica el Android, se firme siempre con `key.jks` y no se cambien los permisos, **el código no cambia** (reinstalar la app no lo cambia). La app guarda la licencia en una carpeta **fija** `/sdcard/FaceSdk`; para que viva ahí y sobreviva a reinstalaciones hay que darle el permiso **Acceso a todos los archivos**. Sin ese permiso la licencia queda en una carpeta de la app y se borra al desinstalar.

En las dos versiones, el engranaje de la pantalla principal abre la administración (con clave).

## 4. Lector 2D del PDA

La app funciona con el escáner en **cualquiera de los dos modos de salida**; no es obligatorio usar broadcast.

**Modo teclado (el más fácil):** el escáner "escribe" la lectura y termina con Enter.
1. En la app del escáner (suele llamarse *Scanner*, *ScanSettings*, *Barcode* o *Escáner*), deje la salida en **Teclado / Keyboard / Simular teclado** con **sufijo Enter**.
2. Programe el mismo formato de los lectores DIGIACCESS: saltar 48 caracteres, enviar 10 y Enter. Al PDA llega `0080123456` y la app lo toma como la cédula 80123456.
3. No hay que escribir nada en la configuración de DIGIACCESS.

**Modo broadcast (opcional):** el escáner le pasa la lectura a la app "por debajo", sin escribirla.
1. En la app del escáner busque **Modo de salida / Output mode** y elija **Broadcast**, **Intent** o **API**.
2. Esa misma pantalla muestra dos datos: la **acción** (por ejemplo `android.intent.ACTION_DECODE_DATA`) y el **nombre del dato** o *extra* (por ejemplo `barcode_string`).
3. Si la acción es de las que ya trae la app (Urovo, iData, Newland, Sunmi, Seuic, Honeywell, Zebra, Chainway), no hay que hacer nada más. Si no, copie esos dos datos en DIGIACCESS › "Lector 2D: acción broadcast adicional" y "nombre del dato (extra)" y guarde.
4. Si la pantalla permite escribir la acción, puede poner `com.digiaccess.SCAN` con el extra `barcode_string`.

En los dos modos, active la simbología **PDF417** (cédula amarilla) y **QR** (para avisar que la cédula digital se debe digitar).

## 5. Configurar la app

**Funciones** (ícono de engranaje en la pantalla de verificación; la clave por defecto es `123456`, **cámbiela en Ajustes**) › **DIGIACCESS**:

| Campo | Ejemplo |
|---|---|
| URL de validar_acceso.php | `https://digiaccesstibitoc.com/validar_acceso.php` |
| URL de pda_sync.php | `https://digiaccesstibitoc.com/pda_sync.php` |
| Token del PDA | el de `pda_config.php` |
| Nombre del punto | `PDA Molinete Principal` (queda en `ingresos.punto`) |
| Sentido / carril | ENTRADA o SALIDA; PEATONAL o VEHICULAR |

Luego pulse **Probar conexión** y después **Sincronizar ahora**.

> El tablero de ingresos clasifica como **peatonal** todo punto cuyo nombre contenga "molinete". Si el PDA es peatonal, incluya esa palabra en el nombre del punto.

## 6. Rostros: desde la nube yunatt o en el PDA

**Desde la nube yunatt (recomendado):** el PDA se conecta a `global.yunatt.com` como una terminal más y recibe los rostros que DIGIACCESS ya maneja en yunatt (altas con foto, cambios de nombre, bajas y borrado total).
1. En la nube yunatt, agregue el PDA como dispositivo con un número de serie (SN), por ejemplo `DIGIPDA001`.
2. En el PDA: Funciones › DIGIACCESS › **Nube yunatt (rostros)**. Active "Conectar a la nube yunatt", deje servidor `global.yunatt.com` y puerto `7792`, escriba el mismo SN y pulse **Guardar y conectar a yunatt**. El recuadro debe decir "Conectado a yunatt".
3. Desde yunatt, envíe los usuarios al PDA. El contador "recibidos" sube con cada rostro.
4. **Importante:** el ID (enrollid) del usuario en yunatt debe ser el número de cédula, porque con ese número el PDA valida en DIGIACCESS.
5. El ingreso **siempre** se registra en DIGIACCESS (validar_acceso.php). Enviar también la marca a yunatt es opcional y viene apagado.

**En el PDA:**

- **Uno a uno:** Funciones › Rostros › Nuevo. Escriba la **cédula** (el nombre se llena solo si la persona está en la lista de autorizados) y tome la foto de frente, sin tapabocas.
- **Masivo:** copie fotos al PDA en la carpeta `/LoadUsers` con el nombre `CEDULA_NOMBRE.jpg` (por ejemplo `80123456_JUAN PEREZ.jpg`) y pulse **Cargar** en Funciones. Las que fallan quedan en `/Bad` con el motivo en el nombre.
- Opción **"eliminar rostros sin autorización vigente"**: está desactivada por defecto, porque los rostros solo existen en el PDA (no hay fotos en el servidor) y si una autorización vence por un día, la persona tendría que enrolarse de nuevo. También hay un botón para depurarlos manualmente.

## 7. Uso diario

- **Sentido y carril sin clave:** el operador los cambia sin entrar a administración.
  - En la pantalla principal, con los selectores **Entrada/Salida** y **Peatonal/Vehicular**.
  - En las pantallas de cédula y de rostro, tocando las píldoras de arriba.
  - Cada cambio muestra un aviso corto con el modo nuevo, por ejemplo "Ahora: SALIDA · VEHICULAR".
- **Pantalla principal:** botones **Rostro** y **Cédula**. La cámara solo se enciende al tocar **Rostro** (o el círculo), y se apaga al salir de esa pantalla; así el lector 2D queda libre. En la pantalla de rostro, **Usar cédula** cierra la cámara y pasa a la cédula. (Si prefiere que el PDA detecte el rostro solo, active "Detección automática de rostro" en DIGIACCESS; mantiene la cámara encendida.)
- **Voz en español:** el PDA dice "Bienvenido, Juan", "Hasta luego, Juan", el motivo del rechazo o "Rostro no reconocido". Usa la voz de Android; si no suena, instale "Servicios de voz de Google" y descargue el idioma Español en Ajustes › Idioma › Salida de texto a voz.
- **La cédula se puede leer en cualquier pantalla** (principal, rostro o cédula): el PDA pasa solo a la validación con cédula.
- **Cédula digital:** su QR está cifrado por la Registraduría, así que se usa **Digitar cédula**.
- **Colores:** verde = autorizado; rojo = denegado (sin autorización vigente, fuera de días, sin permiso vehicular o restringido); naranja = antipassback o no se pudo validar.
- **Repeticiones:** si la misma persona sigue frente a la cámara, no se marca otra vez durante 15 segundos.

## 8. Privacidad (Ley 1581 de 2012)

- La biometría facial es un dato sensible: se necesita la autorización expresa de cada persona antes de enrolarla.
- Del código de la cédula, el PDA **solo envía el número**. La fecha de nacimiento y el RH se leen pero no se envían ni se guardan.
- `pda_sync.php` exige token, porque expone nombres e identificaciones. Use HTTPS.

## 9. Qué cambió frente a la demo

- Nuevo paquete `com.digiaccess`, con `Cedula` (lectura del PDF417), `ScannerHelper`, `Acceso` (validar_acceso.php y respaldo sin conexión), `SyncManager`, `LocalStore` y `FaceStore`.
- Nuevas pantallas `CedulaActivity` y `DigiAccessActivity`, e interfaz en español.
- **Diseño DIGIACCESS** tomado de digiaccessoikos.com:
  - **Tipografía y colores:** Inter (`res/font`), fondo con degradado morado `#667EEA → #764BA2`, botón principal con degradado `#3B82F6 → #06B6D4`, y verde, rojo y ámbar para los resultados.
  - **Componentes:** tarjetas blancas de 20 dp y radios de 12 dp.
  - **Marca:** logo oficial en las barras superiores (`drawable-nodpi/da_logo.png`) y el símbolo como ícono de la app.
  - **Dónde cambiarlo:** los colores están en `res/values/colors.xml` y los estilos en `res/values/styles.xml`, con prefijo `Da`.
  - **Licencias:** Inter usa la SIL Open Font License y los íconos son de Tabler Icons (licencia MIT).
- **Pantallas de administración rediseñadas:**
  - Encabezado común con botón atrás y el logo.
  - Funciones en mosaicos, con la conexión DIGIACCESS destacada.
  - Rostros en tarjetas; la selección se ve en azul.
  - Enrolamiento con la cédula primero, para que el nombre se llene solo.
  - Registros con estado verde o rojo y el porcentaje de similitud.
  - Ajustes por secciones con interruptores.
  - Clave de administrador y licencia sobre el fondo morado.
- **Correcciones:**
  - La comparación de dos fotos extraía la segunda plantilla de la primera imagen.
  - El cálculo del segundo mejor puntaje estaba mal.
  - El campo de clave venía prellenado con `123456`.
  - Se protegió la lista de rostros contra cambios simultáneos, para evitar que en una comparación salga la persona equivocada.
- `jcenter()` se reemplazó por `mavenCentral()`, y el build de release ahora firma con la llave del proyecto.
- **Pendiente de probar en el equipo real:** la compilación en Android Studio, la lectura del escáner por broadcast y la licencia. El código Java se verificó compilando contra `android.jar` (API 30), y el lector de cédula y `pda_sync.php` se probaron con datos de ejemplo.
