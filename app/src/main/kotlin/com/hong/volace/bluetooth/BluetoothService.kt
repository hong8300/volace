package com.hong.volace.bluetooth

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.hong.volace.widget.WidgetScope

private const val TAG = "VolaceBluetooth"

/**
 * Runs for the few seconds a switch takes (waiting for the audio route included). Started by the
 * exact alarm, which may start a foreground service from the background (DESIGN.md 5.18, 8.7).
 */
class BluetoothService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = BluetoothNotifications.running(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(BluetoothNotifications.RUNNING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // API 33 has no "special use" type; the manifest's is used.
            startForeground(BluetoothNotifications.RUNNING_ID, notification)
        }
        val app = applicationContext
        // Not tied to the service's lifetime: stopping it must not cut the redraw short.
        WidgetScope.run(null) {
            try {
                BluetoothSwitch.process(app)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        /** False when Android refused to start it. */
        fun start(context: Context): Boolean =
            runCatching { context.startForegroundService(Intent(context, BluetoothService::class.java)) }
                .onFailure { Log.w(TAG, "could not start the Bluetooth service", it) }
                .isSuccess
    }
}

/**
 * A device connected or disconnected. Exported: the Bluetooth stack sends these from its own uid,
 * not the system's (a non-exported receiver never got them); the manifest asks the sender for
 * BLUETOOTH_CONNECT, and only devices with a rule are acted on.
 */
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED && action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val app = context.applicationContext
        WidgetScope.run(goAsync()) {
            BluetoothSwitch.onEvent(app, device.address, connected = action == BluetoothDevice.ACTION_ACL_CONNECTED)
        }
    }
}

/** The alarm due right after an event: starts [BluetoothService]. */
class BluetoothAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PROCESS) return
        val app = context.applicationContext
        if (BluetoothService.start(app)) return
        // Switching from here is likely ignored on Android 17, but it is read back and then
        // reported as failed, with the notification to switch on a tap.
        WidgetScope.run(goAsync()) { BluetoothSwitch.process(app) }
    }

    companion object {
        const val ACTION_PROCESS = "com.hong.volace.action.BLUETOOTH_PROCESS"
    }
}
