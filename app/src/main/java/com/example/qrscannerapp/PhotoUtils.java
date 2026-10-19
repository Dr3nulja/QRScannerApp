package com.example.qrscannerapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Фото с камеры в полном размере лежит файлом; отсюда — уменьшенная копия
 * с правильным поворотом (камеры часто пишут портрет как пейзаж + EXIF-флаг).
 */
public final class PhotoUtils {

    // Для отправки: хватает, чтобы разглядеть показания и пломбы, и не тяжело для мобильной сети
    public static final int UPLOAD_MAX_SIDE = 1600;
    public static final int PREVIEW_MAX_SIDE = 800;

    private PhotoUtils() {
    }

    public static Bitmap load(File file, int maxSide) {
        if (file == null || !file.exists() || file.length() == 0) return null;

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        // Грубое уменьшение при декодировании, чтобы не держать в памяти 12-мегапиксельный кадр
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
        if (bitmap == null) return null;

        Matrix matrix = new Matrix();
        float scale = Math.min(1f, (float) maxSide / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        if (scale < 1f) {
            matrix.postScale(scale, scale);
        }
        int rotation = exifRotation(file);
        if (rotation != 0) {
            matrix.postRotate(rotation);
        }
        if (matrix.isIdentity()) return bitmap;

        Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (result != bitmap) bitmap.recycle();
        return result;
    }

    public static String toBase64Jpeg(Bitmap bitmap) {
        if (bitmap == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private static int exifRotation(File file) {
        try {
            int orientation = new ExifInterface(file.getPath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }
}
