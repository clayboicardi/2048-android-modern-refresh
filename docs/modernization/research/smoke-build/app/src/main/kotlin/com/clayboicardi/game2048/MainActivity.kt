package com.clayboicardi.game2048

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.engine.slideRowLeft

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Composable
fun App() {
    MaterialExpressiveTheme(motionScheme = MotionScheme.expressive()) {
        Column {
            Text("2048 " + slideRowLeft(listOf(2, 2, 0, 0)))
            Box(Modifier.size(48.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(Color(0xFFEDC22E)))
            ButtonGroup(overflowIndicator = {}) {
                clickableItem(onClick = {}, label = "New")
                clickableItem(onClick = {}, label = "Undo")
            }
            LoadingIndicator()
            Button(onClick = {}) { Text("Play") }
        }
    }
}
