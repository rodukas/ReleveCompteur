package com.relevecompteur.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.relevecompteur.app.ui.MainViewModel
import com.relevecompteur.app.ui.navigation.ReleveCompteurApp
import com.relevecompteur.app.ui.theme.ReleveCompteurTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReleveCompteurTheme {
                ReleveCompteurApp(viewModel = mainViewModel)
            }
        }
    }
}

