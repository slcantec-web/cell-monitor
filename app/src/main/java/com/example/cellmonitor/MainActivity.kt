package com.example.cellmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.cellmonitor.data.CellMonitorRepository
import com.example.cellmonitor.ui.CellMonitorScreen
import com.example.cellmonitor.ui.theme.CellMonitorTheme
import com.example.cellmonitor.update.UpdateChecker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: CellMonitorRepository
    private lateinit var updateChecker: UpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = CellMonitorRepository(
            context = applicationContext,
            scope = lifecycleScope
        )
        updateChecker = UpdateChecker(applicationContext)

        setContent {
            CellMonitorTheme {
                CellMonitorScreen(
                    repository = repository,
                    updateChecker = updateChecker
                )
            }
        }

        // Cold start: always hit the network so a new release is announced without tapping anything
        lifecycleScope.launch {
            updateChecker.check(force = true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) {
            repository.checkPermissions()
            repository.refresh()
        }
        if (::updateChecker.isInitialized) {
            lifecycleScope.launch {
                // Cooldown applies; cache still refreshes the in-app banner
                updateChecker.check(force = false)
            }
        }
    }
}
