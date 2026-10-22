package com.powerampfree.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Minimal first UI: launch/stop hook + band sliders showing curve info.
 * TODO(integrate): full player screen; EQ curve drawn from ParametricEq.responseCurve.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("PowerampFree — reconstruction scaffold", style = MaterialTheme.typography.titleMedium)
                    // Placeholder slider: the real EQ UI will bind EqPreset JSON.
                    androidx.compose.material3.Slider(value = 0f, onValueChange = {}, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
