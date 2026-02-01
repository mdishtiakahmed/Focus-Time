package com.focusguard.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.focusguard.app.data.PreferenceManager
import com.focusguard.app.manager.ScheduleManager
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.utils.PermissionUtils
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var scheduleManager: ScheduleManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager = PreferenceManager(this)
        scheduleManager = ScheduleManager(this)
        
        requestNotificationPermission()
        
        setContent {
            FocusGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isSoundEnabled by preferenceManager.isSoundEnabled.collectAsState(initial = false)
                    
                    FocusGuardDataScreen(
                        isSoundEnabled = isSoundEnabled,
                        onSoundToggle = { 
                            lifecycleScope.launch { preferenceManager.setSoundEnabled(it) }
                        },
                        onStartFocus = { h, m -> startFocusMode(h, m) },
                        onScheduleFocus = { sh, sm, dh, dm -> scheduleFocusMode(sh, sm, dh, dm) }
                    )
                }
            }
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun FocusGuardDataScreen(
        isSoundEnabled: Boolean,
        onSoundToggle: (Boolean) -> Unit,
        onStartFocus: (Int, Int) -> Unit,
        onScheduleFocus: (Int, Int, Int, Int) -> Unit
    ) {
        var selectedTab by remember { mutableIntStateOf(0) }
        
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = "Focus") },
                        label = { Text("Focus") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Schedule, contentDescription = "Schedule") },
                        label = { Text("Schedule") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                        )
                    )
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                when (selectedTab) {
                    0 -> FocusTab(onStartFocus)
                    1 -> ScheduleTab(onScheduleFocus)
                    2 -> SettingsTab(isSoundEnabled, onSoundToggle)
                }
            }
        }
    }
    
    @Composable
    fun FocusTab(onStart: (Int, Int) -> Unit) {
        var hours by remember { mutableIntStateOf(0) }
        var minutes by remember { mutableIntStateOf(30) }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Instant Focus",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D9FF)
            )
            
            TimeDurationPicker(hours, minutes, { hours = it }, { minutes = it })
            
            Button(
                onClick = { onStart(hours, minutes) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D9FF)),
                enabled = (hours > 0 || minutes > 0)
            ) {
                Text("Start Now", color = Color(0xFF1A1A2E), fontWeight = FontWeight.Bold)
            }
        }
    }
    
    @Composable
    fun ScheduleTab(onSchedule: (Int, Int, Int, Int) -> Unit) {
        var startHour by remember { mutableIntStateOf(9) }
        var startMinute by remember { mutableIntStateOf(0) }
        var durationHours by remember { mutableIntStateOf(0) }
        var durationMinutes by remember { mutableIntStateOf(45) }
        var isAm by remember { mutableStateOf(true) }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Schedule Focus",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D9FF)
            )
            
            // Start Time Picker
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3460).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Start Time", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TimePickerColumn("", startHour, { startHour = it.coerceIn(1, 12) })
                        Text(":", color = Color(0xFF00D9FF), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        TimePickerColumn("", startMinute, { startMinute = it.coerceIn(0, 59) })
                        
                        // AM/PM Toggle
                        Button(
                            onClick = { isAm = !isAm },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if(isAm) Color(0xFF00D9FF) else Color(0xFF0F3460)
                            )
                        ) {
                            Text(if (isAm) "AM" else "PM", color = if(isAm) Color.Black else Color.White)
                        }
                    }
                }
            }
            
            // Duration Picker
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3460).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Duration", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimeDurationPicker(durationHours, durationMinutes, { durationHours = it }, { durationMinutes = it })
                }
            }
            
            Button(
                onClick = { 
                    // Convert to 24h format for alarm
                    var hour24 = startHour
                    if (!isAm && hour24 < 12) hour24 += 12
                    else if (isAm && hour24 == 12) hour24 = 0
                    
                    onSchedule(hour24, startMinute, durationHours, durationMinutes) 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D9FF)),
                enabled = (durationHours > 0 || durationMinutes > 0)
            ) {
                Text("Set Schedule", color = Color(0xFF1A1A2E), fontWeight = FontWeight.Bold)
            }
        }
    }
    
    @Composable
    fun SettingsTab(isSoundEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00D9FF)
            )
            
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3460).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Unlock Sound", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Play sound when timer ends", color = Color.Gray, fontSize = 14.sp)
                    }
                    Switch(
                        checked = isSoundEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00D9FF),
                            checkedTrackColor = Color(0xFF0F3460),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF1A1A2E)
                        )
                    )
                }
            }
        }
    }

    @Composable
    fun TimeDurationPicker(
        hours: Int, 
        minutes: Int, 
        onHoursChange: (Int) -> Unit, 
        onMinutesChange: (Int) -> Unit
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimePickerColumn("Hours", hours, { onHoursChange(it.coerceIn(0, 23)) })
            Text(":", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00D9FF))
            TimePickerColumn("Minutes", minutes, { onMinutesChange(it.coerceIn(0, 59)) })
        }
    }
    
    @Composable
    fun TimePickerColumn(
        label: String,
        value: Int,
        onValueChange: (Int) -> Unit
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (label.isNotEmpty()) {
                Text(text = label, fontSize = 14.sp, color = Color(0xFFAAAAAA))
            }
            IconButton(onClick = { onValueChange(value + 1) }, modifier = Modifier.size(40.dp)) {
                Text("▲", fontSize = 20.sp, color = Color(0xFF00D9FF))
            }
            Text(
                text = value.toString().padStart(2, '0'),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .width(80.dp)
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp),
            )
            IconButton(onClick = { onValueChange(value - 1) }, modifier = Modifier.size(40.dp)) {
                Text("▼", fontSize = 20.sp, color = Color(0xFF00D9FF))
            }
        }
    }
    
    private fun startFocusMode(hours: Int, minutes: Int) {
        if (!PermissionUtils.hasOverlayPermission(this)) {
            PermissionUtils.requestOverlayPermission(this)
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!PermissionUtils.isIgnoringBatteryOptimizations(this)) {
            PermissionUtils.requestIgnoreBatteryOptimization(this)
             Toast.makeText(this, "Please grant battery permission", Toast.LENGTH_SHORT).show()
            return
        }
        
        val totalMinutes = (hours * 60) + minutes
        if (totalMinutes > 0) {
            val intent = Intent(this, LockService::class.java).apply {
                putExtra(LockService.EXTRA_DURATION_MINUTES, totalMinutes)
            }
            startService(intent)
            moveTaskToBack(true)
        }
    }
    
    private fun scheduleFocusMode(startHour: Int, startMinute: Int, durationHours: Int, durationMinutes: Int) {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
                Toast.makeText(this, "Please allow exact alarms for scheduling", Toast.LENGTH_LONG).show()
                return
            }
        }
        
        val totalDuration = (durationHours * 60) + durationMinutes
        scheduleManager.scheduleFocus(startHour, startMinute, totalDuration)
        Toast.makeText(this, "Focus scheduled!", Toast.LENGTH_SHORT).show()
    }
}
