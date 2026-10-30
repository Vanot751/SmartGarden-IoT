package com.example.smartgarden

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// ============================================================================
// PALETA DE COLORES GLOBAL: PASTEL MINIMALISM (Sin fondos blancos puros)
// ============================================================================
val PearlWarmBackground = Color(0xFFF5F5F0)       // Fondo principal gris perlado cálido / crema suave
val WarmSurfaceContainer = Color(0xFFEBEBE4)      // Contenedor secundario cálido (sin usar blanco)
val WarmSurfaceHigh = Color(0xFFE0E0D6)           // Contenedor de realce suave
val CharcoalText = Color(0xFF2E2E2E)              // Gris carbón oscuro para texto principal
val CharcoalSubtle = Color(0xFF5C5C57)            // Gris carbón medio para etiquetas secundarias
val EmeraldActive = Color(0xFF2ECC71)             // Verde esmeralda para botones e interruptores activos
val EmeraldSoftBg = Color(0xFFD5F5E3)             // Verde pastel suave para insignias de estado

// Fondos Pastel para Tarjetas de Telemetría
val PastelOrangeTemp = Color(0xFFFCE7D6)          // Naranja melocotón pastel (Temperatura)
val PastelOrangeAccent = Color(0xFF9A3412)        // Ocre oscuro para iconos/detalles de temperatura
val PastelBlueMoisture = Color(0xFFD6EAF8)        // Celeste agua pastel (Humedad del Suelo)
val PastelBlueAccent = Color(0xFF0369A1)          // Azul marino para detalles de humedad
val PastelYellowSolar = Color(0xFFFEF9C3)         // Amarillo pálido mantequilla (Luz Solar)
val PastelYellowAccent = Color(0xFF854D0E)        // Ámbar oscuro para detalles de luz solar

// Colores de Alerta y Emergencia
val EmergencyRed = Color(0xFFE74C3C)              // Rojo sólido para PARADA DE EMERGENCIA y alertas
val EmergencySoftBg = Color(0xFFFADBD8)           // Fondo pastel rojizo para banners de advertencia

class MainActivity : ComponentActivity() {

    companion object {
        const val CHANNEL_ID = "temp_alerts_channel"
        const val CHANNEL_NAME = "Alertas de Temperatura Smart Garden"
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permiso de notificaciones otorgado o denegado por el usuario
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configuración de notificaciones locales para alertas de temperatura
        createNotificationChannel()
        checkAndRequestNotificationPermission()

        setContent {
            FloraSenseTheme {
                AppNavigation()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Notificaciones de alertas de temperatura crítica para Smart Garden IoT"
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun FloraSenseTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = EmeraldActive,
        onPrimary = CharcoalText,
        background = PearlWarmBackground,
        onBackground = CharcoalText,
        surface = WarmSurfaceContainer,
        onSurface = CharcoalText,
        error = EmergencyRed,
        onError = Color.White,
    )

    val typography = Typography(
        displayLarge = TextStyle(
            fontSize = 38.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CharcoalText,
            letterSpacing = (-0.8).sp,
        ),
        headlineMedium = TextStyle(
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            color = CharcoalText,
        ),
        titleMedium = TextStyle(
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = CharcoalText,
        ),
        bodyMedium = TextStyle(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
            color = CharcoalText,
        ),
        labelSmall = TextStyle(
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            color = CharcoalSubtle,
            letterSpacing = 0.7.sp,
        ),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun FloraSenseAppPreview() {
    FloraSenseTheme {
        DashboardScreen(
            uiState = FloraSenseUiState(),
            onSelectCategory = {},
            onUpdateSimulatedTelemetry = { _, _, _ -> },
            onSimulateSecondDevice = {},
            onNavigateToControls = {},
            onLogout = {},
        )
    }
}
