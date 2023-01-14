package org.prozach.floaty.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.prozach.floaty.android.ble.ConnectionEventListener
import org.prozach.floaty.android.ble.ConnectionManager
import org.prozach.floaty.android.databinding.ActivityMainBinding
import timber.log.Timber


private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
//private const val LOCATION_PERMISSION_REQUEST_CODE = 2

class MainActivity : AppCompatActivity() {

    // This is the list of UUIDs associated with VESC
    private val searchDeviceUUIDs: Array<String> = arrayOf("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private lateinit var binding: ActivityMainBinding

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread { binding.scanButton.text = if (value) "Stop Scan" else "Start Scan" }
        }

    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter: ScanResultAdapter by lazy {
        ScanResultAdapter(scanResults) { result ->
            if (isScanning) {
                stopBleScan()
            }
            with(result.device) {
                Timber.w("Connecting to $address")
                ConnectionManager.connect(this, this@MainActivity)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        binding.scanButton.setOnClickListener { if (isScanning) stopBleScan() else startBleScan() }
        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        ConnectionManager.registerListener(connectionEventListener)
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    private fun startBleScan() {
        if (!ensurePermissions()) return

        promptEnableBluetooth()

        var filters = ArrayList<android.bluetooth.le.ScanFilter>()
        for (deviceUUID in searchDeviceUUIDs) {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(deviceUUID))
                .build()
                filters.add(filter)
        }
        scanResults.clear()
        scanResultAdapter.notifyDataSetChanged()
        bleScanner.startScan(filters, scanSettings, scanCallback)
        isScanning = true

    }

    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    private fun ensurePermissions() : Boolean {
        var requestingPermissions = arrayOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) requestingPermissions += Manifest.permission.BLUETOOTH_SCAN
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) requestingPermissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) requestingPermissions += Manifest.permission.ACCESS_FINE_LOCATION
        if (!requestingPermissions.isEmpty()) runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Location permission required");
            builder.setMessage("Starting from Android M (6.0), the system requires apps to be granted " +
                    "location access in order to scan for BLE devices.")
            builder.setCancelable(false)
            builder.setPositiveButton(android.R.string.ok){dialogInterface, which->
                requestMultiplePermissions.launch(requestingPermissions)
            }
            builder.show()
        }
        return requestingPermissions.isEmpty()
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Timber.d("test006", "${it.key} = ${it.value}")
            }
        }

    private fun setupRecyclerView() {
        binding.scanResultsRecyclerView.apply {
            adapter = scanResultAdapter
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = binding.scanResultsRecyclerView.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
                    Timber.i("Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("onScanFailed: code $errorCode")
        }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = { gatt ->
                // Start the stats service
                Intent(this@MainActivity, FloatyStatsService::class.java).also {
                    it.putExtra(BluetoothDevice.EXTRA_DEVICE, gatt.device)
                    startService(it)
                }
                // Open the stats view
                ConnectionManager.unregisterListener(this)
                Intent(this@MainActivity, FloatyStatsActivity::class.java).also {
                    startActivity(it)
                }
            }
            onDisconnect = {
                runOnUiThread {
                    showToast("Disconnected from device")
                }
            }
            onConnectionFailed = { gatt ->
                runOnUiThread {
                    showToast("Connection failed. Try Again. It may take several tries.")
                }
            }
        }
    }

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
            PackageManager.PERMISSION_GRANTED
    }

}
