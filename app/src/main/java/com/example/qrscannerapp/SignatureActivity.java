package com.example.qrscannerapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Подпись на весь экран (горизонтально). Картинка передаётся через файл в кэше:
 * в Intent большая Bitmap не влезает.
 */
public class SignatureActivity extends AppCompatActivity {

    private static final String INPUT_FILE = "signature_input.png";
    private static final String RESULT_FILE = "signature_large.png";

    private SignatureView signatureView;

    /**
     * Открыть большой экран; уже нарисованная маленькая подпись показывается и там
     */
    public static Intent intent(Context context, Bitmap current) {
        File input = new File(context.getCacheDir(), INPUT_FILE);
        if (current != null) {
            writePng(current, input);
        } else {
            input.delete();
        }
        return new Intent(context, SignatureActivity.class);
    }

    /**
     * Подпись с большого экрана или null, если её стёрли
     */
    public static Bitmap readResult(Context context) {
        File result = new File(context.getCacheDir(), RESULT_FILE);
        if (!result.exists()) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(result.getAbsolutePath());
        result.delete();
        return bitmap;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signature);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.signatureRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left + dp(16), bars.top + dp(12), bars.right + dp(16), bars.bottom + dp(12));
            return insets;
        });

        signatureView = findViewById(R.id.largeSignatureView);
        File input = new File(getCacheDir(), INPUT_FILE);
        if (savedInstanceState == null && input.exists()) {
            signatureView.setSignature(BitmapFactory.decodeFile(input.getAbsolutePath()));
        }

        Button btnClear = findViewById(R.id.btnLargeClear);
        Button btnCancel = findViewById(R.id.btnLargeCancel);
        Button btnDone = findViewById(R.id.btnLargeDone);

        btnClear.setOnClickListener(v -> signatureView.clear());
        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        btnDone.setOnClickListener(v -> {
            File result = new File(getCacheDir(), RESULT_FILE);
            Bitmap signature = signatureView.getSignatureBitmap();
            if (signature != null) {
                writePng(signature, result);
            } else {
                result.delete();
            }
            setResult(RESULT_OK);
            finish();
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static void writePng(Bitmap bitmap, File file) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            file.delete();
        }
    }
}
