@file:Suppress("DEPRECATION")
package com.flowpay.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AnnotationOptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@Composable
fun QRScannerScreen(
    onQRScanned: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showManualInput by remember { mutableStateOf(false) }
    var manualUpi by remember { mutableStateOf("") }
    var flashEnabled by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            // CameraX Preview
            CameraPreviewView(
                flashEnabled = flashEnabled,
                onBarcodeDetected = { barcode ->
                    if (!hasScanned) {
                        hasScanned = true
                        onQRScanned(barcode)
                    }
                }
            )
        }

        // Viewfinder overlay
        QRViewfinderOverlay()

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.3f)
                )
            ) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }

            Text(
                "Scan QR Code",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            IconButton(
                onClick = { flashEnabled = !flashEnabled },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.3f)
                )
            ) {
                Icon(
                    if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    "Flash",
                    tint = if (flashEnabled) Color(0xFFFFD700) else Color.White
                )
            }
        }

        // Bottom section
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Point camera at a UPI QR code",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Gallery button
                OutlinedButton(
                    onClick = { /* Gallery picker placeholder */ },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.5f))
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }

                // Manual entry button
                OutlinedButton(
                    onClick = { showManualInput = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.5f))
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Keyboard, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("UPI ID")
                }
            }

            Spacer(Modifier.height(8.dp))

            if (!hasCameraPermission) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Camera permission required for QR scanning",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                ) {
                    Text("Grant Permission")
                }
            }

            // Demo scan button (for emulator / no camera)
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    if (!hasScanned) {
                        hasScanned = true
                        onQRScanned("upi://pay?pa=demo.merchant@upi&pn=Demo%20Shop&am=100")
                    }
                }
            ) {
                Text("Demo: Simulate QR Scan", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }

        // Manual UPI input dialog
        if (showManualInput) {
            AlertDialog(
                onDismissRequest = { showManualInput = false },
                title = { Text("Enter UPI ID") },
                text = {
                    OutlinedTextField(
                        value = manualUpi,
                        onValueChange = { manualUpi = it },
                        placeholder = { Text("name@bank") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (manualUpi.contains("@")) {
                                showManualInput = false
                                onQRScanned("upi://pay?pa=$manualUpi")
                            }
                        },
                        enabled = manualUpi.contains("@")
                    ) { Text("Pay") }
                },
                dismissButton = {
                    TextButton(onClick = { showManualInput = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun QRViewfinderOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scannerSize = size.minDimension * 0.65f
        val left = (size.width - scannerSize) / 2
        val top = (size.height - scannerSize) / 2.5f
        val cornerRadius = 24.dp.toPx()
        val cornerLength = 40.dp.toPx()
        val strokeWidth = 4.dp.toPx()

        // Semi-transparent background with cutout
        val path = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addRoundRect(
                RoundRect(
                    left = left, top = top,
                    right = left + scannerSize, bottom = top + scannerSize,
                    cornerRadius = CornerRadius(cornerRadius)
                )
            )
        }
        drawPath(path, Color.Black.copy(alpha = 0.6f), blendMode = BlendMode.SrcOver)

        // Draw corner brackets
        val bracketColor = Color.White

        // Top-left
        drawLine(bracketColor, Offset(left, top + cornerLength), Offset(left, top + cornerRadius), strokeWidth)
        drawLine(bracketColor, Offset(left + cornerRadius, top), Offset(left + cornerLength, top), strokeWidth)

        // Top-right
        drawLine(bracketColor, Offset(left + scannerSize, top + cornerLength), Offset(left + scannerSize, top + cornerRadius), strokeWidth)
        drawLine(bracketColor, Offset(left + scannerSize - cornerRadius, top), Offset(left + scannerSize - cornerLength, top), strokeWidth)

        // Bottom-left
        drawLine(bracketColor, Offset(left, top + scannerSize - cornerLength), Offset(left, top + scannerSize - cornerRadius), strokeWidth)
        drawLine(bracketColor, Offset(left + cornerRadius, top + scannerSize), Offset(left + cornerLength, top + scannerSize), strokeWidth)

        // Bottom-right
        drawLine(bracketColor, Offset(left + scannerSize, top + scannerSize - cornerLength), Offset(left + scannerSize, top + scannerSize - cornerRadius), strokeWidth)
        drawLine(bracketColor, Offset(left + scannerSize - cornerRadius, top + scannerSize), Offset(left + scannerSize - cornerLength, top + scannerSize), strokeWidth)
    }
}

@Composable
@AnnotationOptIn(ExperimentalGetImage::class)
private fun CameraPreviewView(
    flashEnabled: Boolean,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(flashEnabled) {
        camera?.cameraControl?.enableTorch(flashEnabled)
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val barcodeScanner = BarcodeScanning.getClient()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                barcodeScanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            val rawValue = barcode.rawValue
                                            if (rawValue != null && (
                                                rawValue.startsWith("upi://") ||
                                                rawValue.contains("pa=") ||
                                                rawValue.contains("@")
                                            )) {
                                                val upiUri = if (rawValue.startsWith("upi://")) {
                                                    rawValue
                                                } else if (rawValue.contains("@")) {
                                                    "upi://pay?pa=$rawValue"
                                                } else {
                                                    rawValue
                                                }
                                                onBarcodeDetected(upiUri)
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (_: Exception) { }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Screen for entering a UPI ID manually (standalone).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayNewScreen(
    onProceed: (String) -> Unit,
    onCancel: () -> Unit
) {
    var upiId by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Pay to UPI ID") },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text("Enter UPI ID", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = upiId,
                onValueChange = { upiId = it.lowercase().trim() },
                placeholder = { Text("name@bank") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Example: shopname@paytm, 9876543210@upi",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onProceed(upiId) },
                enabled = upiId.contains("@") && upiId.length > 3,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) { Text("Proceed", fontSize = 16.sp) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
