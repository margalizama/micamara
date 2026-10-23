package com.micamara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MiCamaraTheme { App() }
        }
    }

    override fun onStart() {
        super.onStart()
        WifiBinder.bind(this)
    }
}

sealed interface Screen {
    data object Home : Screen
    data class Edit(val id: String?) : Screen
    data class Live(val id: String) : Screen
}

@Composable
fun App() {
    val context = LocalContext.current
    val repo = remember { CameraRepository(context) }
    var cameras by remember { mutableStateOf(repo.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    fun persist(list: List<Camera>) {
        cameras = list
        repo.save(list)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                cameras = cameras,
                onOpen = { screen = Screen.Live(it.id) },
                onEdit = { screen = Screen.Edit(it.id) },
                onAdd = { screen = Screen.Edit(null) },
            )

            is Screen.Edit -> {
                BackHandler { screen = Screen.Home }
                val existing = cameras.firstOrNull { it.id == s.id }
                val deleteAction: (() -> Unit)? = if (existing == null) null else fun() {
                    persist(cameras.filter { it.id != existing.id })
                    screen = Screen.Home
                }
                EditScreen(
                    camera = existing,
                    onSave = { cam ->
                        persist(
                            if (existing != null) cameras.map { if (it.id == cam.id) cam else it }
                            else cameras + cam,
                        )
                        screen = Screen.Home
                    },
                    onDelete = deleteAction,
                    onBack = { screen = Screen.Home },
                )
            }

            is Screen.Live -> {
                BackHandler { screen = Screen.Home }
                val cam = cameras.firstOrNull { it.id == s.id }
                if (cam != null) {
                    LiveScreen(
                        camera = cam,
                        onBack = { screen = Screen.Home },
                        onCameraUpdated = { updated ->
                            persist(cameras.map { if (it.id == updated.id) updated else it })
                        },
                    )
                }
            }
        }
    }
}
