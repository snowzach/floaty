package org.prozach.floaty.android

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import org.prozach.floaty.android.databinding.ActivityFloatyStatsBinding
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FloatyStatsActivity : AppCompatActivity() {

    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)

    private var receiverRegistered: Boolean = false

    private lateinit var binding: ActivityFloatyStatsBinding

    companion object {
        private const val REQUEST_CODE_DRAW_OVERLAY_PERMISSION = 5
    }

    var statsService: FloatyStatsService? = null
    var isBound = false
    private val FloatyStatsServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Timber.d("Activity Connected")
            val binder = service as FloatyStatsService.FloatyStatsBinder
            statsService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.d("Activity Disconnected")
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("ONCREATE")
        super.onCreate(savedInstanceState)

        binding = ActivityFloatyStatsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        var intent = Intent(this, FloatyStatsService::class.java)
        bindService(intent, FloatyStatsServiceConnection, BIND_AUTO_CREATE)

        val filter = IntentFilter()
        filter.addAction("com.prozach.floaty.android.stats")
        registerReceiver(broadcastHandler, filter)
        receiverRegistered = true

//      Send action to stats service
//        binding.icResetStats.setOnClickListener{
//            statsService?.clearStats()
//        }
    }

    private val broadcastHandler: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Timber.d("onReceive")
            runOnUiThread {
                val vescValuesJSON = intent.getStringExtra("vescValues")
                if(!vescValuesJSON.isNullOrEmpty()) {
                    var vescValues: FloatyStatsService.VescValues = Gson().fromJson(vescValuesJSON, FloatyStatsService.VescValues::class.java)
                    binding.logTextView.text = vescValues.toString()
                }
                val vescFloatValuesJSON = intent.getStringExtra("vescFloatValues")
                if(!vescFloatValuesJSON.isNullOrEmpty()) {
                    var vescFloatValues: FloatyStatsService.VescFloatValues = Gson().fromJson(vescFloatValuesJSON, FloatyStatsService.VescFloatValues::class.java)
                    binding.logTextView2.text = vescFloatValues.toString()
                }
            }

        }
    }

    override fun onBackPressed() {
        statsService?.shutdown()
        super.onBackPressed() // Don't call this
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(FloatyStatsServiceConnection)
        if (receiverRegistered) {
            unregisterReceiver(broadcastHandler)
            receiverRegistered = false
        }
    }

    override fun onPause() {
        super.onPause()
        if (receiverRegistered) {
            unregisterReceiver(broadcastHandler)
            receiverRegistered = false
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter()
        filter.addAction("com.prozach.floaty.android.stats")
        if (!receiverRegistered) {
            registerReceiver(broadcastHandler, filter)
            receiverRegistered = true
        }

    }
}

