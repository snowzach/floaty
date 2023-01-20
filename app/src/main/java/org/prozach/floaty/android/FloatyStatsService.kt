package org.prozach.floaty.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import org.prozach.floaty.android.ble.ConnectionEventListener
import org.prozach.floaty.android.ble.ConnectionManager
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.*

class FloatyStatsService : Service() {

    private val vescMainUUID = java.util.UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    private val vescCommandUUID = java.util.UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    private val vescCommandGetValues = ubyteArrayOf(0x04u)
    private val vescCommandGetBalanceValues = ubyteArrayOf(0x24u, 0x65u, 0x01u)
    private val CHANNEL_ID = "Floaty"

    private val timer: Timer = Timer()

    // https://www.techotopia.com/index.php/Android_Local_Bound_Services_%E2%80%93_A_Kotlin_Example
    private val binder = FloatyStatsBinder()

    private lateinit var device: BluetoothDevice
    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }
    private var notifyingCharacteristics = mutableListOf<UUID>()

    // https://github.com/vedderb/bldc/blob/a28eec80d95868a040c38a00c9caa6c238076983/comm/commands.c#L376
    data class VescValues(
        var avgMotorCurrent: Float = 0f,
        var avgInputCurrent: Float = 0f,
        var dutyCycleNow: Float = 0f,
        var rpm: Float = 0f,
        var inpVoltage: Float = 0f,
        var ampHours: Float = 0f,
        var ampHoursCharged: Float = 0f,
        var wattHours: Float = 0f,
        var wattHoursCharged: Float = 0f,
        var tachometer: Int = 0,
        var tachometerAbs: Int = 0,
        var tempMosfet: Float = 0f,
        var tempMotor: Float = 0f,
        var pidPos: Float = 0f,
    )
    var vescValues = VescValues();
    data class VescFloatValues(
        var pidOutput: Float = 0f,
        var pitchAngle: Float = 0f,
        var rollAngle: Float = 0f,
        var state: Int = 0,
        var switchState: Int = 0,
        var adc1: Float = 0f,
        var adc2: Float = 0f,
        var setPoint: Float = 0f,
        var atr: Float = 0f,
        var brakeTilt: Float = 0f,
        var torqueTilt: Float = 0f,
        var turnTilt: Float = 0f,
        var inputTilt: Float = 0f,
        var truePitchAngle: Float = 0f,
        var atrFilteredCurrent: Float = 0f,
        var accDiff: Float = 0f,
        var appliedBoosterCurrent: Float = 0f,
        var motorCurrent: Float = 0f,
    )
    var vescFloatValues = VescFloatValues();

    fun shutdown() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        stopForeground(true)
        stopSelf()
    }

    // Helpers to start/stop
    // https://androidwave.com/foreground-service-android-example-in-kotlin/
    companion object {
        fun startService(context: Context, message: String) {
            val startIntent = Intent(context, FloatyStatsService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, FloatyStatsService::class.java)
            context.stopService(stopIntent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId);
        println("onStartCommand")

        // Create a notification to keep it running
        createNotificationChannel()
        val notificationIntent = Intent(this, FloatyStatsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_MUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(this.resources.getString(R.string.app_name))
            .setContentText("Stats collection running...")
            .setSmallIcon(R.drawable.floaty)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)

        ConnectionManager.registerListener(connectionEventListener)
        device = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")

        for (characteristic in characteristics) {
            Timber.d("CHARACTERISTIC ${characteristic.uuid} C${characteristic.permissions} P:${characteristic.properties}")
            when (characteristic.uuid) {
                vescMainUUID -> {
                    ConnectionManager.enableNotifications(device, characteristic)
                    Timber.d("Enabling notifications from ${characteristic.uuid}")
                }
            }
        }

        // Poll vesc at 5 second interval
        // http://vedder.se/2015/10/communicating-with-the-vesc-using-uart/
        // https://github.com/RollingGecko/VescUartControl/blob/master/VescUart.cpp
        timer.scheduleAtFixedRate(writeCharacteristic(
            device,
            BluetoothGattCharacteristic(vescCommandUUID, 12, 0),
            encodeVescMessage(vescCommandGetValues)
        ), 0, 500)

        timer.scheduleAtFixedRate(writeCharacteristic(
            device,
            BluetoothGattCharacteristic(vescCommandUUID, 12, 0),
            encodeVescMessage(vescCommandGetBalanceValues)
        ), 0, 500)


        return START_NOT_STICKY;
    }

    private class writeCharacteristic(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, payload: ByteArray) : TimerTask() {
        val device = device
        val characteristic = characteristic
        val payload = payload
        override fun run() {
            ConnectionManager.writeCharacteristic(
                device,
                characteristic,
                payload
            )
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    inner class FloatyStatsBinder : Binder() {
        fun getService(): FloatyStatsService {
            return this@FloatyStatsService
        }
    }

    override fun onCreate() {
        Timber.d("onCreate")
        super.onCreate()
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        timer.cancel()
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                Timber.d("Disconnected")
            }

            onCharacteristicRead = { _, characteristic ->
                Timber.d("Read from ${characteristic.uuid}: ${characteristic.value.toHexString()}")
            }

            onCharacteristicWrite = { _, characteristic ->
                Timber.d("Wrote to ${characteristic.uuid}")
            }

            onCharacteristicChanged = { _, characteristic ->
                Timber.d("Characteristic ${characteristic}: ${characteristic.value.toHexString()}")

                if(characteristic.value[0] == 0x02.toByte() && characteristic.value.size > 5) {
                    val messageLength = characteristic.value[1].toInt()
                    if(messageLength == characteristic.value.size-5) {
                        // Get the internal message
                        val message = characteristic.value.copyOfRange(2, messageLength+2)
                        // Check the CRC
                        val crc = crc16(message.toUByteArray()).to2UByteArrayInBigEndian()
                        if(crc[0] == characteristic.value[messageLength+2].toUByte() &&
                            crc[1] == characteristic.value[messageLength+3].toUByte()) {
                            decodeMessageAndBroadcast(message)
                        } else {
                            Timber.e("Message invalid crc")
                        }
                    } else {
                        Timber.e("Message invalid length")
                    }
                } else {
                    Timber.e("Message invalid length too small")
                }
            }

            onNotificationsEnabled = { _, characteristic ->
                Timber.d("Enabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                Timber.d("Disabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.remove(characteristic.uuid)
            }
        }
    }

    fun decodeMessageAndBroadcast(message: ByteArray) {

        when(message[0].toUByte()) {
            vescCommandGetValues[0] -> {
                val buffer = ByteBuffer.wrap(message)
                vescValues.tempMosfet = buffer.getShort(1).toFloat()/10.0f
                vescValues.tempMotor = buffer.getShort(3).toFloat()/10.0f
                vescValues.avgMotorCurrent = buffer.getInt(5).toFloat()/100.0f
                vescValues.avgInputCurrent = buffer.getInt(9).toFloat()/100.0f
                vescValues.dutyCycleNow = buffer.getShort(21).toFloat()/1000.0f
                vescValues.rpm = buffer.getInt(23).toFloat()
                vescValues.inpVoltage = buffer.getShort(27).toFloat()/10.0f
                vescValues.ampHours = buffer.getInt(29).toFloat()/10000.0f
                vescValues.ampHoursCharged = buffer.getInt(33).toFloat()/10000.0f
                vescValues.wattHours = buffer.getInt(37).toFloat()/10000.0f
                vescValues.wattHoursCharged = buffer.getInt(41).toFloat()/10000.0f
                vescValues.tachometer = buffer.getInt(45)
                vescValues.tachometerAbs = buffer.getInt(49)
                Timber.d("GOT VALUES ${vescValues.toString()}")

                var intent = Intent("com.prozach.floaty.android.stats");
                intent.putExtra("vescValues", Gson().toJson(vescValues));
                sendBroadcast(intent)
            }
            vescCommandGetBalanceValues[0] -> {
//                https://github.com/vedderb/vesc_pkg/blob/b3927886511734c990de232da75d5efc3f94fdeb/float/float/float.c#L1880
                val buffer = ByteBuffer.wrap(message)
                vescFloatValues.pidOutput = buffer.getFloat(3)
                vescFloatValues.pitchAngle = buffer.getFloat(7)
                vescFloatValues.rollAngle = buffer.getFloat(11)
                vescFloatValues.state = buffer.get(15).toInt()
                vescFloatValues.switchState = buffer.get(16).toInt()
                vescFloatValues.adc1 = buffer.getFloat(17)
                vescFloatValues.adc2 = buffer.getFloat(21)
                vescFloatValues.setPoint = buffer.getFloat(25)
                vescFloatValues.atr = buffer.getFloat(29)
                vescFloatValues.brakeTilt = buffer.getFloat(33)
                vescFloatValues.torqueTilt = buffer.getFloat(37)
                vescFloatValues.turnTilt = buffer.getFloat(41)
                vescFloatValues.inputTilt = buffer.getFloat(45)
                vescFloatValues.truePitchAngle = buffer.getFloat(49)
                vescFloatValues.atrFilteredCurrent = buffer.getFloat(53)
                vescFloatValues.accDiff = buffer.getFloat(57)
                vescFloatValues.appliedBoosterCurrent = buffer.getFloat(61)
                vescFloatValues.motorCurrent = buffer.getFloat(65)
                Timber.d("GOT BALANCE VALUES ${vescFloatValues.toString()}")

                var intent = Intent("com.prozach.floaty.android.stats");
                intent.putExtra("vescFloatValues", Gson().toJson(vescFloatValues));
                sendBroadcast(intent)
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun encodeVescMessage(message: UByteArray): ByteArray {
        var encodedMessage: UByteArray = ubyteArrayOf();
        encodedMessage+=0x2u; // Short Length Only (if larger than 255 need to modify)
        encodedMessage+=message.size.toUByte();
        encodedMessage+=message;
        val crc = crc16(message);
        encodedMessage+=crc.to2UByteArrayInBigEndian()
        encodedMessage += 0x03u;
        return encodedMessage.toByteArray();
    }
}



