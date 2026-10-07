package one.brj.bikebus

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import one.brj.bikebus.ui.TripScreen
import one.brj.bikebus.ui.theme.BikeBusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) {
                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            BikeBusTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TripScreen()
                }
            }
        }
    }
}
