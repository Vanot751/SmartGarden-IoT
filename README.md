# 🌿 SmartGarden - Pasarela Segura de Monitoreo y Control Botánico

**SmartGarden** es una aplicación móvil nativa para Android diseñada como pasarela de supervisión, telemetría ambiental en tiempo real y control remoto de actuadores para un **Jardín Inteligente Autónomo**.

El proyecto traslada al ecosistema móvil y en la nube una **arquitectura IoT de modelo emisor-receptor** originalmente instrumentada sobre dos microcontroladores **Arduino UNO (ATmega328P)** interconectados por bus serial asíncrono (`UART TX/RX`), donde:
- **Nodo Emisor (Sensor):** Adquiere de forma continua las variables analógicas críticas del cultivo (temperatura ambiente, humedad de sustrato y radiación lumínica) y empaqueta la telemetría en tramas estructuradas (`T,L,H`).
- **Nodo Receptor (Actuador) y Pasarela Cloud:** Procesa las lecturas en tiempo real, ejecuta reglas deterministas autónomas y sincroniza su estado de forma bidireccional con múltiples terminales Android a través de **Firebase Realtime Database (RTDB)**.

---

## 🏗️ Arquitectura de Software

La aplicación ha sido construida siguiendo las mejores prácticas de ingeniería de software moderno para Android:

- **Lenguaje:** **Kotlin** con uso intensivo de corrutinas y flujos reactivos (`StateFlow` / `MutableStateFlow`).
- **Patrón Arquitectónico (MVVM - Model-View-ViewModel):**
  - **Model / Estado Inmutable (`FloraSenseUiState`):** Representa una única fuente de verdad (*Single Source of Truth*) con el estado de sesión, telemetría de sensores, estado de actuadores, modo oscuro OLED y banderas de alerta crítica.
  - **ViewModel (`FloraSenseViewModel`):** Orquesta la lógica de negocio, el registro y autenticación con Firebase Auth, el control de temas y los *listeners* permanentes de lectura/escritura hacia Firebase Realtime Database.
  - **View (`LoginScreen`, `DashboardScreen`, `ControlPanelScreen`):** Interfaz 100% declarativa en **Jetpack Compose (Material 3)** articulada mediante **Jetpack Navigation Compose** (`AppNavigation`).
- **Sistema de Temas Dual (Pastel Minimalism + True Black OLED):**
  - **Modo Claro ("Pastel Minimalism"):** Diseñado para eliminar la fatiga visual mediante superficies cálidas libres de blancos puros (`#FFFFFF`). Lienzo gris perlado cálido (`#F5F5F0`) y contenedores arena (`#EBEBE4`).
  - **Modo Oscuro OLED ("True Black `#000000`"):** Optimizado para pantallas AMOLED/OLED apagando los píxeles negros por completo para maximizar el ahorro de batería en dispositivos móviles. Utiliza tonos de superficie oscura (`#121212`) con acentos neón de alto contraste.
  - **Conmutador Sol ☀️ / Luna 🌙:** Botón reactivo presente en las cabeceras de todas las pantallas para cambiar instantáneamente entre el modo claro pastel y el modo oscuro OLED, con persistencia en `SharedPreferences`.
  - **Codificación Semántica Pastel / Neón:** Naranja melocotón para temperatura, celeste agua para humedad de suelo y amarillo mantequilla para radiación solar.
  - **Acentos Operativos y Críticos:** Verde esmeralda (`#2ECC71`) para actuadores activos y rojo sólido (`#E74C3C`) reservado estrictamente para alertas críticas y parada de emergencia.

```text
app/src/main/java/com/example/smartgarden/
├── MainActivity.kt          # Punto de entrada, NotificationChannel, paleta Pastel + OLED True Black
├── AppNavigation.kt         # NavHost, auto-login, monitoreo de alertas y navegación
├── FloraSenseViewModel.kt   # Estado StateFlow, auth Firebase, RTDB y conmutación de Modo Oscuro
├── NotificationHelper.kt    # Notificaciones push locales de alta prioridad (Temp > 35°C y Humedad)
├── LoginScreen.kt           # Login/Registro, casilla "Recordar operador", ISO 27400 y Sol/Luna
├── DashboardScreen.kt       # Perfiles botánicos, tarjetas pastel/OLED, simulador y conmutador Sol/Luna
└── ControlPanelScreen.kt    # Interruptores de hardware, dosificador (50/100/250ml), E-STOP e insignia RTDB
```

