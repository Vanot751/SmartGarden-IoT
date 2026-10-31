package com.example.smartgarden

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Categorías botánicas para los filtros tipo "Chips" del Dashboard.
 */
enum class PlantCategory(
    val displayName: String,
    val optimalTempRange: String,
    val minMoistureThreshold: Int,
) {
    TODAS("Todas", "20°C - 35°C", 30),
    TROPICALES("Tropicales", "22°C - 32°C", 35),
    SUCULENTAS("Suculentas", "18°C - 35°C", 20),
    AROMATICAS("Aromáticas", "20°C - 30°C", 30),
}

/**
 * Estado global inmutable de FloraSense sincronizado con Firebase Auth y Firebase Realtime Database.
 */
data class FloraSenseUiState(
    // Estado de Autenticación (Firebase Auth + ISO/IEC 27400 IoT Security)
    val isAuthenticated: Boolean = false,
    val isAuthenticating: Boolean = false,
    val userEmail: String = "",
    val authError: String? = null,
    val isDarkMode: Boolean = false,
    val isoSecurityProtocol: String = "ISO/IEC 27400 • TLS 1.3 • AES-256-GCM",

    // Sincronización Multi-Dispositivo (Firebase Realtime Database)
    val firebaseNodePath: String = "/florasense/gardens/nodo_central_01",
    val connectedDevicesCount: Int = 2,
    val isRealtimeConnected: Boolean = false,
    val lastSyncSource: String = "Firebase Realtime Database",
    val syncLatencyMs: Int = 22,

    // Filtro de categoría de planta activo en el Dashboard
    val selectedCategory: PlantCategory = PlantCategory.TODAS,

    // Telemetría Ambiental en Tiempo Real (Trama T,L,H)
    val temperatureCelsius: Float = 26.4f,
    val soilMoisturePercent: Int = 28,
    val sunlightPercent: Int = 78,

    // Estado de Actuadores y Dosificación (Panel de Control)
    val isWaterPumpOn: Boolean = true,
    val isNebulizerOn: Boolean = false,
    val selectedDoseMl: Int = 100, // Opciones: 50, 100, 250
    val isEmergencyStopActive: Boolean = false,
    val statusLogMessage: String = "RTDB: Conectando...",
) {
    // Reglas de negocio deterministas del prototipo IoT
    val isCriticalHeat: Boolean
        get() = temperatureCelsius > 35.0f

    val isLowMoisture: Boolean
        get() = soilMoisturePercent < 30

    val hasCriticalAlert: Boolean
        get() = isCriticalHeat || isLowMoisture || isEmergencyStopActive
}

class FloraSenseViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FloraSenseUiState())
    val uiState: StateFlow<FloraSenseUiState> = _uiState.asStateFlow()

    // Instancias reales del SDK de Firebase
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val databaseRef: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("/florasense/gardens/nodo_central_01")

    // Listener permanente en tiempo real para sincronización multi-dispositivo
    private val realtimeValueListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (!snapshot.exists()) {
                // Si el nodo aún no existe en la nube, inicializa la estructura base en Firebase RTDB
                seedInitialGardenNodeIfEmpty()
                return
            }

            _uiState.update { current ->
                // Lectura de sensores (soporta subnodo "sensores/*" o claves directas)
                val tempValue = snapshot.child("sensores/temperatura").getValue(Double::class.java)
                    ?: snapshot.child("temperatureCelsius").getValue(Double::class.java)
                    ?: current.temperatureCelsius.toDouble()

                val moistureValue = snapshot.child("sensores/humedadSuelo").getValue(Int::class.java)
                    ?: snapshot.child("soilMoisturePercent").getValue(Int::class.java)
                    ?: current.soilMoisturePercent

                val sunlightValue = snapshot.child("sensores/luzSolar").getValue(Int::class.java)
                    ?: snapshot.child("sunlightPercent").getValue(Int::class.java)
                    ?: current.sunlightPercent

                // Lectura de actuadores (soporta subnodo "actuadores/*" o claves directas)
                val waterPumpState = snapshot.child("actuadores/bombaAgua").getValue(Boolean::class.java)
                    ?: snapshot.child("isWaterPumpOn").getValue(Boolean::class.java)
                    ?: current.isWaterPumpOn

                val nebulizerState = snapshot.child("actuadores/nebulizador").getValue(Boolean::class.java)
                    ?: snapshot.child("isNebulizerOn").getValue(Boolean::class.java)
                    ?: current.isNebulizerOn

                val emergencyStopState = snapshot.child("actuadores/paradaEmergencia").getValue(Boolean::class.java)
                    ?: snapshot.child("isEmergencyStopActive").getValue(Boolean::class.java)
                    ?: current.isEmergencyStopActive

                val doseMlValue = snapshot.child("actuadores/dosisRiegoMl").getValue(Int::class.java)
                    ?: snapshot.child("selectedDoseMl").getValue(Int::class.java)
                    ?: current.selectedDoseMl

                val statusText = when {
                    emergencyStopState -> "RTDB: Parada de Emergencia"
                    tempValue > 35.0 -> String.format(java.util.Locale.US, "RTDB: Alerta Térmica (%.1f°C)", tempValue.toFloat())
                    moistureValue < 30 -> "RTDB: Humedad Crítica ($moistureValue%)"
                    else -> "RTDB: Conectado en Tiempo Real"
                }

                current.copy(
                    isRealtimeConnected = true,
                    temperatureCelsius = tempValue.toFloat(),
                    soilMoisturePercent = moistureValue.coerceIn(0, 100),
                    sunlightPercent = sunlightValue.coerceIn(0, 100),
                    isWaterPumpOn = if (emergencyStopState) false else waterPumpState,
                    isNebulizerOn = if (emergencyStopState) false else nebulizerState,
                    selectedDoseMl = doseMlValue,
                    isEmergencyStopActive = emergencyStopState,
                    lastSyncSource = "Firebase RTDB (/florasense/gardens/nodo_central_01)",
                    statusLogMessage = statusText,
                )
            }
        }

        override fun onCancelled(error: DatabaseError) {
            _uiState.update { current ->
                current.copy(
                    isRealtimeConnected = false,
                    statusLogMessage = "Error de sincronización RTDB: ${error.message}",
                )
            }
        }
    }

    init {
        // Verifica si ya existe una sesión activa en Firebase Auth
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            _uiState.update {
                it.copy(
                    isAuthenticated = true,
                    userEmail = currentUser.email.orEmpty(),
                )
            }
        }

        // Suscripción permanente en tiempo real al nodo de Firebase Realtime Database
        databaseRef.addValueEventListener(realtimeValueListener)
    }

    private fun seedInitialGardenNodeIfEmpty() {
        val current = _uiState.value
        val initialPayload = mapOf(
            "sensores" to mapOf(
                "temperatura" to current.temperatureCelsius,
                "humedadSuelo" to current.soilMoisturePercent,
                "luzSolar" to current.sunlightPercent,
            ),
            "actuadores" to mapOf(
                "bombaAgua" to current.isWaterPumpOn,
                "nebulizador" to current.isNebulizerOn,
                "dosisRiegoMl" to current.selectedDoseMl,
                "paradaEmergencia" to current.isEmergencyStopActive,
            ),
        )
        databaseRef.setValue(initialPayload)
    }

    // ========================================================================
    // PASO 2: AUTENTICACIÓN REAL CON FIREBASE AUTHENTICATION
    // ========================================================================
    fun loginWithFirebase(
        email: String,
        password: String,
        onLoginSuccess: () -> Unit,
    ) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            _uiState.update {
                it.copy(authError = "Ingresa un correo electrónico válido.")
            }
            return
        }
        if (password.length < 6) {
            _uiState.update {
                it.copy(authError = "Política ISO 27400: La contraseña requiere mínimo 6 caracteres.")
            }
            return
        }

        _uiState.update {
            it.copy(isAuthenticating = true, authError = null)
        }

        firebaseAuth.signInWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { authResult ->
                val authenticatedEmail = authResult.user?.email ?: cleanEmail
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        isAuthenticating = false,
                        userEmail = authenticatedEmail,
                        authError = null,
                        statusLogMessage = "Sesión Firebase Auth activa ($authenticatedEmail)",
                    )
                }
                onLoginSuccess()
            }
            .addOnFailureListener { exception ->
                _uiState.update {
                    it.copy(
                        isAuthenticated = false,
                        isAuthenticating = false,
                        authError = exception.localizedMessage
                            ?: "Error al iniciar sesión en Firebase Authentication",
                    )
                }
            }
    }

    fun registerWithFirebase(
        email: String,
        confirmEmail: String,
        password: String,
        confirmPassword: String,
        onRegisterSuccess: () -> Unit,
    ) {
        val cleanEmail = email.trim()
        val cleanConfirmEmail = confirmEmail.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            _uiState.update {
                it.copy(authError = "Ingresa un correo electrónico válido.")
            }
            return
        }
        if (cleanEmail != cleanConfirmEmail) {
            _uiState.update {
                it.copy(authError = "Los correos electrónicos no coinciden.")
            }
            return
        }
        if (password.length < 6) {
            _uiState.update {
                it.copy(authError = "Política ISO 27400: La contraseña requiere mínimo 6 caracteres.")
            }
            return
        }
        if (password != confirmPassword) {
            _uiState.update {
                it.copy(authError = "Las contraseñas no coinciden.")
            }
            return
        }

        _uiState.update {
            it.copy(isAuthenticating = true, authError = null)
        }

        firebaseAuth.createUserWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { authResult ->
                val authenticatedEmail = authResult.user?.email ?: cleanEmail
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        isAuthenticating = false,
                        userEmail = authenticatedEmail,
                        authError = null,
                        statusLogMessage = "Cuenta registrada y sesión activa ($authenticatedEmail)",
                    )
                }
                onRegisterSuccess()
            }
            .addOnFailureListener { exception ->
                _uiState.update {
                    it.copy(
                        isAuthenticated = false,
                        isAuthenticating = false,
                        authError = exception.localizedMessage
                            ?: "Error al registrar cuenta en Firebase Authentication",
                    )
                }
            }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authError = null) }
    }

    fun logout(onLoggedOut: () -> Unit) {
        firebaseAuth.signOut()
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                userEmail = "",
                authError = null,
                statusLogMessage = "Sesión cerrada en Firebase Authentication",
            )
        }
        onLoggedOut()
    }

    // ========================================================================
    // PASO 4: CONTROL DE ACTUADORES (ESCRITURA EN FIREBASE REALTIME DATABASE)
    // ========================================================================
    fun setWaterPumpState(enabled: Boolean) {
        if (_uiState.value.isEmergencyStopActive) return
        databaseRef.child("actuadores/bombaAgua").setValue(enabled)
            .addOnFailureListener { error ->
                _uiState.update {
                    it.copy(statusLogMessage = "Error al actualizar bombaAgua: ${error.localizedMessage}")
                }
            }
    }

    fun setNebulizerState(enabled: Boolean) {
        if (_uiState.value.isEmergencyStopActive) return
        databaseRef.child("actuadores/nebulizador").setValue(enabled)
            .addOnFailureListener { error ->
                _uiState.update {
                    it.copy(statusLogMessage = "Error al actualizar nebulizador: ${error.localizedMessage}")
                }
            }
    }

    fun selectDoseMl(doseMl: Int) {
        if (_uiState.value.isEmergencyStopActive) return
        databaseRef.child("actuadores/dosisRiegoMl").setValue(doseMl)
            .addOnFailureListener { error ->
                _uiState.update {
                    it.copy(statusLogMessage = "Error al actualizar dosisRiegoMl: ${error.localizedMessage}")
                }
            }
    }

    fun triggerEmergencyStop() {
        val newEmergencyState = !_uiState.value.isEmergencyStopActive
        val updates = mutableMapOf<String, Any>(
            "actuadores/paradaEmergencia" to newEmergencyState,
        )
        if (newEmergencyState) {
            // Override prioritario: apaga inmediatamente ambos actuadores en la nube
            updates["actuadores/bombaAgua"] = false
            updates["actuadores/nebulizador"] = false
        }
        databaseRef.updateChildren(updates)
            .addOnFailureListener { error ->
                _uiState.update {
                    it.copy(statusLogMessage = "Error en Parada de Emergencia: ${error.localizedMessage}")
                }
            }
    }

    fun applyIrrigationDose() {
        val current = _uiState.value
        if (current.isEmergencyStopActive) return
        val moistureGain = when (current.selectedDoseMl) {
            50 -> 6
            100 -> 12
            else -> 24
        }
        val updatedMoisture = (current.soilMoisturePercent + moistureGain).coerceAtMost(100)
        val updates = mapOf<String, Any>(
            "actuadores/bombaAgua" to true,
            "sensores/humedadSuelo" to updatedMoisture,
        )
        databaseRef.updateChildren(updates)
    }

    fun toggleDarkMode() {
        _uiState.update { current ->
            current.copy(isDarkMode = !current.isDarkMode)
        }
    }

    // ========================================================================
    // FUNCIONES COMPLEMENTARIAS DEL DASHBOARD (ESCRITURA RTDB PARA PRUEBAS)
    // ========================================================================
    fun selectPlantCategory(category: PlantCategory) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category,
                statusLogMessage = "Perfil botánico: ${category.displayName} (Óptimo ${category.optimalTempRange})",
            )
        }
    }

    fun updateSimulatedTelemetry(
        temperature: Float = _uiState.value.temperatureCelsius,
        moisture: Int = _uiState.value.soilMoisturePercent,
        sunlight: Int = _uiState.value.sunlightPercent,
    ) {
        val clampedMoisture = moisture.coerceIn(0, 100)
        val clampedSunlight = sunlight.coerceIn(0, 100)
        val updates = mutableMapOf<String, Any>(
            "sensores/temperatura" to temperature,
            "sensores/humedadSuelo" to clampedMoisture,
            "sensores/luzSolar" to clampedSunlight,
        )
        if (clampedMoisture < 30 && !_uiState.value.isEmergencyStopActive) {
            updates["actuadores/bombaAgua"] = true
        }
        databaseRef.updateChildren(updates)
    }

    fun simulateSecondDeviceSync() {
        if (_uiState.value.isEmergencyStopActive) return
        val toggledNebulizer = !_uiState.value.isNebulizerOn
        databaseRef.child("actuadores/nebulizador").setValue(toggledNebulizer)
    }

    override fun onCleared() {
        databaseRef.removeEventListener(realtimeValueListener)
    }
}
