package com.cs22study.easygopedometer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs22study.easygopedometer.calc.DateDisplayFormatter
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SkeletonHomeScreen(today = LocalDate.now())
                }
            }
        }
    }
}

@Composable
fun SkeletonHomeScreen(today: LocalDate) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF4B89D6), Color(0xFF84B7EE))
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = DateDisplayFormatter.formatDayHeader(today),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "0",
            color = Color.White,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "EasyGo-Pedometer 核心骨架已初始化",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
        )
    }
}
