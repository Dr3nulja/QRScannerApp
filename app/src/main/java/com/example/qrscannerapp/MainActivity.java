package com.example.qrscannerapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {

    private EditText etApartment, etQrId, etComment;
    private TextView tvHouse;

    private RadioGroup rgDNType, rgWaterType, rgSize, rgKitchen, rgBathroom;

    private ImageView imgBefore, imgAfter;
    private Bitmap beforeBitmap, afterBitmap;
    private boolean isBeforePhoto = true;

    private int houseId;

    private ActivityResultLauncher<ScanOptions> scanLauncher;

    private ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Bitmap image = (Bitmap) result.getData().getExtras().get("data");
                            Bitmap scaled = Bitmap.createScaledBitmap(image, 600, 800, true);

                            if (isBeforePhoto) {
                                beforeBitmap = scaled;
                                imgBefore.setImageBitmap(scaled);
                            } else {
                                afterBitmap = scaled;
                                imgAfter.setImageBitmap(scaled);
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
        etComment = findViewById(R.id.etComment);
        tvHouse = findViewById(R.id.tvHouse);

        rgDNType = findViewById(R.id.rgDNType);
        rgWaterType = findViewById(R.id.rgWaterType);
        rgSize = findViewById(R.id.rgSize);
        rgKitchen = findViewById(R.id.rgKitchen);
        rgBathroom = findViewById(R.id.rgBathroom);

        imgBefore = findViewById(R.id.imgBefore);
        imgAfter = findViewById(R.id.imgAfter);

        Button btnPhotoBefore = findViewById(R.id.btnPhotoBefore);
        Button btnPhotoAfter = findViewById(R.id.btnPhotoAfter);
        Button btnScan = findViewById(R.id.btnScan);
        Button btnSave = findViewById(R.id.btnSave);

        btnPhotoBefore.setOnClickListener(v -> {
            isBeforePhoto = true;
            openCamera();
        });

        btnPhotoAfter.setOnClickListener(v -> {
            isBeforePhoto = false;
            openCamera();
        });

        scanLauncher = registerForActivityResult(new ScanContract(), result -> {
            if(result.getContents() != null){
                String qr = result.getContents();
                String processed = "";
                if (qr.contains("-") && qr.contains("/")) {
                    int dashIndex = qr.indexOf("-");
                    int slashIndex = qr.indexOf("/");
                    if (dashIndex < slashIndex) {
                        processed = qr.substring(dashIndex + 1, slashIndex);
                    }
                }
                etQrId.setText(processed);
                Toast.makeText(this, "QR: " + processed, Toast.LENGTH_SHORT).show();
            }
        });

        btnScan.setOnClickListener(v -> startScan());
        btnSave.setOnClickListener(v -> sendPost());

        houseId = getIntent().getIntExtra("HOUSE_ID", -1);
        String houseAddress = getIntent().getStringExtra("HOUSE_ADDRESS");

        if (houseAddress != null) {
            tvHouse.setText(houseAddress);
        }

        requestCameraPermission();
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Skanni QR");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        scanLauncher.launch(options);
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }
    }

    private String getSelectedText(RadioGroup group) {
        int id = group.getCheckedRadioButtonId();
        if (id == -1) return "";
        RadioButton rb = findViewById(id);
        return rb.getText().toString();
    }

    private String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return "";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    private void sendPost() {

        new Thread(() -> {
            try {
                String apartment = etApartment.getText().toString();
                String qrId = etQrId.getText().toString();
                String comment = etComment.getText().toString();

                String dn = getSelectedText(rgDNType);
                String water = getSelectedText(rgWaterType);
                String size = getSelectedText(rgSize);
                String kitchen = getSelectedText(rgKitchen);
                String bathroom = getSelectedText(rgBathroom);

                String beforeImage = bitmapToBase64(beforeBitmap);
                String afterImage = bitmapToBase64(afterBitmap);

                String postData =
                        "house_id=" + URLEncoder.encode(String.valueOf(houseId), "UTF-8") +
                                "&apartment=" + URLEncoder.encode(apartment, "UTF-8") +
                                "&qr_id=" + URLEncoder.encode(qrId, "UTF-8") +
                                "&comment=" + URLEncoder.encode(comment, "UTF-8") +
                                "&dn_type=" + URLEncoder.encode(dn, "UTF-8") +
                                "&water_type=" + URLEncoder.encode(water, "UTF-8") +
                                "&size=" + URLEncoder.encode(size, "UTF-8") +
                                "&kitchen=" + URLEncoder.encode(kitchen, "UTF-8") +
                                "&bathroom=" + URLEncoder.encode(bathroom, "UTF-8") +
                                "&photo_before=" + URLEncoder.encode(beforeImage, "UTF-8") +
                                "&photo_after=" + URLEncoder.encode(afterImage, "UTF-8");

                URL url = new URL("https://arvestused.agr-torud.ee/insert_dev_data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();

                runOnUiThread(() ->
                        Toast.makeText(this, "Andmed saadetud!", Toast.LENGTH_LONG).show()
                );

            } catch (Exception e) {
                Log.e("POST", "Ошибка при POST", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Viga saatmisel", Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }
}
