package de.treker.habits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import de.treker.habits.ui.HabitViewModel
import de.treker.habits.ui.TrekerApp

class MainActivity : ComponentActivity() {
    private val viewModel: HabitViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TrekerApp(viewModel) }
    }
}
