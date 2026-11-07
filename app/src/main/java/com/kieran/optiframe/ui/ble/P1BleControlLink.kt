package com.kieran.optiframe.ui.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.kieran.optiframe.protocol.P1ControlMessage
import com.kieran.optiframe.protocol.P1ControlMessageCodec
import java.util.ArrayDeque
import java.util.UUID

/** M4's versioned GATT profile; its message format is defined in protocol-core. */
object P1BleControlProfile {
    val SERVICE_UUID: UUID = UUID.fromString("a5f10000-7e40-4b34-9ea0-0b63d2a97a11")
    val FEEDBACK_UUID: UUID = UUID.fromString("a5f10001-7e40-4b34-9ea0-0b63d2a97a11")
}

/** Sender-side GATT peripheral: advertises a write-only receiver feedback characteristic. */
class P1BleControlServer(
    private val context: Context,
    private val sessionId: Long,
    private val onMessage: (P1ControlMessage) -> Unit,
    private val onState: (String) -> Unit = {}
) : AutoCloseable {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter = checkNotNull(manager.adapter) { "Bluetooth is unavailable" }
    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    @SuppressLint("MissingPermission")
    fun start() {
        check(server == null) { "BLE control server is already running" }
        val gattServer = manager.openGattServer(context, object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                if (status == BluetoothGatt.GATT_SUCCESS && service.uuid == P1BleControlProfile.SERVICE_UUID) {
                    runCatching { startAdvertising() }
                        .onFailure { onState("BLE advertising could not start: ${it.message ?: "error"}") }
                } else onState("Could not register M4 BLE service ($status)")
            }

            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                onState(if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS)
                    "BLE receiver connected" else "BLE receiver disconnected")
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                val response = runCatching {
                    require(characteristic.uuid == P1BleControlProfile.FEEDBACK_UUID)
                    require(!preparedWrite && offset == 0) { "Prepared or offset BLE writes are unsupported" }
                    val message = P1ControlMessageCodec.decode(value.copyOf())
                    require(message.sessionId == sessionId) { "BLE message belongs to another session" }
                    onMessage(message)
                }
                if (responseNeeded) {
                    server?.sendResponse(
                        device,
                        requestId,
                        if (response.isSuccess) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                        0,
                        null
                    )
                }
                response.exceptionOrNull()?.let { onState("BLE control rejected: ${it.message ?: "invalid message"}") }
            }
        }) ?: error("Could not open BLE GATT server")
        server = gattServer
        try {
            advertiser = adapter.bluetoothLeAdvertiser ?: error("BLE advertising is unavailable on this device")
            val service = BluetoothGattService(P1BleControlProfile.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(
                BluetoothGattCharacteristic(
                    P1BleControlProfile.FEEDBACK_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                )
            )
            check(gattServer.addService(service)) { "Could not register M4 BLE control service" }
        } catch (error: Exception) {
            gattServer.close()
            server = null
            advertiser = null
            throw error
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val leAdvertiser = checkNotNull(advertiser) { "BLE advertiser is unavailable" }
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                onState("BLE feedback ready")
            }
            override fun onStartFailure(errorCode: Int) {
                onState("BLE advertising failed ($errorCode)")
            }
        }
        advertiser = leAdvertiser
        advertiseCallback = callback
        leAdvertiser.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .build(),
            AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(P1BleControlProfile.SERVICE_UUID))
                .build(),
            callback
        )
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        advertiseCallback?.let { callback -> advertiser?.stopAdvertising(callback) }
        advertiseCallback = null
        advertiser = null
        server?.close()
        server = null
    }
}

/** Receiver-side GATT central: scans for the session service and serializes control writes. */
class P1BleControlClient(
    private val context: Context,
    private val sessionId: Long,
    private val onState: (String) -> Unit = {}
) : AutoCloseable {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter = checkNotNull(manager.adapter) { "Bluetooth is unavailable" }
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null
    private val pendingWrites = ArrayDeque<ByteArray>()
    private var writing = false

    @SuppressLint("MissingPermission")
    fun startScan() {
        check(gatt == null) { "BLE control client is already connected" }
        val leScanner = adapter.bluetoothLeScanner ?: error("BLE scanning is unavailable on this device")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                stopScan()
                onState("BLE sender found; connecting")
                gatt = result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }
            override fun onScanFailed(errorCode: Int) {
                onState("BLE scan failed ($errorCode)")
            }
        }
        scanner = leScanner
        scanCallback = callback
        leScanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(P1BleControlProfile.SERVICE_UUID)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback
        )
        onState("Searching for the sender's BLE service")
    }

    @SuppressLint("MissingPermission")
    fun send(message: P1ControlMessage) {
        require(message.sessionId == sessionId) { "BLE message belongs to another session" }
        val bytes = P1ControlMessageCodec.encode(message)
        synchronized(pendingWrites) {
            pendingWrites.addLast(bytes)
            writeNextLocked()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                controlCharacteristic = null
                gatt.close()
                this@P1BleControlClient.gatt = null
                synchronized(pendingWrites) { writing = false }
                onState("BLE control disconnected")
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                onState("BLE connected; discovering control service")
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(P1BleControlProfile.SERVICE_UUID)
            controlCharacteristic = service?.getCharacteristic(P1BleControlProfile.FEEDBACK_UUID)
            onState(if (status == BluetoothGatt.GATT_SUCCESS && controlCharacteristic != null)
                "BLE feedback connected" else "M4 BLE service is unavailable")
            synchronized(pendingWrites) { writeNextLocked() }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            synchronized(pendingWrites) {
                writing = false
                if (status != BluetoothGatt.GATT_SUCCESS) onState("BLE feedback write failed ($status)")
                writeNextLocked()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeNextLocked() {
        if (writing) return
        val activeGatt = gatt ?: return
        val characteristic = controlCharacteristic ?: return
        val payload = pendingWrites.pollFirst() ?: return
        writing = true
        val accepted = if (Build.VERSION.SDK_INT >= 33) {
            activeGatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = payload
            activeGatt.writeCharacteristic(characteristic)
        }
        if (!accepted) {
            writing = false
            pendingWrites.addFirst(payload)
            onState("BLE feedback could not be queued")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val callback = scanCallback
        if (callback != null) scanner?.stopScan(callback)
        scanCallback = null
        scanner = null
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        stopScan()
        synchronized(pendingWrites) { pendingWrites.clear(); writing = false }
        controlCharacteristic = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
