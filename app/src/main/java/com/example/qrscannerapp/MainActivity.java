package com.example.qrscannerapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.ByteArrayOutputStream;
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

        View mainScroll = findViewById(R.id.mainScroll);
        final int initialPaddingLeft = mainScroll.getPaddingLeft();
        final int initialPaddingTop = mainScroll.getPaddingTop();
        final int initialPaddingRight = mainScroll.getPaddingRight();
        final int initialPaddingBottom = mainScroll.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(mainScroll, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    initialPaddingLeft + systemBars.left,
                    initialPaddingTop + systemBars.top,
                    initialPaddingRight + systemBars.right,
                    initialPaddingBottom + systemBars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(mainScroll);

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
        Button btnBack = findViewById(R.id.btnBackToList);
        btnBack.setOnClickListener(v -> {
            finish();
        });

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

        Button btnClear = findViewById(R.id.btnClear);
        btnClear.setOnClickListener(v -> {
            etQrId.setText("");
            etComment.setText("");
            rgDNType.clearCheck();
            rgWaterType.clearCheck();
            rgSize.clearCheck();
            rgKitchen.clearCheck();
            rgBathroom.clearCheck();
            beforeBitmap = null;
            afterBitmap = null;
            imgBefore.setImageDrawable(null);
            imgAfter.setImageDrawable(null);
        });

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

                int dnType = 15;
                if (rgDNType.getCheckedRadioButtonId() == R.id.rgDN20) {
                    dnType = 20;
                }

                int waterType = 1; // külm
                if (rgWaterType.getCheckedRadioButtonId() == R.id.rbHot) {
                    waterType = 2; // soe
                }

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
                                "&dn_type=" + dnType +
                                "&water_type=" + waterType +
                                "&size=" + URLEncoder.encode(size, "UTF-8") +
                                "&kitchen=" + URLEncoder.encode(kitchen, "UTF-8") +
                                "&bathroom=" + URLEncoder.encode(bathroom, "UTF-8") +
                                "&photo_before=" + URLEncoder.encode(beforeImage, "UTF-8") +
                                "&photo_after=" + URLEncoder.encode(afterImage, "UTF-8");

                Log.d("POST_DATA", postData);

                URL url = new URL("https://arvestused.agr-torud.ee/insert_dev_data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d("POST", "Response code: " + responseCode);

                java.io.InputStream is;

                if (responseCode >= 200 && responseCode < 400) {
                    is = conn.getInputStream();
                } else {
                    is = conn.getErrorStream();
                }

                java.io.BufferedReader reader =
                        new java.io.BufferedReader(new java.io.InputStreamReader(is));

                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();
                conn.disconnect();

                Log.d("SERVER_RESPONSE", response.toString());

                runOnUiThread(() -> {
                    if (responseCode == 200) {
                        Toast.makeText(this, "Andmed saadetud!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Server error: " + responseCode, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e("POST_ERROR", "Send error", e);

                runOnUiThread(() ->
                        Toast.makeText(this, "Viga saatmisel", Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private String getSelectedText(RadioGroup group) {
        int id = group.getCheckedRadioButtonId();
        if (id == -1) return "";
        RadioButton rb = findViewById(id);
        return rb.getText().toString();
    }
}