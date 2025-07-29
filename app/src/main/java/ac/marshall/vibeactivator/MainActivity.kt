package ac.marshall.vibeactivator

import android.media.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ac.marshall.vibeactivator.ui.theme.VibeActivatorTheme
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VibeActivatorTheme {
                Surface(Modifier.fillMaxSize()) {
                    VibeActivatorApp()
                }
            }
        }
    }
}

@Composable
fun VibeActivatorApp() {
    var isActivationTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            embeddedServer(Netty, port = 8080) {
                routing {
                    get("/trigger") {
                        call.respondText("Activation triggered")
                        withContext(Dispatchers.Main) {
                            isActivationTriggered = true
                        }
                    }
                }
            }.start(wait = true)
        }
    }

    if (isActivationTriggered) {
        ActivationScreen(onFinish = { isActivationTriggered = false })
    } else {
        TonePanel()
    }
}

@Composable
fun ActivationScreen(onFinish: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(10) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            playTone(ToneKind.LEFT_ONLY, 10_000)
        }

        launch {
            for (i in 10 downTo 1) {
                secondsLeft = i
                delay(1_000)
            }
            onFinish()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "SOUND ACTIVATION TRIGGERED",
            color = Color.Red,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "$secondsLeft",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold
            )
        )
    }
}


/* ---------- UI ---------- */

@Composable
fun TonePanel() {
    val scope = rememberCoroutineScope()
    var secondsLeft by remember { mutableIntStateOf(0) }
    var isBusy by remember { mutableStateOf(false) }

    fun launchTone(kind: ToneKind) {
        if (isBusy) return
        isBusy = true
        secondsLeft = 5
        scope.launch(Dispatchers.IO) {
            playTone(kind, 5_000)
        }
        scope.launch {
            repeat(5) {
                delay(1_000)
                secondsLeft--
            }
            isBusy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Audio‑GPIO tester", style = MaterialTheme.typography.titleLarge)

        Button(
            onClick = { launchTone(ToneKind.LEFT_ONLY) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Left‑only max square") }

        Button(
            onClick = { launchTone(ToneKind.RIGHT_ONLY) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Right‑only max square") }

        Button(
            onClick = { launchTone(ToneKind.OPPOSITE_PHASE) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("L & R Same Phase") }

        /*
        Button(
            onClick = { launchTone(ToneKind.INVERTED_PHASE) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("L & R Opposite Phase") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Button(
            onClick = { launchTone(ToneKind.SINE_LEFT_ONLY) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Left-only 1kHz sine") }


            onClick = { launchTone(ToneKind.SINE_RIGHT_ONLY) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Right-only 1kHz sine") }

        Button(
            onClick = { launchTone(ToneKind.SINE_BOTH) },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("L & R 1kHz sine") }
        */

        if (isBusy) {
            Text("Playing… $secondsLeft", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/* ---------- Tone generation ---------- */

private enum class ToneKind { LEFT_ONLY, RIGHT_ONLY, OPPOSITE_PHASE, INVERTED_PHASE, SINE_LEFT_ONLY, SINE_RIGHT_ONLY, SINE_BOTH }

/**
 * Plays a 1 kHz tone for the requested duration.
 * The tone is a full-scale square wave unless `SINE` is in the `kind`'s name.
 * - LEFT_ONLY   : L = +FS/-FS, R = 0
 * - RIGHT_ONLY  : R = +FS/-FS, L = 0
 * - OPPOSITE_PHASE: L = +FS/-FS, R = +FS/-FS  (Same phase)
 * - INVERTED_PHASE: L = +FS/-FS, R = -FS/+FS  (Opposite phase)
 */
private suspend fun playTone(kind: ToneKind, durationMillis: Int) {
    val sampleRate = 48000
    val freq = 1000.0
    val periodInSamples = (sampleRate / freq).toInt()

    val numSamples = (durationMillis / 1000.0 * sampleRate).toInt()
    val buffer = ShortArray(numSamples * 2) // For stereo

    for (i in 0 until numSamples) {
        val angle = 2.0 * Math.PI * i / periodInSamples
        val sampleValue = if (kind.name.contains("SINE")) {
            (Math.sin(angle) * Short.MAX_VALUE).toInt().toShort()
        } else {
            if (angle % (2.0 * Math.PI) < Math.PI) Short.MAX_VALUE else Short.MIN_VALUE
        }

        when (kind) {
            ToneKind.LEFT_ONLY, ToneKind.SINE_LEFT_ONLY -> {
                buffer[i * 2] = sampleValue
                buffer[i * 2 + 1] = 0
            }
            ToneKind.RIGHT_ONLY, ToneKind.SINE_RIGHT_ONLY -> {
                buffer[i * 2] = 0
                buffer[i * 2 + 1] = sampleValue
            }
            ToneKind.OPPOSITE_PHASE, ToneKind.SINE_BOTH -> {
                buffer[i * 2] = sampleValue
                buffer[i * 2 + 1] = sampleValue
            }
            ToneKind.INVERTED_PHASE -> {
                buffer[i * 2] = sampleValue
                buffer[i * 2 + 1] = (-sampleValue.toInt()).toShort()
            }
        }
    }

    val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(buffer.size * 2)
        .build()

    audioTrack.write(buffer, 0, buffer.size)
    audioTrack.play()

    delay(durationMillis.toLong())

    audioTrack.stop()
    audioTrack.release()
}
