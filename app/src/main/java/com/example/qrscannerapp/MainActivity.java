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
                etQrId.setText(result.getContents());
            }
        });


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