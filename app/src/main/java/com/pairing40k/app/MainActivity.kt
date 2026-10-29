package com.pairing40k.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pairing40k.app.ui.AnalysisScreen
import com.pairing40k.app.ui.Bone
import com.pairing40k.app.ui.Gold
import com.pairing40k.app.ui.HistoryScreen
import com.pairing40k.app.ui.MatrixScreen
import com.pairing40k.app.ui.Muted
import com.pairing40k.app.ui.PairingScreen

private val PairingColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color.Black,
    secondary = Color(0xFF3B7BD1),
    tertiary = Color(0xFFD0392F),
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF17161A),
    surfaceVariant = Color(0xFF26242A),
    secondaryContainer = Color(0xFF3A3122),
    onBackground = Bone,
    onSurface = Bone
)

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MaterialTheme(colorScheme = PairingColors) {
                var screen by rememberSaveable { mutableIntStateOf(0) }
                val data by vm.data.collectAsState()
                val message by vm.message.collectAsState()
                val context = LocalContext.current
                LaunchedEffect(message) {
                    message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.consumeMessage() }
                }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = { Header(data.active.title, "${data.active.size}v${data.active.size}") },
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            val items = listOf(
                                Triple("Matrice", Icons.Rounded.GridOn, 0),
                                Triple("Analyse", Icons.Rounded.Insights, 1),
                                Triple("Pairing", Icons.Rounded.Style, 2),
                                Triple("Historique", Icons.Rounded.History, 3)
                            )
                            items.forEach { (label, icon, idx) ->
                                NavigationBarItem(
                                    selected = screen == idx, onClick = { screen = idx },
                                    icon = { Icon(icon, null) }, label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold)
                                )
                            }
                        }
                    }
                ) { pad ->
                    Column(Modifier.fillMaxSize().padding(pad)) {
                        when (screen) {
                            0 -> MatrixScreen(vm)
                            1 -> AnalysisScreen(vm)
                            2 -> PairingScreen(vm)
                            else -> HistoryScreen(vm)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** CSV / Excel ouvert ou partagé depuis une autre appli (Drive, mail…). */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            else -> null
        }
        if (uri != null) vm.importUri(uri)
        else if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.importText("Texte partagé", it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun Header(title: String, format: String) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(40.dp).clip(CircleShape))
                Column(Modifier.padding(start = 10.dp)) {
                    Text("PAIRING 40K", color = Gold, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
                    Text("$title · $format", color = Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    )
}
