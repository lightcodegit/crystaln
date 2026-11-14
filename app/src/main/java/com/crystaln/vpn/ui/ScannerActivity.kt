package com.crystaln.vpn.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.crystaln.vpn.R
import com.crystaln.vpn.ui.theme.CrystalNTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen camera view that scans a QR code containing a VPN config
 * (the same text the clipboard import accepts) and returns it via
 * [EXTRA_TEXT]. Finish without a result on permission denial.
 */
class ScannerActivity : ComponentActivity() {

    private val sent = AtomicBoolean(false)
    private var previewView: PreviewView? = null
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CrystalNTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            }.also { previewView = it }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        stringResource(R.string.scan_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp),
                    )
                }
            }
        }
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView?.surfaceProvider)
                }
                val scanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build(),
                )
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analyzerExecutor) { proxy ->
                    try {
                        val media = proxy.image
                        if (media != null) {
                            val input = InputImage.fromMediaImage(
                                media,
                                proxy.imageInfo.rotationDegrees,
                            )
                            scanner.process(input)
                                .addOnSuccessListener { barcodes ->
                                    val value = barcodes.firstOrNull()?.rawValue
                                    if (value != null && sent.compareAndSet(false, true)) {
                                        setResult(
                                            RESULT_OK,
                                            Intent().putExtra(EXTRA_TEXT, value),
                                        )
                                        finish()
                                    }
                                }
                                .addOnFailureListener { /* keep scanning */ }
                        }
                    } finally {
                        proxy.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Throwable) {
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        analyzerExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TEXT = "scan_text"
    }
}
