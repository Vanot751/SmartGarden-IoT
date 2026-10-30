# 🌿 SmartGarden - Pasarela Segura de Monitoreo y Control Botánico

**SmartGarden** es una aplicación móvil nativa para Android diseñada como pasarela de supervisión, telemetría ambiental y control remoto de actuadores para un **Jardín Inteligente Autónomo**.

El proyecto traslada al ecosistema móvil y en la nube una **arquitectura IoT de modelo emisor-receptor** originalmente instrumentada sobre dos microcontroladores **Arduino UNO (ATmega328P)** interconectados por bus serial asíncrono (`UART TX/RX`), donde:
- **Nodo Emisor (Censor):** Adquiere de forma continua las variables analógicas críticas del cultivo (temperatura ambiente, humedad de sustrato y radiación lumínica) y empaqueta la telemetría en tramas estructuradas (`T,L,H`).
- **Nodo Receptor (Actuador) y Pasarela Cloud:** Procesa las lecturas en tiempo real, ejecuta reglas deterministas autónomas y sincroniza su estado de forma bidireccional con múltiples terminales Android a través de **Firebase**.

---

## 🏗️ Arquitectura de Software

La aplicación ha sido construida siguiendo las mejores prácticas de ingeniería de software moderno para Android:

- **Lenguaje:** **Kotlin** con uso intensivo de corrutinas y flujos reactivos (`StateFlow` / `MutableStateFlow`).
- **Patrón Arquitectónico (MVVM - Model-View-ViewModel):**
  - **Model / Estado Inmutable (`FloraSenseUiState`):** Representa una única fuente de verdad (*Single Source of Truth*) con el estado de sesión, telemetría de sensores, estado de actuadores y banderas de alerta crítica.
  - **ViewModel (`FloraSenseViewModel`):** Orquesta la lógica de negocio, el registro y autenticación con Firebase Auth y los *listeners* permanentes de lectura/escritura hacia Firebase Realtime Database.
  - **View (`LoginScreen`, `DashboardScreen`, `ControlPanelScreen`):** Interfaz 100% declarativa en **Jetpack Compose (Material 3)** articulada mediante **Jetpack Navigation Compose** (`AppNavigation`).
- **Sistema de Diseño *"Pastel Minimalism"*:**
  - Diseñado para eliminar la fatiga visual mediante superficies cálidas libres de blancos puros (`#FFFFFF`).
  - **Lienzo Principal:** Gris perlado cálido / crema suave (`#F5F5F0`) y contenedores arena (`#EBEBE4`).
  - **Tipografía de Alto Contraste:** Gris carbón oscuro (`#2E2E2E`) con lecturas numéricas de gran formato (`displayLarge`).
  - **Codificación Semántica Pastel:** Naranja melocotón (`#FCE7D6`) para temperatura, celeste agua (`#D6EAF8`) para humedad de suelo y amarillo mantequilla (`#FEF9C3`) para radiación solar.
  - **Acentos Operativos y Críticos:** Verde esmeralda (`#2ECC71`) para actuadores activos y rojo sólido (`#E74C3C`) reservado estrictamente para alertas críticas y parada de emergencia.

```text
app/src/main/java/com/example/smartgarden/
├── MainActivity.kt          # Punto de entrada, FloraSenseTheme y paleta Pastel Minimalism
├── AppNavigation.kt         # NavHost (Login, Dashboard, ControlPanel) y NavigationBar inferior
├── FloraSenseViewModel.kt   # Estado reactivo StateFlow + integración SDK Firebase Auth y RTDB
├── LoginScreen.kt           # Autenticación/Registro de usuario (doble confirmación) y panel ISO/IEC 27400
├── DashboardScreen.kt       # Chips de perfiles botánicos, tarjetas de telemetría y alertas
└── ControlPanelScreen.kt    # Interruptores de hardware, dosificador (50/100/250ml) y E-STOP
```

---

## 🌐 Interconexión IoT y Sincronización en Tiempo Real (Requisito Académico)

Para cumplir con el requisito de **conectividad inalámbrica multi-dispositivo en tiempo real**, **SmartGarden** implementa **Firebase Realtime Database** apuntando al nodo centralizado `/florasense/gardens/nodo_central_01`:

