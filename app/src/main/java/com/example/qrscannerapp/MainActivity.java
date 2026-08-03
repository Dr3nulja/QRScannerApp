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

    private static final String OTHER_OPTION = "Muu";

    private EditText etApartment, etQrId, etLastReading;
    private EditText etKitchenComment, etBathroomComment;
    private EditText etAllocatorLocationComment, etAllocatorComment;
    private EditText etRadiatorTypeOther, etRadiatorWidthOther, etRadiatorDepthOther, etRadiatorLengthOther;
    private EditText etPhotoBeforeComment, etPhotoAfterComment;
    private TextView tvHouse;

    private RadioGroup rgDeviceType, rgAction;
    private RadioGroup rgDNType, rgWaterType, rgSize;
    private CheckBox cbKitchenFloor, cbKitchenCeiling, cbBathroomFloor, cbBathroomCeiling;
    private RadioGroup rgAllocatorLocation;
    private Spinner spRadiatorType, spRadiatorWidth, spRadiatorDepth, spRadiatorLength;

    private View waterMeterSection, allocatorSection, beforePhotoGroup, lastReadingGroup;

    private ImageView imgBefore, imgAfter;
    private Bitmap beforeBitmap, afterBitmap;
    private boolean isBeforePhoto = true;

    private SignatureView signatureView;

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
        etLastReading = findViewById(R.id.etLastReading);
        etKitchenComment = findViewById(R.id.etKitchenComment);
        etBathroomComment = findViewById(R.id.etBathroomComment);
        etAllocatorLocationComment = findViewById(R.id.etAllocatorLocationComment);
        etAllocatorComment = findViewById(R.id.etAllocatorComment);
        etRadiatorTypeOther = findViewById(R.id.etRadiatorTypeOther);
        etRadiatorWidthOther = findViewById(R.id.etRadiatorWidthOther);
        etRadiatorDepthOther = findViewById(R.id.etRadiatorDepthOther);
        etRadiatorLengthOther = findViewById(R.id.etRadiatorLengthOther);
        etPhotoBeforeComment = findViewById(R.id.etPhotoBeforeComment);
        etPhotoAfterComment = findViewById(R.id.etPhotoAfterComment);
        tvHouse = findViewById(R.id.tvHouse);

        rgDeviceType = findViewById(R.id.rgDeviceType);
        rgAction = findViewById(R.id.rgAction);
        rgDNType = findViewById(R.id.rgDNType);
        rgWaterType = findViewById(R.id.rgWaterType);
        rgSize = findViewById(R.id.rgSize);
        rgAllocatorLocation = findViewById(R.id.rgAllocatorLocation);

        cbKitchenFloor = findViewById(R.id.cbKitchenFloor);
        cbKitchenCeiling = findViewById(R.id.cbKitchenCeiling);
        cbBathroomFloor = findViewById(R.id.cbBathroomFloor);
        cbBathroomCeiling = findViewById(R.id.cbBathroomCeiling);

        spRadiatorType = findViewById(R.id.spRadiatorType);
        spRadiatorWidth = findViewById(R.id.spRadiatorWidth);
        spRadiatorDepth = findViewById(R.id.spRadiatorDepth);
        spRadiatorLength = findViewById(R.id.spRadiatorLength);

        waterMeterSection = findViewById(R.id.waterMeterSection);
        allocatorSection = findViewById(R.id.allocatorSection);
        beforePhotoGroup = findViewById(R.id.beforePhotoGroup);
        lastReadingGroup = findViewById(R.id.lastReadingGroup);

        imgBefore = findViewById(R.id.imgBefore);
        imgAfter = findViewById(R.id.imgAfter);

        signatureView = findViewById(R.id.signatureView);

        setupOtherToggle(spRadiatorType, etRadiatorTypeOther);
        setupOtherToggle(spRadiatorWidth, etRadiatorWidthOther);
        setupOtherToggle(spRadiatorDepth, etRadiatorDepthOther);
        setupOtherToggle(spRadiatorLength, etRadiatorLengthOther);

        rgDeviceType.setOnCheckedChangeListener((group, checkedId) -> updateFormVisibility());
        rgAction.setOnCheckedChangeListener((group, checkedId) -> updateFormVisibility());
        updateFormVisibility();

        Button btnPhotoBefore = findViewById(R.id.btnPhotoBefore);
        Button btnPhotoAfter = findViewById(R.id.btnPhotoAfter);
        Button btnScan = findViewById(R.id.btnScan);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnBack = findViewById(R.id.btnBackToList);
        Button btnClearSignature = findViewById(R.id.btnClearSignature);

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

        btnClearSignature.setOnClickListener(v -> signatureView.clear());

        scanLauncher = registerForActivityResult(new ScanContract(), result -> {
            if(result.getContents() != null){
                String qr = result.getContents();
                String processed = qr;
                if (qr.contains("-") && qr.contains("/")) {
                    int dashIndex = qr.indexOf("-");
                    int slashIndex = qr.indexOf("/");
                    if (dashIndex < slashIndex) {
                        processed = qr.substring(dashIndex + 1, slashIndex);
                    }
                } else if (qr.length() > 6 && qr.substring(6).startsWith("1083")) {
                    processed = qr.substring(6);
                }
                etQrId.setText(processed);
                Toast.makeText(this, "QR: " + processed, Toast.LENGTH_SHORT).show();
            }
        });

        btnScan.setOnClickListener(v -> startScan());
        btnSave.setOnClickListener(v -> sendPost());

        Button btnClear = findViewById(R.id.btnClear);
        btnClear.setOnClickListener(v -> clearForm());

        houseId = getIntent().getIntExtra("HOUSE_ID", -1);
        String houseAddress = getIntent().getStringExtra("HOUSE_ADDRESS");

        if (houseAddress != null) {
            tvHouse.setText(houseAddress);
        }

        requestCameraPermission();
    }

    private void setupOtherToggle(Spinner spinner, EditText otherField) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object selected = parent.getItemAtPosition(position);
                boolean isOther = selected != null && OTHER_OPTION.equals(selected.toString());
                otherField.setVisibility(isOther ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void updateFormVisibility() {
        boolean isAllocator = rgDeviceType.getCheckedRadioButtonId() == R.id.rbAllocator;
        boolean isWaterMeter = rgDeviceType.getCheckedRadioButtonId() == R.id.rbWaterMeter;
        boolean isReplace = rgAction.getCheckedRadioButtonId() == R.id.rbReplace;

        waterMeterSection.setVisibility(isWaterMeter ? View.VISIBLE : View.GONE);
        allocatorSection.setVisibility(isAllocator ? View.VISIBLE : View.GONE);
        lastReadingGroup.setVisibility(isWaterMeter && isReplace ? View.VISIBLE : View.GONE);
        beforePhotoGroup.setVisibility(isReplace ? View.VISIBLE : View.GONE);
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Skanni QR");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE, ScanOptions.DATA_MATRIX);
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

    private void clearForm() {
        etApartment.setText("");
        etQrId.setText("");
        etLastReading.setText("");
        etKitchenComment.setText("");
        etBathroomComment.setText("");
        etAllocatorLocationComment.setText("");
        etAllocatorComment.setText("");
        etRadiatorTypeOther.setText("");
        etRadiatorWidthOther.setText("");
        etRadiatorDepthOther.setText("");
        etRadiatorLengthOther.setText("");
        etPhotoBeforeComment.setText("");
        etPhotoAfterComment.setText("");

        rgDeviceType.clearCheck();
        rgAction.clearCheck();
        rgDNType.clearCheck();
        rgWaterType.clearCheck();
        rgSize.clearCheck();
        rgAllocatorLocation.clearCheck();

        cbKitchenFloor.setChecked(false);
        cbKitchenCeiling.setChecked(false);
        cbBathroomFloor.setChecked(false);
        cbBathroomCeiling.setChecked(false);

        spRadiatorType.setSelection(0);
        spRadiatorWidth.setSelection(0);
        spRadiatorDepth.setSelection(0);
        spRadiatorLength.setSelection(0);
        etRadiatorTypeOther.setVisibility(View.GONE);
        etRadiatorWidthOther.setVisibility(View.GONE);
        etRadiatorDepthOther.setVisibility(View.GONE);
        etRadiatorLengthOther.setVisibility(View.GONE);

        beforeBitmap = null;
        afterBitmap = null;
        imgBefore.setImageDrawable(null);
        imgAfter.setImageDrawable(null);

        signatureView.clear();

        updateFormVisibility();
    }

    private void sendPost() {

        new Thread(() -> {
            try {

                String apartment = etApartment.getText().toString();
                String qrId = etQrId.getText().toString();

                boolean isAllocator = rgDeviceType.getCheckedRadioButtonId() == R.id.rbAllocator;
                String deviceType = isAllocator ? "allocator" : "water_meter";

                boolean isReplace = rgAction.getCheckedRadioButtonId() == R.id.rbReplace;
                String actionType = isReplace ? "replace" : "install";

                String lastReading = isReplace ? etLastReading.getText().toString() : "";

                int dnType = 15;
                if (rgDNType.getCheckedRadioButtonId() == R.id.rgDN20) {
                    dnType = 20;
                }

                int waterType = 1; // külm
                if (rgWaterType.getCheckedRadioButtonId() == R.id.rbHot) {
                    waterType = 2; // soe
                }

                String size = getSelectedText(rgSize);

                String kitchenFloor = cbKitchenFloor.isChecked() ? "1" : "0";
                String kitchenCeiling = cbKitchenCeiling.isChecked() ? "1" : "0";
                String kitchenComment = etKitchenComment.getText().toString();

                String bathroomFloor = cbBathroomFloor.isChecked() ? "1" : "0";
                String bathroomCeiling = cbBathroomCeiling.isChecked() ? "1" : "0";
                String bathroomComment = etBathroomComment.getText().toString();

                String allocatorLocation = getSelectedText(rgAllocatorLocation);
                String allocatorLocationComment = etAllocatorLocationComment.getText().toString();

                String radiatorType = getSpinnerValue(spRadiatorType, etRadiatorTypeOther);
                String radiatorWidth = getSpinnerValue(spRadiatorWidth, etRadiatorWidthOther);
                String radiatorDepth = getSpinnerValue(spRadiatorDepth, etRadiatorDepthOther);
                String radiatorLength = getSpinnerValue(spRadiatorLength, etRadiatorLengthOther);
                String allocatorComment = etAllocatorComment.getText().toString();

                String photoBeforeComment = etPhotoBeforeComment.getText().toString();
                String photoAfterComment = etPhotoAfterComment.getText().toString();

                String beforeImage = bitmapToBase64(beforeBitmap);
                String afterImage = bitmapToBase64(afterBitmap);
                String signatureImage = bitmapToBase64(signatureView.getSignatureBitmap());

                String postData =
                        "house_id=" + URLEncoder.encode(String.valueOf(houseId), "UTF-8") +
                                "&device_type=" + URLEncoder.encode(deviceType, "UTF-8") +
                                "&action_type=" + URLEncoder.encode(actionType, "UTF-8") +
                                "&apartment=" + URLEncoder.encode(apartment, "UTF-8") +
                                "&qr_id=" + URLEncoder.encode(qrId, "UTF-8") +
                                "&last_reading=" + URLEncoder.encode(lastReading, "UTF-8") +
                                "&dn_type=" + dnType +
                                "&water_type=" + waterType +
                                "&size=" + URLEncoder.encode(size, "UTF-8") +
                                "&kitchen_floor=" + kitchenFloor +
                                "&kitchen_ceiling=" + kitchenCeiling +
                                "&kitchen_comment=" + URLEncoder.encode(kitchenComment, "UTF-8") +
                                "&bathroom_floor=" + bathroomFloor +
                                "&bathroom_ceiling=" + bathroomCeiling +
                                "&bathroom_comment=" + URLEncoder.encode(bathroomComment, "UTF-8") +
                                "&allocator_location=" + URLEncoder.encode(allocatorLocation, "UTF-8") +
                                "&allocator_location_comment=" + URLEncoder.encode(allocatorLocationComment, "UTF-8") +
                                "&radiator_type=" + URLEncoder.encode(radiatorType, "UTF-8") +
                                "&radiator_width=" + URLEncoder.encode(radiatorWidth, "UTF-8") +
                                "&radiator_depth=" + URLEncoder.encode(radiatorDepth, "UTF-8") +
                                "&radiator_length=" + URLEncoder.encode(radiatorLength, "UTF-8") +
                                "&allocator_comment=" + URLEncoder.encode(allocatorComment, "UTF-8") +
                                "&photo_before_comment=" + URLEncoder.encode(photoBeforeComment, "UTF-8") +
                                "&photo_after_comment=" + URLEncoder.encode(photoAfterComment, "UTF-8") +
                                "&photo_before=" + URLEncoder.encode(beforeImage, "UTF-8") +
                                "&photo_after=" + URLEncoder.encode(afterImage, "UTF-8") +
                                "&signature=" + URLEncoder.encode(signatureImage, "UTF-8");

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

    private String getSpinnerValue(Spinner spinner, EditText otherField) {
        Object selected = spinner.getSelectedItem();
        String value = selected != null ? selected.toString() : "";
        if (OTHER_OPTION.equals(value)) {
            return otherField.getText().toString();
        }
        return value;
    }
}
