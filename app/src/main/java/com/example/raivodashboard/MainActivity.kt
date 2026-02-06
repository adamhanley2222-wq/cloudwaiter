package com.example.raivodashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.raivodashboard.ui.DashboardScreen
import com.example.raivodashboard.ui.DashboardViewModel
import com.example.raivodashboard.ui.theme.RaivoDashboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RaivoDashboardTheme {
                DashboardScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    RaivoDashboardTheme {
        DashboardScreen(viewModel = DashboardViewModel())
    }
}
