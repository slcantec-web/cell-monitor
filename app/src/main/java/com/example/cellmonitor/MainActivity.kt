package com.example.cellmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.cellmonitor.data.CellMonitorRepository
import com.example.cellmonitor.ui.CellMonitorScreen
import com.example.cellmonitor.ui.theme.CellMonitorTheme

class MainActivity : ComponentActivity() {
    private lateinit var repository: CellMonitorRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = CellMonitorRepository(
            context = applicationContext,
            scope = lifecycleScope
        )

        setContent {
            CellMonitorTheme {
                CellMonitorScreen(repository = repository)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) {
            repository.checkPermissions()
            repository.refresh()
        }
    }
}
