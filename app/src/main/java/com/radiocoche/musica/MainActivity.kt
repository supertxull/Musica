package com.radiocoche.musica

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.radiocoche.musica.ui.DayNight
import com.radiocoche.musica.ui.LibraryScreen
import com.radiocoche.musica.ui.PlayerScreen

class MainActivity : ComponentActivity() {

    private val vm: MusicViewModel by viewModels()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Al encender o apagar las luces, la unidad avisa y cambiamos los colores. */
    private val dayNightReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            DayNight.apply()
            mainHandler.postDelayed({ DayNight.apply() }, 500)
        }
    }

    /** Al pinchar o quitar un USB o la tarjeta SD. */
    private val mountReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mainHandler.postDelayed({ vm.refreshSources() }, 1500)
        }
    }

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermission(hasReadPermission())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DayNight.apply()

        ContextCompat.registerReceiver(this, dayNightReceiver, IntentFilter(DayNight.ACTION), ContextCompat.RECEIVER_EXPORTED)
        val mountFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(this, mountReceiver, mountFilter, ContextCompat.RECEIVER_EXPORTED)

        setContent {
            BackHandler(enabled = vm.screen == Screen.LIBRARY) {
                if (vm.canGoUp) vm.goUp() else vm.screen = Screen.PLAYER
            }
            when (vm.screen) {
                Screen.PLAYER -> PlayerScreen(vm)
                Screen.LIBRARY -> LibraryScreen(vm)
            }
        }

        if (hasReadPermission()) vm.onPermission(true) else askPermission.launch(neededPermissions())
    }

    override fun onResume() {
        super.onResume()
        DayNight.apply()
        if (hasReadPermission()) {
            if (!vm.hasPermission) vm.onPermission(true) else vm.refreshSources()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dayNightReceiver) }
        runCatching { unregisterReceiver(mountReceiver) }
        super.onDestroy()
    }

    private fun readPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun neededPermissions(): Array<String> {
        val list = mutableListOf(readPermission())
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list.toTypedArray()
    }

    private fun hasReadPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            ContextCompat.checkSelfPermission(this, readPermission()) == PackageManager.PERMISSION_GRANTED
}
