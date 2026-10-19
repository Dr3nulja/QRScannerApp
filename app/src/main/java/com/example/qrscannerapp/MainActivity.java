package com.example.qrscannerapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String OTHER_OPTION = "Muu";

    private EditText etApartment, etQrId, etLastReading;
    private EditText etKitchenComment, etBathroomComment;
    private EditText etAllocatorLocationComment, etAllocatorComment;
    private EditText etRadiatorTypeOther, etRadiatorPanelTypeOther, etRadiatorWidthOther, etRadiatorDepthOther, etRadiatorHeightOther, etRadiatorLengthOther;
    private EditText etPhotoBeforeComment, etPhotoAfterComment;
    private TextView tvHouse;

    private RadioGroup rgDeviceType, rgAction;
    private RadioGroup rgDNType, rgWaterType, rgSize;
    private CheckBox cbKitchenFloor, cbKitchenCeiling, cbBathroomFloor, cbBathroomCeiling;
    private RadioGroup rgAllocatorLocation;
    private Spinner spRadiatorType, spRadiatorPanelType, spRadiatorWidth, spRadiatorDepth, spRadiatorHeight, spRadiatorLength;

    private View waterMeterSection, allocatorSection, beforePhotoGroup, lastReadingGroup;

    private ImageView imgBefore, imgAfter;
    // Полноразмерные фото лежат файлами: переживают перезапуск активити, пока открыта камера
    private File beforeFile, afterFile, pendingPhotoFile;
    private boolean isBeforePhoto = true;

    private SignatureView signatureView;
    private Button btnSave;

    // id отчёта: повторная отправка после обрыва связи не создаёт дубль на сервере
    private String clientUuid;
    private boolean sending = false;

    // Ключ поля (например "apartment") -> блок "галочка + комментарий"
    private final Map<String, FieldComment> fieldComments = new LinkedHashMap<>();

    private static class FieldComment {
        final CheckBox toggle;
        final EditText input;

        FieldComment(CheckBox toggle, EditText input) {
            this.toggle = toggle;
            this.input = input;
        }

        String getValue() {
            return toggle.isChecked() ? input.getText().toString() : "";
        }
    }

    private int houseId;
    private String presetDeviceType, presetActionType;

    private ActivityResultLauncher<ScanOptions> scanLauncher;

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), this::onPhotoTaken);

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    openCamera();
                } else {
                    Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            beforeFile = fileOrNull(savedInstanceState.getString("before_file"));
            afterFile = fileOrNull(savedInstanceState.getString("after_file"));
            pendingPhotoFile = fileOrNull(savedInstanceState.getString("pending_file"));
            isBeforePhoto = savedInstanceState.getBoolean("is_before", true);
            clientUuid = savedInstanceState.getString("client_uuid");
        }
        if (clientUuid == null) {
            clientUuid = UUID.randomUUID().toString();
        }

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
        etRadiatorPanelTypeOther = findViewById(R.id.etRadiatorPanelTypeOther);
        etRadiatorWidthOther = findViewById(R.id.etRadiatorWidthOther);
        etRadiatorDepthOther = findViewById(R.id.etRadiatorDepthOther);
        etRadiatorHeightOther = findViewById(R.id.etRadiatorHeightOther);
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
        spRadiatorPanelType = findViewById(R.id.spRadiatorPanelType);
        spRadiatorWidth = findViewById(R.id.spRadiatorWidth);
        spRadiatorDepth = findViewById(R.id.spRadiatorDepth);
        spRadiatorHeight = findViewById(R.id.spRadiatorHeight);
        spRadiatorLength = findViewById(R.id.spRadiatorLength);

        waterMeterSection = findViewById(R.id.waterMeterSection);
        allocatorSection = findViewById(R.id.allocatorSection);
        beforePhotoGroup = findViewById(R.id.beforePhotoGroup);
        lastReadingGroup = findViewById(R.id.lastReadingGroup);

        imgBefore = findViewById(R.id.imgBefore);
        imgAfter = findViewById(R.id.imgAfter);

        signatureView = findViewById(R.id.signatureView);

        setupOtherToggle(spRadiatorType, etRadiatorTypeOther);
        setupOtherToggle(spRadiatorPanelType, etRadiatorPanelTypeOther);
        setupOtherToggle(spRadiatorWidth, etRadiatorWidthOther);
        setupOtherToggle(spRadiatorDepth, etRadiatorDepthOther);
        setupOtherToggle(spRadiatorHeight, etRadiatorHeightOther);
        setupOtherToggle(spRadiatorLength, etRadiatorLengthOther);

        addFieldComment(rgDeviceType, "device_type");
        addFieldComment(rgAction, "action_type");
        addFieldComment(etApartment, "apartment");
        addFieldComment(findViewById(R.id.btnScan), "qr_id");
        addFieldComment(etLastReading, "last_reading");
        addFieldComment(rgDNType, "dn_type");
        addFieldComment(rgWaterType, "water_type");
        addFieldComment(rgSize, "size");
        addFieldComment(etRadiatorTypeOther, "radiator_type");
        addFieldComment(etRadiatorPanelTypeOther, "radiator_panel_type");
        addFieldComment(etRadiatorWidthOther, "radiator_width");
        addFieldComment(etRadiatorDepthOther, "radiator_depth");
        addFieldComment(etRadiatorHeightOther, "radiator_height");
        addFieldComment(etRadiatorLengthOther, "radiator_length");

        rgDeviceType.setOnCheckedChangeListener((group, checkedId) -> updateFormVisibility());
        rgAction.setOnCheckedChangeListener((group, checkedId) -> updateFormVisibility());
        updateFormVisibility();

        Button btnPhotoBefore = findViewById(R.id.btnPhotoBefore);
        Button btnPhotoAfter = findViewById(R.id.btnPhotoAfter);
        Button btnScan = findViewById(R.id.btnScan);
        btnSave = findViewById(R.id.btnSave);
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
                etQrId.setError(null);
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

        // Задача из дашборда: тип и действие уже известны, выбирать вручную не нужно
        presetDeviceType = getIntent().getStringExtra("DEVICE_TYPE");
        presetActionType = getIntent().getStringExtra("ACTION_TYPE");
        if (presetDeviceType != null && presetActionType != null) {
            findViewById(R.id.cardDeviceType).setVisibility(View.GONE);
            findViewById(R.id.cardAction).setVisibility(View.GONE);
            renumberSections();
            tvHouse.setText(getString(R.string.house_with_task, houseAddress,
                    getString(House.taskLabelRes(presetDeviceType, presetActionType))));
            applyPresetTask();
        }

        String installer = InstallerPrefs.getName(this);
        if (!installer.isEmpty()) {
            tvHouse.append("\n" + getString(R.string.installer_label, installer));
        }

        showPreview(imgBefore, beforeFile);
        showPreview(imgAfter, afterFile);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("before_file", beforeFile != null ? beforeFile.getPath() : null);
        outState.putString("after_file", afterFile != null ? afterFile.getPath() : null);
        outState.putString("pending_file", pendingPhotoFile != null ? pendingPhotoFile.getPath() : null);
        outState.putBoolean("is_before", isBeforePhoto);
        outState.putString("client_uuid", clientUuid);
    }

    private static File fileOrNull(String path) {
        return path != null ? new File(path) : null;
    }

    private void applyPresetTask() {
        if (presetDeviceType == null || presetActionType == null) return;
        rgDeviceType.check(House.DEVICE_ALLOCATOR.equals(presetDeviceType)
                ? R.id.rbAllocator : R.id.rbWaterMeter);
        rgAction.check(House.ACTION_REPLACE.equals(presetActionType)
                ? R.id.rbReplace : R.id.rbInstall);
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

    // Вставляет после anchor галочку "Lisa kommentaar", которая показывает поле комментария
    private void addFieldComment(View anchor, String key) {
        ViewGroup parent = (ViewGroup) anchor.getParent();
        int index = parent.indexOfChild(anchor);

        CheckBox toggle = new CheckBox(this);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        toggleParams.topMargin = dp(8);
        toggle.setLayoutParams(toggleParams);
        toggle.setBackgroundResource(R.drawable.btn_selection_gray_rounded);
        toggle.setButtonTintList(ContextCompat.getColorStateList(this, R.color.elder_primary));
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPaddingRelative(dp(18), 0, 0, 0);
        toggle.setText(R.string.toggle_field_comment);
        toggle.setTextColor(ContextCompat.getColor(this, R.color.elder_text_muted));
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        EditText input = new EditText(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(80));
        inputParams.topMargin = dp(8);
        input.setLayoutParams(inputParams);
        input.setBackgroundResource(android.R.drawable.edit_text);
        input.setGravity(Gravity.TOP);
        input.setHint(R.string.hint_field_comment);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setPadding(dp(16), dp(16), dp(16), dp(16));
        input.setTextColor(ContextCompat.getColor(this, R.color.elder_text));
        input.setHintTextColor(ContextCompat.getColor(this, R.color.elder_text_muted));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setVisibility(View.GONE);

        toggle.setOnCheckedChangeListener((button, checked) -> {
            input.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked) {
                input.requestFocus();
            }
        });

        parent.addView(toggle, index + 1);
        parent.addView(input, index + 2);
        fieldComments.put(key, new FieldComment(toggle, input));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateFormVisibility() {
        boolean isAllocator = rgDeviceType.getCheckedRadioButtonId() == R.id.rbAllocator;
        boolean isWaterMeter = rgDeviceType.getCheckedRadioButtonId() == R.id.rbWaterMeter;
        boolean isReplace = rgAction.getCheckedRadioButtonId() == R.id.rbReplace;

        waterMeterSection.setVisibility(isWaterMeter ? View.VISIBLE : View.GONE);
        allocatorSection.setVisibility(isAllocator ? View.VISIBLE : View.GONE);
        lastReadingGroup.setVisibility(isWaterMeter && isReplace ? View.VISIBLE : View.GONE);
        beforePhotoGroup.setVisibility(isReplace ? View.VISIBLE : View.GONE);
        renumberSections();
    }

    // Нумерует только видимые разделы, чтобы не было пропусков (1, 2, 3...)
    private void renumberSections() {
        int[][] sections = {
                {R.id.tvSectionDeviceType, R.string.section_device_type},
                {R.id.tvSectionAction, R.string.section_action},
                {R.id.tvSectionApartment, R.string.section_apartment},
                {R.id.tvSectionPhotos, R.string.section_photos},
                {R.id.tvSectionOptions, R.string.section_options},
                {R.id.tvSectionSignature, R.string.section_signature},
        };
        int number = 1;
        for (int[] section : sections) {
            TextView title = findViewById(section[0]);
            if (isVisibleInForm(title)) {
                title.setText(getString(R.string.numbered_section, number++, getString(section[1])));
            }
        }
    }

    // View.isShown() не работает до прикрепления к окну, поэтому проверяем родителей сами
    private boolean isVisibleInForm(View view) {
        while (view != null) {
            if (view.getVisibility() != View.VISIBLE) return false;
            if (view.getId() == R.id.mainScroll) return true;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return true;
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Skanni QR");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE, ScanOptions.DATA_MATRIX);
        options.addExtra("TRY_HARDER", true);
        scanLauncher.launch(options);
    }

    private void openCamera() {
        // Приложение объявляет CAMERA, поэтому без разрешения системная камера не откроется
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }

        try {
            File dir = new File(getFilesDir(), "photos");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
            pendingPhotoFile = new File(dir, (isBeforePhoto ? "before_" : "after_") + System.currentTimeMillis() + ".jpg");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pendingPhotoFile);
            takePictureLauncher.launch(uri);
        } catch (Exception e) {
            Log.e("CAMERA", "Cannot open camera", e);
            pendingPhotoFile = null;
            Toast.makeText(this, R.string.camera_error, Toast.LENGTH_LONG).show();
        }
    }

    private void onPhotoTaken(Boolean success) {
        File taken = pendingPhotoFile;
        pendingPhotoFile = null;

        if (!Boolean.TRUE.equals(success) || taken == null || !taken.exists() || taken.length() == 0) {
            deleteQuietly(taken);
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(this, R.string.photo_error, Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (isBeforePhoto) {
            deleteQuietly(beforeFile);
            beforeFile = taken;
            showPreview(imgBefore, beforeFile);
        } else {
            deleteQuietly(afterFile);
            afterFile = taken;
            showPreview(imgAfter, afterFile);
        }
    }

    private void showPreview(ImageView view, File file) {
        Bitmap preview = PhotoUtils.load(file, PhotoUtils.PREVIEW_MAX_SIDE);
        if (preview != null) {
            view.setImageBitmap(preview);
        } else {
            view.setImageDrawable(null);
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w("CAMERA", "Cannot delete " + file);
        }
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
        etRadiatorPanelTypeOther.setText("");
        etRadiatorWidthOther.setText("");
        etRadiatorDepthOther.setText("");
        etRadiatorHeightOther.setText("");
        etRadiatorLengthOther.setText("");
        etPhotoBeforeComment.setText("");
        etPhotoAfterComment.setText("");
        etApartment.setError(null);
        etQrId.setError(null);

        rgDeviceType.clearCheck();
        rgAction.clearCheck();
        applyPresetTask();
        rgDNType.clearCheck();
        rgWaterType.clearCheck();
        rgSize.clearCheck();
        rgAllocatorLocation.clearCheck();

        cbKitchenFloor.setChecked(false);
        cbKitchenCeiling.setChecked(false);
        cbBathroomFloor.setChecked(false);
        cbBathroomCeiling.setChecked(false);

        spRadiatorType.setSelection(0);
        spRadiatorPanelType.setSelection(0);
        spRadiatorWidth.setSelection(0);
        spRadiatorDepth.setSelection(0);
        spRadiatorHeight.setSelection(0);
        spRadiatorLength.setSelection(0);
        etRadiatorTypeOther.setVisibility(View.GONE);
        etRadiatorPanelTypeOther.setVisibility(View.GONE);
        etRadiatorWidthOther.setVisibility(View.GONE);
        etRadiatorDepthOther.setVisibility(View.GONE);
        etRadiatorHeightOther.setVisibility(View.GONE);
        etRadiatorLengthOther.setVisibility(View.GONE);

        for (FieldComment comment : fieldComments.values()) {
            comment.input.setText("");
            comment.toggle.setChecked(false);
        }

        deleteQuietly(beforeFile);
        deleteQuietly(afterFile);
        beforeFile = null;
        afterFile = null;
        imgBefore.setImageDrawable(null);
        imgAfter.setImageDrawable(null);

        signatureView.clear();

        // Новый отчёт — новый id
        clientUuid = UUID.randomUUID().toString();

        updateFormVisibility();
    }

    private void sendPost() {
        if (sending) return;

        // Обязательное: без этого отчёт в дашборде бесполезен
        if (rgDeviceType.getCheckedRadioButtonId() == -1) {
            showError(rgDeviceType, getString(R.string.error_device_type));
            return;
        }
        if (rgAction.getCheckedRadioButtonId() == -1) {
            showError(rgAction, getString(R.string.error_action));
            return;
        }
        if (etApartment.getText().toString().trim().isEmpty()) {
            etApartment.setError(getString(R.string.error_apartment));
            showError(etApartment, getString(R.string.error_apartment));
            return;
        }
        if (etQrId.getText().toString().trim().isEmpty()) {
            etQrId.setError(getString(R.string.error_qr));
            showError(etQrId, getString(R.string.error_qr));
            return;
        }

        // Не обязательное, но обычно забывают случайно — переспрашиваем
        boolean isReplace = rgAction.getCheckedRadioButtonId() == R.id.rbReplace;
        boolean isWaterMeter = rgDeviceType.getCheckedRadioButtonId() == R.id.rbWaterMeter;
        List<String> warnings = new ArrayList<>();
        if (isReplace && beforeFile == null) warnings.add(getString(R.string.warn_photo_before));
        if (afterFile == null) warnings.add(getString(R.string.warn_photo_after));
        if (isReplace && isWaterMeter && etLastReading.getText().toString().trim().isEmpty()) {
            warnings.add(getString(R.string.warn_last_reading));
        }
        if (!signatureView.hasSignature()) warnings.add(getString(R.string.warn_signature));

        if (warnings.isEmpty()) {
            doSend();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.warn_title)
                    .setMessage("• " + TextUtils.join("\n• ", warnings))
                    .setPositiveButton(R.string.btn_send_anyway, (dialog, which) -> doSend())
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        }
    }

    private void showError(View field, String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        field.getParent().requestChildFocus(field, field);
        field.requestFocus();
    }

    // Значения формы читаем в UI-потоке; в фоне — только фото и сеть
    private Map<String, String> collectFormData() {
        boolean isAllocator = rgDeviceType.getCheckedRadioButtonId() == R.id.rbAllocator;
        boolean isReplace = rgAction.getCheckedRadioButtonId() == R.id.rbReplace;

        // Не выбрано — пусто, а не значение по умолчанию (раньше всегда уходило DN15 / külm)
        String dnType = "";
        if (rgDNType.getCheckedRadioButtonId() == R.id.rgDN15) dnType = "15";
        if (rgDNType.getCheckedRadioButtonId() == R.id.rgDN20) dnType = "20";

        String waterType = "";
        if (rgWaterType.getCheckedRadioButtonId() == R.id.rbCold) waterType = "1"; // külm
        if (rgWaterType.getCheckedRadioButtonId() == R.id.rbHot) waterType = "2";  // soe

        Map<String, String> data = new LinkedHashMap<>();
        data.put("house_id", String.valueOf(houseId));
        data.put("client_uuid", clientUuid);
        data.put("installer", InstallerPrefs.getName(this));
        data.put("device_type", isAllocator ? House.DEVICE_ALLOCATOR : House.DEVICE_WATER_METER);
        data.put("action_type", isReplace ? House.ACTION_REPLACE : House.ACTION_INSTALL);
        data.put("apartment", etApartment.getText().toString().trim());
        data.put("qr_id", etQrId.getText().toString().trim());
        data.put("last_reading", isReplace ? etLastReading.getText().toString().trim() : "");
        data.put("dn_type", dnType);
        data.put("water_type", waterType);
        data.put("size", getSelectedText(rgSize));
        data.put("kitchen_floor", cbKitchenFloor.isChecked() ? "1" : "0");
        data.put("kitchen_ceiling", cbKitchenCeiling.isChecked() ? "1" : "0");
        data.put("kitchen_comment", etKitchenComment.getText().toString());
        data.put("bathroom_floor", cbBathroomFloor.isChecked() ? "1" : "0");
        data.put("bathroom_ceiling", cbBathroomCeiling.isChecked() ? "1" : "0");
        data.put("bathroom_comment", etBathroomComment.getText().toString());
        data.put("allocator_location", getSelectedText(rgAllocatorLocation));
        data.put("allocator_location_comment", etAllocatorLocationComment.getText().toString());
        data.put("radiator_type", getSpinnerValue(spRadiatorType, etRadiatorTypeOther));
        data.put("radiator_panel_type", getSpinnerValue(spRadiatorPanelType, etRadiatorPanelTypeOther));
        data.put("radiator_width", getSpinnerValue(spRadiatorWidth, etRadiatorWidthOther));
        data.put("radiator_depth", getSpinnerValue(spRadiatorDepth, etRadiatorDepthOther));
        data.put("radiator_height", getSpinnerValue(spRadiatorHeight, etRadiatorHeightOther));
        data.put("radiator_length", getSpinnerValue(spRadiatorLength, etRadiatorLengthOther));
        data.put("allocator_comment", etAllocatorComment.getText().toString());
        data.put("photo_before_comment", etPhotoBeforeComment.getText().toString());
        data.put("photo_after_comment", etPhotoAfterComment.getText().toString());
        for (Map.Entry<String, FieldComment> entry : fieldComments.entrySet()) {
            data.put(entry.getKey() + "_comment", entry.getValue().getValue());
        }
        return data;
    }

    private void doSend() {
        if (sending) return;
        setSending(true);

        final Map<String, String> data = collectFormData();
        final File before = rgAction.getCheckedRadioButtonId() == R.id.rbReplace ? beforeFile : null;
        final File after = afterFile;
        final String signatureImage = PhotoUtils.toBase64Jpeg(signatureView.getSignatureBitmap());

        new Thread(() -> {
            int responseCode = -1;
            String validationError = null;
            boolean ok = false;

            try {
                data.put("photo_before", PhotoUtils.toBase64Jpeg(PhotoUtils.load(before, PhotoUtils.UPLOAD_MAX_SIDE)));
                data.put("photo_after", PhotoUtils.toBase64Jpeg(PhotoUtils.load(after, PhotoUtils.UPLOAD_MAX_SIDE)));
                data.put("signature", signatureImage);

                StringBuilder body = new StringBuilder();
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    if (body.length() > 0) body.append('&');
                    body.append(entry.getKey()).append('=')
                            .append(URLEncoder.encode(entry.getValue() != null ? entry.getValue() : "", "UTF-8"));
                }

                URL url = new URL(ServerConfig.BASE_URL + "/insert_dev_data");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(20000);
                // Два фото и подпись по мобильной сети — даём время
                conn.setReadTimeout(60000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Accept", "application/json");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                responseCode = conn.getResponseCode();
                String response = readAll(responseCode < 400 ? conn.getInputStream() : conn.getErrorStream());
                conn.disconnect();
                Log.d("POST", "Response " + responseCode + ": "
                        + (response.length() > 300 ? response.substring(0, 300) : response));

                if (responseCode == 200) {
                    ok = "ok".equals(new JSONObject(response).optString("status"));
                } else if (responseCode == 422) {
                    validationError = firstValidationError(response);
                }
            } catch (Exception e) {
                Log.e("POST_ERROR", "Send error", e);
            }

            final boolean success = ok;
            final int code = responseCode;
            final String message = validationError;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                setSending(false);
                if (success) {
                    Toast.makeText(this, R.string.save_success_cleared, Toast.LENGTH_LONG).show();
                    clearForm();
                    findViewById(R.id.mainScroll).scrollTo(0, 0);
                } else if (message != null) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                } else if (code > 0) {
                    Toast.makeText(this, getString(R.string.server_error_code, code), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.send_error_kept, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void setSending(boolean value) {
        sending = value;
        btnSave.setEnabled(!value);
        btnSave.setText(value ? R.string.sending : R.string.btn_save);
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }

    // {"status":"error","errors":{"apartment":["..."]}} -> первый текст ошибки
    private static String firstValidationError(String response) {
        try {
            JSONObject errors = new JSONObject(response).optJSONObject("errors");
            if (errors == null) return null;
            Iterator<String> keys = errors.keys();
            if (!keys.hasNext()) return null;
            return errors.getJSONArray(keys.next()).optString(0, null);
        } catch (Exception e) {
            return null;
        }
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
        if (getString(R.string.not_selected).equals(value)) {
            return "";
        }
        if (OTHER_OPTION.equals(value)) {
            return otherField.getText().toString();
        }
        return value;
    }
}
