package com.jaivish.gowork

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jaivish.gowork.data.Graph
import com.jaivish.gowork.service.GuardService
import com.jaivish.gowork.ui.AppRoot
import com.jaivish.gowork.ui.theme.GoWorkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            GoWorkTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The app is in the foreground here, so Android always allows starting the guard.
        if (Graph.settings.current.onboarded) GuardService.start(this)
    }
}
