package com.example.qrscannerapp;


import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;


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


    private ActivityResultLauncher<ScanOptions> scanLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        etApartment = findViewById(R.id.etApartment);
        etQrId = findViewById(R.id.etQrId);
        btnScan = findViewById(R.id.btnScan);

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


//        scanLauncher = registerForActivityResult(new ScanContract(), result -> {
//            if (result.getContents() != null) {
//
//                String qrText = result.getContents();
//                String onlyId = qrText.replaceAll("\\D+", "");
//
//                if (onlyId.length() >= 8) {
//                    onlyId = onlyId.substring(0, 8);
//                }
//
//                etQrId.setText(onlyId);
//            }
//        });


        btnScan.setOnClickListener(v -> startScan());


        requestCameraPermission();
    }


    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Наведите камеру на QR код");
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
}