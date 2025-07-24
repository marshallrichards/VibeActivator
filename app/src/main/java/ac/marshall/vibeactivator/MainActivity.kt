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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ac.marshall.vibeactivator.ui.theme.VibeActivatorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VibeActivatorTheme {
                Surface(Modifier.fillMaxSize()) {
                    TonePanel()
                }
            }
        }
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

        Button(
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
private suspend fun playTone(kind: ToneKind, millis: Int) {
    val fs = 48_000                      // sample rate
    val freq = 1_000                     // 1 kHz
    val periodInFrames = fs / freq       // period in samples

    // The buffer must contain an integer number of cycles.
    // The original `fs / 60` (~16.7ms) does not, causing a phase jump on loop.
    val frameCount = periodInFrames * 16 // 16 cycles -> 768 frames
    val bufStereo = ShortArray(frameCount * 2)
    for (i in 0 until frameCount) {
        val sample = if (kind.name.contains("SINE")) {
            (Short.MAX_VALUE * kotlin.math.sin(2 * kotlin.math.PI * freq * i / fs)).toInt().toShort()
        } else {
            val samplesIntoCycle = i % periodInFrames
            if (samplesIntoCycle < periodInFrames / 2) Short.MAX_VALUE else Short.MIN_VALUE
        }
        when (kind) {
            ToneKind.LEFT_ONLY, ToneKind.SINE_LEFT_ONLY -> {
                bufStereo[2 * i] = sample
                bufStereo[2 * i + 1] = 0
            }
            ToneKind.RIGHT_ONLY, ToneKind.SINE_RIGHT_ONLY -> {
                bufStereo[2 * i] = 0
                bufStereo[2 * i + 1] = sample
            }
            ToneKind.OPPOSITE_PHASE, ToneKind.SINE_BOTH -> {
                bufStereo[2 * i] = sample
                bufStereo[2 * i + 1] = sample
            }
            ToneKind.INVERTED_PHASE -> {
                bufStereo[2 * i] = sample
                bufStereo[2 * i + 1] = (-sample.toInt()).toShort()
            }
        }
    }

    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(fs)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(bufStereo.size * 2)   // 2 bytes per short
        .build()

    track.write(bufStereo, 0, bufStereo.size)
    track.setLoopPoints(0, frameCount, -1)          // loop forever
    track.play()

    delay(millis.toLong())

    track.stop()
    track.release()
}