---

## 🌐 Interconexión IoT y Sincronización en Tiempo Real (Requisito Académico)

Para cumplir con el requisito de **conectividad inalámbrica multi-dispositivo en tiempo real**, **SmartGarden** implementa **Firebase Realtime Database (RTDB)** apuntando al nodo centralizado `/florasense/gardens/nodo_central_01`:

1. **Lectura Reactiva Permanente (`ValueEventListener`):**
   - Al inicializarse el `FloraSenseViewModel`, se registra un `addValueEventListener` permanente sobre la referencia de la base de datos.
   - Cualquier cambio en las lecturas de los sensores o en el estado de los actuadores realizado desde un **Dispositivo #1**, un **Dispositivo #2** o la pasarela del microcontrolador dispara instantáneamente el callback `onDataChange(snapshot: DataSnapshot)`, actualizando el `StateFlow` y recomponiendo la UI en milisegundos sin necesidad de recargar la pantalla (*zero polling*).
2. **Escritura Bidireccional Instantánea (`.setValue()` / `.updateChildren()`):**
   - Las acciones manuales del operador (encender/apagar la bomba de agua, activar el nebulizador, seleccionar la dosis de `50ml`, `100ml` o `250ml`, o accionar la Parada de Emergencia) escriben directamente sobre los subnodos en la nube.
3. **Insignia Horizontal de Estado (`Pill Badge`):**
   - Incorpora indicadores horizontales de estado de la conexión RTDB (`● RTDB ACTIVO`, `● BLOQUEADO`) diseñados para mantenerse siempre legibles y horizontales en pantalla.

### Estructura JSON en Firebase Realtime Database

```json
{
  "florasense": {
    "gardens": {
      "nodo_central_01": {
        "sensores": {
          "temperatura": 26.4,
          "humedadSuelo": 50,
          "luzSolar": 78
        },
        "actuadores": {
          "bombaAgua": true,
          "nebulizador": false,
          "dosisRiegoMl": 100,
          "paradaEmergencia": false
        }
      }
    }
  }
}
```

---

## 🔔 Sistema de Notificaciones Ambientales y Permisos Dinámicos (Android 13+)

**SmartGarden** integra un subsistema de alertas locales de alta prioridad (`NotificationHelper.kt`) para notificar inmediatamente al operador ante variaciones críticas de la parcela:

- **Manejo de Permisos Dinámicos (`POST_NOTIFICATIONS`):** Solicitud automática del permiso dinámico en Android 13+ (API 33+) al abrir la aplicación, junto con la creación del canal `NotificationChannel` con prioridad `IMPORTANCE_HIGH`.
- **🔥 Alerta Crítica de Temperatura:** Se dispara automáticamente en la barra de notificaciones del sistema si la temperatura ambiente supera los **`35.0°C`**.
- **💧 Alerta Crítica de Humedad del Suelo:** Se dispara de forma dinámica si la humedad desciende por debajo del umbral mínimo del perfil botánico seleccionado (*Todas: <30%, Tropicales: <35%, Suculentas: <20%, Aromáticas: <30%*).
- **Control de Falsos Positivos:** El subsistema de alertas evalúa los parámetros únicamente cuando la conexión en tiempo real con Firebase RTDB está verificada (`isRealtimeConnected == true`) y con valores por defecto neutros, garantizando que no existan notificaciones erróneas al abrir la aplicación.

---

## 🔐 Seguridad y Cumplimiento ISO/IEC 27400

El diseño de **SmartGarden** aborda directamente las brechas de seguridad identificadas en sistemas IoT tradicionales, alineándose con los principios de la norma **ISO/IEC 27400 (Seguridad y Privacidad en el Internet de las Cosas)**:

- **🔒 Confidencialidad (Mitigación de *Sniffing*):**
  - Toda la telemetría ambiental y los comandos de actuación viajan entre los dispositivos Android y los servidores de Firebase sobre canales cifrados mediante **TLS 1.3** y cifrado en reposo **AES-256-GCM**, impidiendo la interceptación en texto plano de la trama `(T,L,H)`.
