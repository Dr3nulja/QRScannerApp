package com.example.qrscannerapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private EditText etApartment, etQrId;
    private Button btnScan;
    private TextView tvHouse;

    private Button btnPhotoBefore, btnPhotoAfter;
    private ImageView imgBefore, imgAfter;
    private boolean isBeforePhoto = true;

    private ActivityResultLauncher<ScanOptions> scanLauncher;

    private ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Bundle extras = result.getData().getExtras();
                            Bitmap image = (Bitmap) extras.get("data");

                            Bitmap scaledImage = Bitmap.createScaledBitmap(image, 600, 800, true);

                            if (isBeforePhoto) {
                                imgBefore.setImageBitmap(scaledImage);
                            } else {
                                imgAfter.setImageBitmap(scaledImage);
                            }
                        }
                    }
            );


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etApartment = findViewById(R.id.etApartment);
        etQrId = findViewById(R.id.etQrId);
        btnScan = findViewById(R.id.btnScan);
        tvHouse = findViewById(R.id.tvHouse);

        btnPhotoBefore = findViewById(R.id.btnPhotoBefore);
        btnPhotoAfter = findViewById(R.id.btnPhotoAfter);
        imgBefore = findViewById(R.id.imgBefore);
        imgAfter = findViewById(R.id.imgAfter);

        btnPhotoBefore.setOnClickListener(v -> {
            isBeforePhoto = true;
            openCamera();
        });

        btnPhotoAfter.setOnClickListener(v -> {
            isBeforePhoto = false;
            openCamera();
        });

        scanLauncher = registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) {
                String qrText = result.getContents();
                Pattern pattern = Pattern.compile("(\\d{8})");
                Matcher matcher = pattern.matcher(qrText);

                if (matcher.find()) {
                    String onlyId = matcher.group(1);
                    etQrId.setText(onlyId);
                } else {
                    etQrId.setText("Invalid QR code format");
                }
            }
        });

        btnScan.setOnClickListener(v -> startScan());

        requestCameraPermission();

        Button btnBackToList = findViewById(R.id.btnBackToList);
        btnBackToList.setOnClickListener(v -> finish());

        String house = getIntent().getStringExtra("HOUSE_ADDRESS");
        if (house != null) {
            tvHouse.setText(house);
        }
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Suuna kaamera QR-koodi poole");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);
        scanLauncher.launch(options);
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);
    }
}
