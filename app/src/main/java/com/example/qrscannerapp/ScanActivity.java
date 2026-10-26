package com.example.qrscannerapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.TorchState;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.mlkit.vision.MlKitAnalyzer;
import androidx.camera.view.CameraController;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions;
import com.google.mlkit.vision.barcode.common.Barcode;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Сканер на ML Kit: ZXing плохо читает мелкие DataMatrix (наклейки на счётчиках)
public class ScanActivity extends AppCompatActivity {
    public static final String EXTRA_RESULT = "SCAN_RESULT";

    private PreviewView previewView;
    private LifecycleCameraController controller;
    private BarcodeScanner scanner;
    private ExecutorService analysisExecutor;
    private ToneGenerator tone;
    private boolean found;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        previewView = findViewById(R.id.previewView);
        analysisExecutor = Executors.newSingleThreadExecutor();

        Button btnTorch = findViewById(R.id.btnTorch);
        btnTorch.setOnClickListener(v -> {
            if (controller == null) return;
            Integer torch = controller.getTorchState().getValue();
            controller.enableTorch(torch == null || torch != TorchState.ON);
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        // Контроллер сам даёт фокус по касанию и зум двумя пальцами
        controller = new LifecycleCameraController(this);
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS);
        controller.setImageAnalysisResolutionSelector(new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(new Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build());
        controller.setTapToFocusEnabled(true);
        controller.bindToLifecycle(this);
        previewView.setController(controller);
        showFocusIndicator();

        // Максимальный зум известен только после открытия камеры
        controller.getZoomState().observe(this, state -> {
            if (scanner == null && state != null) {
                startAnalysis(state.getMaxZoomRatio());
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showFocusIndicator() {
        View focusRing = findViewById(R.id.focusRing);
        // Слушатель возвращает false, поэтому PreviewView сам обрабатывает касание и фокусирует камеру
        previewView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                focusRing.setX(event.getX() - focusRing.getWidth() / 2f);
                focusRing.setY(event.getY() - focusRing.getHeight() / 2f);
            }
            return false;
        });
        controller.getTapToFocusState().observe(this, state -> {
            focusRing.animate().cancel();
            if (state == CameraController.TAP_TO_FOCUS_STARTED) {
                focusRing.getBackground().setTint(Color.WHITE);
                focusRing.setAlpha(1f);
                focusRing.setScaleX(1.3f);
                focusRing.setScaleY(1.3f);
                focusRing.setVisibility(View.VISIBLE);
                focusRing.animate().scaleX(1f).scaleY(1f).setDuration(200);
            } else if (state == CameraController.TAP_TO_FOCUS_FOCUSED
                    || state == CameraController.TAP_TO_FOCUS_NOT_FOCUSED
                    || state == CameraController.TAP_TO_FOCUS_FAILED) {
                focusRing.getBackground().setTint(
                        state == CameraController.TAP_TO_FOCUS_FOCUSED ? Color.GREEN : Color.RED);
                focusRing.animate().alpha(0f).setStartDelay(600).setDuration(300)
                        .withEndAction(() -> focusRing.setVisibility(View.INVISIBLE));
            }
        });
    }

    private void startAnalysis(float maxZoomRatio) {
        BarcodeScannerOptions.Builder options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
                        Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_ITF);
        if (maxZoomRatio > 1f) {
            // ML Kit сам приближает камеру, если код слишком мелкий в кадре
            options.setZoomSuggestionOptions(new ZoomSuggestionOptions.Builder(zoomRatio -> {
                previewView.post(() -> controller.setZoomRatio(zoomRatio));
                return true;
            }).setMaxSupportedZoomRatio(maxZoomRatio).build());
        }
        scanner = BarcodeScanning.getClient(options.build());

        controller.setImageAnalysisAnalyzer(analysisExecutor, new MlKitAnalyzer(
                Collections.singletonList(scanner),
                ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
                ContextCompat.getMainExecutor(this),
                result -> {
                    List<Barcode> barcodes = result.getValue(scanner);
                    if (found || barcodes == null) return;
                    for (Barcode barcode : barcodes) {
                        String value = barcode.getRawValue();
                        if (value != null && !value.isEmpty()) {
                            onFound(value);
                            return;
                        }
                    }
                }));
    }

    private void onFound(String value) {
        found = true;
        controller.clearImageAnalysisAnalyzer();
        try {
            tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
        } catch (RuntimeException ignored) {
            // Звук не обязателен
        }
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_RESULT, value));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null) scanner.close();
        analysisExecutor.shutdown();
        if (tone != null) previewView.postDelayed(tone::release, 300);
    }
}
