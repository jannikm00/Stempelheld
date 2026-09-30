package com.jannik.stundenrechner_2

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jannik.stundenrechner_2.ui.theme.Stundenrechner2Theme
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// --- Constants & Helper Functions ---

val RastaRed = Color(0xFFE53935)
val RastaYellow = Color(0xFFFFEB3B)
val RastaGreen = Color(0xFF43A047)

@OptIn(ExperimentalMaterial3Api::class)
fun calculateFromPicker(state: TimePickerState, hours: Int, minutes: Int): LocalTime {
    val initialTime = LocalTime.of(state.hour, state.minute)
    return initialTime.plusHours(hours.toLong()).plusMinutes(minutes.toLong())
}

fun scheduleReminder(context: Context, resultTime: LocalTime) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Toast.makeText(context, "Bitte Benachrichtigungen erlauben.", Toast.LENGTH_LONG).show()
            return
        }
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (!alarmManager.canScheduleExactAlarms()) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            context.startActivity(intent)
            return
        }
    }

    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    val now = LocalDateTime.now()
    var reminderDateTime = resultTime.minusMinutes(10).atDate(now.toLocalDate())
    if (reminderDateTime.isBefore(now)) reminderDateTime = reminderDateTime.plusDays(1)

    val triggerMillis = reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    try {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        Toast.makeText(context, "Erinnerung für ${reminderDateTime.toLocalTime()} gesetzt!", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "Fehler: Keine Berechtigung.", Toast.LENGTH_SHORT).show()
    }
}

fun cancelReminder(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
    )
    if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Toast.makeText(context, "Erinnerung wurde abgebrochen", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Keine aktive Erinnerung gefunden", Toast.LENGTH_SHORT).show()
    }
}

fun scheduleTestReminder(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // 1. Check for Exact Alarm permission (Android 12+)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (!alarmManager.canScheduleExactAlarms()) {
            // Redirect to settings if permission is missing to prevent crash
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            context.startActivity(intent)
            Toast.makeText(context, "Bitte erlauben Sie exakte Alarme in den Einstellungen.", Toast.LENGTH_LONG).show()
            return
        }
    }

    // 2. Check for Notification permission (Android 13+)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Toast.makeText(context, "Bitte Benachrichtigungen erlauben.", Toast.LENGTH_SHORT).show()
            return
        }
    }

    // 3. Proceed with the alarm if permissions are granted
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        99,
        intent,
        PendingIntent.FLAG_IMMUTABLE
    )

    val triggerMillis = System.currentTimeMillis() + 5000

    try {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerMillis,
            pendingIntent
        )
        Toast.makeText(context, "Test-Alarm in 5 Sekunden...", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        // Final fallback to prevent crash if something goes wrong
        Toast.makeText(context, "Fehler: Exakte Alarme nicht erlaubt.", Toast.LENGTH_SHORT).show()
    }
}

// --- UI Components ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Create the notification channel as soon as the app starts
        createNotificationChannel()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        enableEdgeToEdge()
        setContent {
            Stundenrechner2Theme {
                TimeCalculatorScreen()
            }
        }
    }

    // 2. Define the helper function to register the channel with the system
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Erinnerungen"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH // Must be HIGH
            val channel = android.app.NotificationChannel("reminder_channel", name, importance).apply {
                description = "Benachrichtigungen für das Ausstempeln"
                // Optional: Enables lights and vibration for heads-up
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCalculatorScreen() {
    var isCustomAddExpanded by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = LocalTime.now().hour,
        initialMinute = LocalTime.now().minute
    )

    var resultTime by remember { mutableStateOf<LocalTime?>(null) }
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    var customHoursInput by remember { mutableStateOf("") }
    var customMinutesInput by remember { mutableStateOf("") }

    var eggClickCount by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Now checks the time currently selected in the picker
    val isRastaMode = (timePickerState.hour == 4 && timePickerState.minute == 20) ||
            (timePickerState.hour == 16 && timePickerState.minute == 20)

    val backgroundModifier = if (isRastaMode) {
        Modifier.background(
            brush = Brush.verticalGradient(
                // Red block
                0.0f to RastaRed,
                0.30f to RastaRed,

                // Small gradient transition (Red -> Yellow)
                0.35f to RastaYellow,

                // Yellow block
                0.60f to RastaYellow,

                // Small gradient transition (Yellow -> Green)
                0.65f to RastaGreen,

                // Green block
                1.0f to RastaGreen
            )
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.background)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .then(backgroundModifier)
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Einstempelzeit",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp, // Change the size here
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = if (isRastaMode) Color.Black else MaterialTheme.colorScheme.primary // Change the color
            )

            Box(modifier = Modifier.wrapContentHeight()) {
                TimePicker(state = timePickerState)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                DurationButton("+6h") { resultTime = calculateFromPicker(timePickerState, 6, 0) }
                DurationButton("+8:45h") { resultTime = calculateFromPicker(timePickerState, 8, 45) }
                DurationButton("+12:45h") { resultTime = calculateFromPicker(timePickerState, 12, 45) }
            }

            TextButton(onClick = { isCustomAddExpanded = !isCustomAddExpanded }) {
                Text(if (isCustomAddExpanded) "↑ Einklappen" else "↓ Individuelle Zeit")
            }

            AnimatedVisibility(visible = isCustomAddExpanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = customHoursInput, onValueChange = { customHoursInput = it.filter { c -> c.isDigit() } }, label = { Text("Std") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = customMinutesInput, onValueChange = { customMinutesInput = it.filter { c -> c.isDigit() } }, label = { Text("Min") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Button(onClick = { resultTime = calculateFromPicker(timePickerState, customHoursInput.toIntOrNull() ?: 0, customMinutesInput.toIntOrNull() ?: 0) }) { Text("+") }
                }
            }

            resultTime?.let { time ->
                val context = LocalContext.current
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isRastaMode) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ausstempeln vor", style = MaterialTheme.typography.labelSmall)
                        Text(text = time.format(timeFormatter), style = MaterialTheme.typography.headlineSmall)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scheduleReminder(context, time) }, modifier = Modifier.weight(1f)) { Text("10 Min. vorher", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(onClick = { cancelReminder(context) }, modifier = Modifier.weight(1f)) { Text("Abbrechen", style = MaterialTheme.typography.labelSmall) }
                            TextButton(onClick = { scheduleTestReminder(context) }) { Text("Test", color = if (isRastaMode) Color.Black else MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
            if (isRastaMode) Text("Die Zeit ist gekommen...", color = Color.DarkGray)
            Spacer(modifier = Modifier.weight(1f))

            val uriHandler = LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri("https://jannikm00.github.io") }) {
                Text("Programmiert mit <3 von Jannik", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                text = "Made in Germany 🇩🇪",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    eggClickCount++
                    if (eggClickCount == 7) {
                        scope.launch { snackbarHostState.showSnackbar("Wer das liest ist doof") }
                        eggClickCount = 0
                    }
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 14.sp, // Increased size
                    letterSpacing = 2.sp // Added character spacing for style
                ),
                color = if (isRastaMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DurationButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) { Text(label, style = MaterialTheme.typography.labelMedium) }
}