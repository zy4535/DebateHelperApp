package com.example.debatehelperapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.debatehelperapp.ui.screens.DebateScreen
import com.example.debatehelperapp.ui.theme.DebateHelperAppTheme
import com.example.debatehelperapp.viewmodel.DebateViewModel

class MainActivity : ComponentActivity() {

    // Instantiate the ViewModel bound to the lifecycle of this activity
    private val debateViewModel: DebateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DebateHelperAppTheme {
                // Call the main screen and pass the ViewModel
                DebateScreen(viewModel = debateViewModel)
            }
        }
    }
}