package com.example.xprinterapp

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.xprinterapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * XPrinterBT — ứng dụng in nhãn qua Bluetooth cho máy in nhiệt Xprinter XP-428A.
 *
 * App chỉ hỗ trợ kết nối Bluetooth (Classic SPP). Không hỗ trợ USB vì trên
 * Android, kết nối Bluetooth là phương án phổ biến, không cần cáp OTG hay
 * phần cứng USB Host, phù hợp với hầu hết điện thoại/máy tính bảng.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bluetoothHelper: BluetoothPrinterHelper

    private var pairedDevices: List<BluetoothDevice> = emptyList()

    // Vị trí thiết bị đang được chọn trong dropdown (-1 = chưa chọn).
    // Được reset về -1 mỗi khi danh sách được làm mới, tránh chọn nhầm
    // thiết bị cũ nếu danh sách thay đổi thứ tự/số lượng.
    private var selectedBluetoothPosition = -1

    // Thiết bị đang kết nối (null = chưa kết nối). Đây là nguồn sự thật
    // DUY NHẤT cho trạng thái kết nối, tránh lệch trạng thái như bản trước.
    private var connectedDevice: BluetoothDevice? = null

    // Cờ chống bấm nút liên tục khi đang xử lý kết nối/in
    private var isBusy = false

    // Lưu lại hành động cần chạy sau khi người dùng cấp quyền Bluetooth
    private var pendingPermissionAction: (() -> Unit)? = null

    companion object {
        private const val REQUEST_BT_PERMISSIONS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothHelper = BluetoothPrinterHelper(this)

        setupListeners()

        // Xin quyền Bluetooth (nếu cần) TRƯỚC KHI đọc danh sách thiết bị đã ghép nối,
        // tránh crash SecurityException trên Android 12+ khi mở app lần đầu.
        ensureBluetoothPermissions { refreshPairedDevices() }
    }

    private fun setupListeners() {
        binding.btnScanBluetooth.setOnClickListener {
            ensureBluetoothPermissions { refreshPairedDevices() }
        }

        binding.btnConnectBluetooth.setOnClickListener {
            ensureBluetoothPermissions { connectSelectedBluetoothDevice() }
        }

        binding.btnDisconnect.setOnClickListener {
            disconnectCurrent()
        }

        binding.btnPrint.setOnClickListener {
            printCurrentText()
        }

        binding.btnFeedCut.setOnClickListener {
            feedAndCut()
        }
    }

    // ----------------- QUYỀN BLUETOOTH -----------------

    private fun ensureBluetoothPermissions(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.BLUETOOTH_SCAN)

            if (needed.isNotEmpty()) {
                pendingPermissionAction = onGranted
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_BT_PERMISSIONS)
                return
            }
        }
        onGranted()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BT_PERMISSIONS) {
            val allGranted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            val action = pendingPermissionAction
            pendingPermissionAction = null
            if (allGranted) {
                action?.invoke()
            } else {
                showToast("Cần cấp quyền Bluetooth để sử dụng ứng dụng")
            }
        }
    }

    // ----------------- DANH SÁCH & KẾT NỐI -----------------

    private fun refreshPairedDevices() {
        if (!bluetoothHelper.isBluetoothSupported) {
            showToast("Thiết bị không hỗ trợ Bluetooth")
            return
        }
        if (!bluetoothHelper.isBluetoothEnabled) {
            showToast("Vui lòng bật Bluetooth trước")
            return
        }

        pairedDevices = bluetoothHelper.getPairedDevices()
        selectedBluetoothPosition = -1

        val names = pairedDevices.map { "${it.name ?: "Không tên"} (${it.address})" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        binding.actBluetoothDevices.setAdapter(adapter)
        binding.actBluetoothDevices.setText("", false)
        binding.actBluetoothDevices.setOnItemClickListener { _, _, position, _ ->
            selectedBluetoothPosition = position
        }

        if (pairedDevices.isEmpty()) {
            showToast("Chưa có thiết bị Bluetooth nào được ghép nối. Vào Cài đặt > Bluetooth để ghép nối máy in trước.")
        } else if (pairedDevices.size == 1) {
            // Chỉ có 1 thiết bị -> tự chọn sẵn cho tiện, người dùng vẫn có thể đổi
            selectedBluetoothPosition = 0
            binding.actBluetoothDevices.setText(names[0], false)
        }
    }

    private fun connectSelectedBluetoothDevice() {
        if (isBusy) return
        val position = selectedBluetoothPosition
        if (position < 0 || position >= pairedDevices.size) {
            showToast("Vui lòng chọn một thiết bị Bluetooth")
            return
        }
        val device = pairedDevices[position]

        setBusy(true)
        setStatus("Đang kết nối tới ${device.name}...", StatusType.NEUTRAL)
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    bluetoothHelper.connect(device)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            setBusy(false)
            if (success) {
                connectedDevice = device
                setStatus("Đã kết nối: ${device.name}", StatusType.SUCCESS)
            } else {
                connectedDevice = null
                setStatus("Kết nối thất bại. Kiểm tra máy in đã bật và trong tầm sóng.")
            }
        }
    }

    private fun disconnectCurrent() {
        bluetoothHelper.disconnect()
        connectedDevice = null
        setStatus("Đã ngắt kết nối", StatusType.NEUTRAL)
    }

    // ----------------- IN -----------------

    private fun printCurrentText() {
        if (isBusy) return

        val text = binding.etPrintText.text.toString()
        if (text.isBlank()) {
            showToast("Vui lòng nhập nội dung cần in")
            return
        }

        val widthMm = binding.etLabelWidthMm.text.toString().toFloatOrNull()
        val heightMm = binding.etLabelHeightMm.text.toString().toFloatOrNull()
        val dpi = binding.etDpi.text.toString().toIntOrNull()

        if (widthMm == null || heightMm == null || widthMm <= 0f || heightMm <= 0f) {
            showToast("Kích thước nhãn không hợp lệ")
            return
        }
        if (dpi == null || dpi <= 0) {
            showToast("Giá trị DPI không hợp lệ")
            return
        }
        if (connectedDevice == null || !bluetoothHelper.isConnected) {
            showToast("Vui lòng kết nối máy in qua Bluetooth trước khi in")
            return
        }

        val cutAfter = binding.swCutAfter.isChecked

        setBusy(true)
        setStatus("Đang chuẩn bị dữ liệu in...", StatusType.NEUTRAL)

        lifecycleScope.launch {
            // Việc dựng bitmap + chuyển raster khá nặng CPU (ảnh nhãn có thể
            // hơn 1 triệu điểm ảnh) nên PHẢI chạy nền, tránh treo giao diện.
            val result = withContext(Dispatchers.Default) {
                try {
                    val data = EscPosCommands.textToLabelBytes(
                        text = text,
                        labelWidthMm = widthMm,
                        labelHeightMm = heightMm,
                        dpi = dpi,
                        cutAfter = cutAfter
                    )
                    Result.success(data)
                } catch (e: IllegalArgumentException) {
                    Result.failure(e)
                }
            }

            result.onSuccess { data ->
                setStatus("Đang gửi dữ liệu tới máy in...", StatusType.NEUTRAL)
                sendToPrinter(data)
            }.onFailure { e ->
                setBusy(false)
                setStatus(e.message ?: "Lỗi khi tạo dữ liệu in")
            }
        }
    }

    private fun feedAndCut() {
        if (isBusy) return
        if (connectedDevice == null || !bluetoothHelper.isConnected) {
            showToast("Vui lòng kết nối máy in trước")
            return
        }
        setBusy(true)
        val data = EscPosCommands.lineFeed(3) + EscPosCommands.cutPaper()
        sendToPrinter(data)
    }

    private fun sendToPrinter(data: ByteArray) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    bluetoothHelper.print(data)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            setBusy(false)
            if (success) {
                setStatus("Đã gửi lệnh in thành công", StatusType.SUCCESS)
                showToast("Đã gửi lệnh in")
            } else {
                // Mất kết nối giữa chừng (ví dụ máy in tắt nguồn) -> chủ động
                // dọn trạng thái để người dùng biết cần kết nối lại, tránh
                // hiểu lầm là vẫn còn kết nối trong khi thực tế đã đứt.
                bluetoothHelper.disconnect()
                connectedDevice = null
                setStatus("Mất kết nối tới máy in. Vui lòng kết nối lại rồi thử in lại.")
            }
        }
    }

    // ----------------- HELPERS -----------------

    private fun setBusy(busy: Boolean) {
        isBusy = busy
        binding.progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        val enabled = !busy
        binding.btnConnectBluetooth.isEnabled = enabled
        binding.btnDisconnect.isEnabled = enabled
        binding.btnPrint.isEnabled = enabled
        binding.btnFeedCut.isEnabled = enabled
    }

    /** 3 sắc thái trạng thái, ánh xạ sang 3 màu badge khác nhau */
    private enum class StatusType { NEUTRAL, SUCCESS, ERROR }

    private fun setStatus(message: String, type: StatusType = StatusType.ERROR) {
        val (bgRes, colorRes) = when (type) {
            StatusType.SUCCESS -> R.drawable.bg_badge_success to R.color.accent
            StatusType.ERROR -> R.drawable.bg_badge_error to R.color.danger
            StatusType.NEUTRAL -> R.drawable.bg_badge_neutral to R.color.info
        }
        binding.tvStatus.text = "● $message"
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.tvStatus.background = ContextCompat.getDrawable(this, bgRes)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothHelper.disconnect()
    }
}
