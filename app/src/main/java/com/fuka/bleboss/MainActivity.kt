package com.fuka.bleboss

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fuka.bleboss.ui.theme.BLEbossTheme
import java.util.UUID

// Bluetooth-enabling action we’ll soon request from the user. It can be any positive integer value
private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val RUNTIME_PERMISSION_REQUEST_CODE = 2
private const val TAG = "BLEBossss"

class MainActivity : ComponentActivity() {

    /*
    * By deferring the initialization of bluetoothAdapter and also bleScanner to when we actually need them,
    * we avoid a crash that would happen if bluetoothAdapter was initialized before onCreate() has returned.*/

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    // getSystemService() function is only available after onCreate() has already been called for our Activity
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }


    // You can tweak your own scan settings
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanResults = mutableListOf<ScanResult>() // List of devices found

    // create an object that implements the functions in ScanCallback so that we’ll be notified when a scan result is available
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
            } else {
                with(result.device) {
                    Log.i(TAG, "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")

                }
                Log.d(TAG, "onScanResult RSSI: ${result.rssi}")
                Log.i(TAG, "Service UUID count: ${result.device.uuids?.size ?: "no UUIDs"}")
                /*result.device.uuids?.let {
                    it.forEach { UUID -> Log.d(TAG, "Service UUID: ${UUID}") }
                }*/
                scanResults.add(result)
            }

            //Log.d(TAG, "onScanResult: list size: ${scanResults.size}")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "onScanFailed: code $errorCode")
        }

    }



    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w(TAG, "Successfully connected to $deviceAddress")

                    //gatt.discoverServices()


                    // Note that service discovery is being performed on the main thread!
                    val bluetoothGatt = gatt
                    Handler(Looper.getMainLooper()).post {
                        if (ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        )
                        bluetoothGatt?.discoverServices()
                    }

                    // BluetoothGatt is the gateway to other BLE operations such as service discovery,
                    // reading and writing data, and even performing a connection teardown.
                    // TODO: Store a reference to BluetoothGatt
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(TAG, "Successfully disconnected from $deviceAddress")
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    )
                    gatt.close()
                }
            } else {
                Log.w(TAG, "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w(TAG, "Discovered ${services.size} services for ${device.address}")
                printGattTable() // See implementation just above this section
                // Consider connection setup as complete here
            }
        }


        /*override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            gatt?.printGattTable()
        }*/


    }

    // extension function that prints out all the UUIDs of available services and characteristics that the BluetoothGatt of a BLE device has to offer
    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i(TAG, "No service and characteristic available, call discoverServices() first?")
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i(TAG, "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

            var isScanning by rememberSaveable { mutableStateOf(false) }
            var list by rememberSaveable { mutableStateOf(scanResults) }

            //val setList: () -> Unit = { list = scanResults }

            val onStartScanning: () -> Unit = { isScanning = true }
            val onStopScanning: () -> Unit = { isScanning = false }
            //val onSetScanResult: (scanResultList: MutableList<ScanResult>) -> Unit = { scanResultList -> scanResult = scanResultList. }

            val context = LocalContext.current

            BLEbossTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp),
                    color = MaterialTheme.colors.background
                ) {
                    Column {
                        Button(
                            enabled = !isScanning,
                            onClick = { startBleScan(onStart = onStartScanning) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "START BLE scan")
                        }

                        Button(
                            enabled = isScanning,
                            onClick = { stopBleScan(onStop = onStopScanning) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "STOP BLE scan")
                        }

                        Button(
                            enabled = !isScanning,
                            onClick = { Log.d(TAG, "size: ${list.size}") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Display list of devices")
                        }

                        LazyColumn {
                            items(
                                //items = listOf("Eka", "Toka", "Kolmas", "Neljäs")
                                items = list
                            ) { item ->
                                if (ActivityCompat.checkSelfPermission(
                                        LocalContext.current,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                )
                                item.device.name?.let {
                                    Row(modifier = Modifier
                                        .clickable {
                                            item.device.connectGatt(
                                                context,
                                                false,
                                                gattCallback
                                            )
                                        }
                                        .height(65.dp)
                                        //.background(Color.DarkGray)
                                        //.padding(vertical = 20.dp)
                                        .fillMaxWidth()
                                    ) {
                                        Text(text = item.device.name ?: "Unnamed")
                                        Spacer(modifier = Modifier.padding(horizontal = 5.dp))
                                        Text(text = item.device.address)
                                        Spacer(modifier = Modifier.padding(horizontal = 5.dp))
                                        Text(text = item.rssi.toString())
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
    }

    private fun startBleScan(onStart: () -> Unit) {
        Log.d(TAG, "startBleScan: no BLE runtime permission ")
        // TODO: Check runtime permissions
        scanResults.clear()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        )
        bleScanner.startScan(null, scanSettings, scanCallback)
        onStart()
    }

    private fun stopBleScan(onStop: () -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        )
        bleScanner.stopScan(scanCallback)
        onStop()
    }


    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            )
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }


    /*
    * The two functions above are extension functions on the Context object,
    * meaning each Context instance with access to these functions can call them and use them as
    * if these functions were part of the original class declaration. Depending on how you structure your source code,
    * these extension functions may reside in another Kotlin source file so that they can be accessed by other Activities as well.
    * */

    // Request runtime permission from the user: in your Activity, ViewModel, or at least in a Context

    fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permissionType
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun Context.hasRequiredRuntimePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }


    /* override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RUNTIME_PERMISSION_REQUEST_CODE -> {
                val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
                    it.second == PackageManager.PERMISSION_DENIED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(this, it.first)
                }
                val containsDenial = grantResults.any { it == PackageManager.PERMISSION_DENIED }
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                when {
                    containsPermanentDenial -> {
                        // TODO: Handle permanent denial (e.g., show AlertDialog with justification)
                        // Note: The user will need to navigate to App Settings and manually grant
                        // permissions that were permanently denied
                    }
                    containsDenial -> {
                        requestRelevantRuntimePermissions()
                    }
                    allGranted && hasRequiredRuntimePermissions() -> {
                        startBleScan()
                    }
                    else -> {
                        // Unexpected scenario encountered when handling permissions
                        recreate()
                    }
                }
            }
        }
    }

    private fun Activity.requestRelevantRuntimePermissions() {
        if (hasRequiredRuntimePermissions()) { return }
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                requestLocationPermission()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                requestBluetoothPermissions()
            }
        }
    }


    private fun requestLocationPermission() {
        runOnUiThread {
            alert {
                title = "Location permission required"
                message = "Starting from Android M (6.0), the system requires apps to be granted " +
                        "location access in order to scan for BLE devices."
                isCancelable = false
                positiveButton(android.R.string.ok) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        RUNTIME_PERMISSION_REQUEST_CODE
                    )
                }
            }.show()
        }
    }

    private fun requestBluetoothPermissions() {
        runOnUiThread {
            alert {
                title = "Bluetooth permissions required"
                message = "Starting from Android 12, the system requires apps to be granted " +
                        "Bluetooth access in order to scan for and connect to BLE devices."
                isCancelable = false
                positiveButton(android.R.string.ok) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ),
                        RUNTIME_PERMISSION_REQUEST_CODE
                    )
                }
            }.show()
        }
    }*/


    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }


}
