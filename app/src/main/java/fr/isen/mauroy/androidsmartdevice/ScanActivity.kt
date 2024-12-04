package fr.isen.mauroy.androidsmartdevice

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager // <-- Ensure this import is included
import androidx.compose.ui.platform.LocalContext // <-- Add this import
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult

class ScanActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private lateinit var enableBtLauncher: ActivityResultLauncher<Intent>

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning by mutableStateOf(false)

    private val scanTimeout = 30_000L  // 30 seconds
    private var scanReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register the Bluetooth state change receiver
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (BluetoothAdapter.ACTION_STATE_CHANGED == action) {
                    val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        // Bluetooth is enabled, proceed with scan
                        startBluetoothScan()
                    }
                }
            }
        }
        registerReceiver(scanReceiver, filter)

        // Register the activity result launcher to handle Bluetooth enable intent
        enableBtLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Bluetooth enabled, proceed with scanning
                startBluetoothScan()
            } else {
                // Bluetooth not enabled
                Toast.makeText(this, "Bluetooth is required to scan devices", Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            ScanScreen(
                isScanning = isScanning,
                onScanButtonClick = { checkAndEnableBluetooth() },
                onPermissionRequest = { requestPermissions() }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the Bluetooth state change receiver
        scanReceiver?.let { unregisterReceiver(it) }
    }

    private fun checkAndEnableBluetooth() {
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            // Bluetooth is not enabled, request user to enable it
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        } else {
            // Bluetooth is already enabled, check permissions and proceed to scan
            checkPermissionsAndScan()
        }
    }

    private fun checkPermissionsAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permissions are not granted, request them
            requestPermissions()
        } else {
            // Permissions granted, start scanning
            startBluetoothScan()
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            1
        )
    }

    private fun startBluetoothScan() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }

        val success = bluetoothAdapter?.startDiscovery() ?: false
        isScanning = true
        // Timeout for the scan after 30 seconds
        handler.postDelayed({
            stopScan("Scan Timeout")
        }, scanTimeout)

        val message = if (success) "Bluetooth scan started" else "Failed to start Bluetooth scan"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun stopScan(message: String) {
        bluetoothAdapter?.cancelDiscovery()
        isScanning = false
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ScanScreen(isScanning: Boolean, onScanButtonClick: () -> Unit, onPermissionRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Scan for Bluetooth Devices",
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            Text(text = "Scanning...", fontSize = 16.sp)
        } else {
            Button(onClick = onScanButtonClick) {
                Text(text = "Start Scan")
            }
        }

        // If permissions are not granted, show a prompt
        val permissionsNotGranted = ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
        if (permissionsNotGranted) {
            Button(onClick = onPermissionRequest) {
                Text(text = "Request Permissions")
            }
        }
    }
}
