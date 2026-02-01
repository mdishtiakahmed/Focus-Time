package com.focusguard.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.utils.PermissionUtils

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FocusGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FocusGuardScreen()
                }
            }
        }
    }
    
    @Composable
    fun FocusGuardScreen() {
        var hours by remember { mutableStateOf(0) }
        var minutes by remember { mutableStateOf(30) }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E),
                            Color(0xFF16213E)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                // Title
                Text(
                    text = "FocusGuard",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00D9FF)
                )
                
                Text(
                    text = "Stay Focused, Stay Productive",
                    fontSize = 16.sp,
                    color = Color(0xFFAAAAAA)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Time Pickers Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F3460).copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Set Focus Duration",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hours Picker
                            TimePickerColumn(
                                label = "Hours",
                                value = hours,
                                onValueChange = { hours = it.coerceIn(0, 23) }
                            )
                            
                            Text(
                                text = ":",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00D9FF)
                            )
                            
                            // Minutes Picker
                            TimePickerColumn(
                                label = "Minutes",
                                value = minutes,
                                onValueChange = { minutes = it.coerceIn(0, 59) }
                            )
                        }
                    }
                }
                
                // Start Button
                Button(
                    onClick = { startFocusMode(hours, minutes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D9FF)
                    ),
                    enabled = (hours > 0 || minutes > 0)
                ) {
                    Text(
                        text = "Start Focus Mode",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A2E)
                    )
                }
                
                // Info Text
                Text(
                    text = "Your phone will be locked for the selected duration.\nYou can still receive calls.",
                    fontSize = 14.sp,
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
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
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color(0xFFAAAAAA)
            )
            
            // Increment Button
            IconButton(
                onClick = { onValueChange(value + 1) },
                modifier = Modifier.size(40.dp)
            ) {
                Text(
                    text = "▲",
                    fontSize = 20.sp,
                    color = Color(0xFF00D9FF)
                )
            }
            
            // Value Display
            Text(
                text = value.toString().padStart(2, '0'),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .width(80.dp)
                    .background(
                        Color(0xFF1A1A2E),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 8.dp),
            )
            
            // Decrement Button
            IconButton(
                onClick = { onValueChange(value - 1) },
                modifier = Modifier.size(40.dp)
            ) {
                Text(
                    text = "▼",
                    fontSize = 20.sp,
                    color = Color(0xFF00D9FF)
                )
            }
        }
    }
    
    private fun startFocusMode(hours: Int, minutes: Int) {
        // Check permissions
        if (!PermissionUtils.hasOverlayPermission(this)) {
            PermissionUtils.requestOverlayPermission(this)
            return
        }
        
        if (!PermissionUtils.isIgnoringBatteryOptimizations(this)) {
            PermissionUtils.requestIgnoreBatteryOptimization(this)
            return
        }
        
        // Calculate total minutes
        val totalMinutes = (hours * 60) + minutes
        
        if (totalMinutes > 0) {
            // Start the lock service
            val intent = Intent(this, LockService::class.java).apply {
                putExtra(LockService.EXTRA_DURATION_MINUTES, totalMinutes)
            }
            startService(intent)
            
            // Minimize the app
            moveTaskToBack(true)
        }
    }
}