- **🛡️ Integridad y Control de Acceso (Mitigación de *Spoofing* e Inyección de Tramas):**
  - Se integra **Firebase Authentication** (`signInWithEmailAndPassword` / `createUserWithEmailAndPassword`) como barrera obligatoria antes de acceder al Dashboard o al Panel de Control.
  - **Doble Confirmación de Registro:** El registro de usuarios exige doble confirmación de correo electrónico y contraseña para prevenir errores tipográficos.
  - **Casilla "Recordar Operador" y Auto-Login:** Incorpora una casilla para recordar las credenciales del operador en `SharedPreferences`. Si la sesión de Firebase Auth se mantiene activa y la opción está marcada, la aplicación redirige de forma segura e instantánea al Dashboard.
  - **Reglas de Seguridad en Firebase RTDB:** Solo operadores autenticados con token válido pueden emitir escrituras hacia `/actuadores`, evitando inyecciones no autorizadas.
- **⚡ Disponibilidad y *Fail-Safe* Operativo (Mitigación de Denegación de Servicio / Estrés Térmico):**
  - Incorpora un módulo prioritario de **`PARADA DE EMERGENCIA (E-STOP)`** que actúa como *override* manual en la nube, enviando una actualización atómica (`paradaEmergencia = true`, `bombaAgua = false`, `nebulizador = false`) para cortar inmediatamente la energía de los actuadores ante anomalías térmicas (`> 35°C`) o fallos hidráulicos.

---

## ⚙️ Simulación e Integración Lógica de Hardware

La aplicación modela fielmente el comportamiento electrónico del prototipo físico y sus umbrales deterministas:

### 📡 Subsistemas de Censado (Entradas Analógicas)
| Componente Físico | Canal / Pin | Variable | Regla Lógica en SmartGarden |
| :--- | :---: | :--- | :--- |
| **Sensor de Temperatura `TMP36`** | `Pin A0` | Temperatura Ambiente (`°C`) | Escala lineal `10 mV/°C`. Si supera el umbral crítico (**`> 35.0°C`**), dispara alerta visual roja y notificación push de temperatura crítica. |
| **Fotorresistencia `LDR` (`10 kΩ`)** | `Pin A1` | Radiación Solar (`%`) | Divisor de tensión (`0V - 5V`) que monitorea el fotoperíodo según el perfil botánico (*Tropicales, Suculentas, Aromáticas*). |
| **Higrómetro / Potenciómetro** | `Pin A2` | Humedad del Suelo (`%`) | Si desciende por debajo del umbral mínimo del perfil (**`< 30%`**), muestra advertencia de sequía, dispara notificación push de humedad y activa la bomba. |

### 🔌 Subsistemas de Actuación (Salidas Digitales / PWM)
- **💧 Motor de Corriente Continua - Bomba de Agua (`Pin D8`):** Conmutado mediante transistor NPN / módulo relé. Permite control manual (`ON/OFF`), activación automática cuando la humedad es `< 30%` y dosificación calibrada por volumen (**`50ml`**, **`100ml`**, **`250ml`**).
- **🌫️ Nebulizador Ultrasónico (`Relé Auxiliar`):** Control de microclima y humedad foliar sincronizado en tiempo real entre dispositivos.
- **🚨 Transductor Piezoeléctrico (`Pin D9 PWM`):** Alarma acústica activada automáticamente mediante modulación por ancho de pulso cuando la temperatura supera los **`35°C`**.

---

## 🚀 Requisitos y Ejecución del Proyecto

1. **Requisitos Previos:**
   - Android Studio (con soporte para AGP `9.5.0` y Kotlin `2.4.21`).
   - JDK 11 o superior.
   - Archivo `app/google-services.json` configurado con el proyecto de Firebase (`florasense-10759`).
2. **Compilación e Instalación:**
   ```bash
   ./gradlew :app:assembleDebug
   ```
3. **Pruebas y Demostración:**
   - **Autenticación:** Registra o inicia sesión con un usuario y prueba la casilla *"Recordar operador en este dispositivo"*.
   - **Modo Oscuro OLED:** Presiona el botón con icono de **Sol ☀️ / Luna 🌙** en cualquier pantalla para cambiar a la interfaz True Black (`#000000`).
   - **Notificaciones:** Simula una temperatura `>35°C` o humedad `<30%` desde el simulador integrado del Dashboard y observa la notificación de alerta en la barra de estado del sistema.
   - **Multi-Dispositivo:** Abre la app en dos dispositivos/emuladores y observa cómo la telemetría y el estado de los actuadores se sincronizan en milisegundos mediante **Firebase Realtime Database**.