1. **Lectura Reactiva Permanente (`ValueEventListener`):**
   - Al inicializarse el `FloraSenseViewModel`, se registra un `addValueEventListener` permanente sobre la referencia de la base de datos.
   - Cualquier cambio en las lecturas de los sensores o en el estado de los actuadores realizado desde un **Dispositivo #1**, un **Dispositivo #2** o la pasarela del microcontrolador dispara instantáneamente el callback `onDataChange(snapshot: DataSnapshot)`, actualizando el `StateFlow` y recomponiendo la UI en milisegundos sin necesidad de recargar la pantalla.
2. **Escritura Bidireccional Instantánea (`.setValue()` / `.updateChildren()`):**
   - Las acciones manuales del operador (encender/apagar la bomba de agua, activar el nebulizador, seleccionar la dosis de `50ml`, `100ml` o `250ml`, o accionar la Parada de Emergencia) escriben directamente sobre los subnodos en la nube.

### Estructura JSON en Firebase Realtime Database

```json
{
  "florasense": {
    "gardens": {
      "nodo_central_01": {
        "sensores": {
          "temperatura": 26.4,
          "humedadSuelo": 28,
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

## 🔐 Seguridad y Cumplimiento ISO/IEC 27400

El diseño de **SmartGarden** aborda directamente las brechas de seguridad identificadas en sistemas IoT tradicionales, alineándose con los principios de la norma **ISO/IEC 27400 (Seguridad y Privacidad en el Internet de las Cosas)**:

- **🔒 Confidencialidad (Mitigación de *Sniffing*):**
  - Toda la telemetría ambiental y los comandos de actuación viajan entre los dispositivos Android y los servidores de Firebase sobre canales cifrados mediante **TLS 1.3** y cifrado en reposo **AES-256-GCM**, impidiendo la interceptación en texto plano de la trama `(T,L,H)`.
- **🛡️ Integridad y Control de Acceso (Mitigación de *Spoofing* e Inyección de Tramas):**
  - Se integra **Firebase Authentication** (`signInWithEmailAndPassword` / `createUserWithEmailAndPassword`) como barrera obligatoria antes de acceder al Dashboard o al Panel de Control.
  - **Doble Confirmación de Registro:** Para prevenir errores tipográficos e inyección de cuentas no deseadas, el registro de usuarios exige **doble confirmación de correo electrónico y contraseña**.
  - Solo operadores autenticados con sesión activa pueden emitir escrituras hacia `/actuadores`, evitando que actores no autorizados inyecten paquetes falsos (ej. mantener la bomba encendida indefinidamente para causar un desborde hídrico).
- **⚡ Disponibilidad y *Fail-Safe* Operativo (Mitigación de Denegación de Servicio / Estrés Térmico):**
  - Incorpora un módulo prioritario de **`PARADA DE EMERGENCIA (E-STOP)`** que actúa como *override* manual sobre el microcontrolador, enviando una actualización atómica (`paradaEmergencia = true`, `bombaAgua = false`, `nebulizador = false`) para cortar inmediatamente la energía de los actuadores ante anomalías térmicas (`> 35°C`) o fallos hidráulicos.

---

## ⚙️ Simulación e Integración Lógica de Hardware

La aplicación modela fielmente el comportamiento electrónico del prototipo físico y sus umbrales deterministas:

### 📡 Subsistemas de Censado (Entradas Analógicas)
| Componente Físico | Canal / Pin | Variable | Regla Lógica en SmartGarden |
| :--- | :---: | :--- | :--- |
| **Sensor de Temperatura `TMP36`** | `Pin A0` | Temperatura Ambiente (`°C`) | Escala lineal `10 mV/°C`. Si supera el umbral crítico (**`> 35.0°C`**), dispara alerta visual roja y activa la alarma térmica. |
| **Fotorresistencia `LDR` (`10 kΩ`)** | `Pin A1` | Radiación Solar (`%`) | Divisor de tensión (`0V - 5V`) que monitorea el fotoperíodo según el perfil botánico (*Tropicales, Suculentas, Aromáticas*). |
| **Higrómetro / Potenciómetro** | `Pin A2` | Humedad del Suelo (`%`) | Si desciende por debajo del umbral crítico (**`< 30%`**), muestra advertencia de sequía y conmuta la bomba a estado activo. |

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
3. **Pruebas Multi-Dispositivo:**
   - Inicia sesión o registra una nueva cuenta (con doble confirmación) en dos dispositivos o emuladores con **Firebase Authentication**.
   - Modifica los sensores desde el botón **"Simular"** en el Dashboard o alterna los interruptores en **Controles Manuales** y observa cómo la telemetría y los actuadores se actualizan en tiempo real en ambos dispositivos.
