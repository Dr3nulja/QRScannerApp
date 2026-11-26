package com.example.qrscannerapp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Номер прибора из отсканированного кода.
 */
public final class QrParser {

    // Водомер Apator Naxom: «8APA1A10597091LC2507» — 6 знаков префикса, 8 цифр номера, LC + дата партии
    private static final Pattern NAXOM = Pattern.compile("^\\d[A-Z]{3}\\d[A-Z](\\d{8})(?:LC\\d*)?$");

    // Поле серийного номера «+S…» (идентификаторы данных ANSI MH10.8.2: P — модель, 12D — дата, S — серийный номер)
    private static final Pattern SERIAL_FIELD = Pattern.compile("\\+S([0-9A-Za-z]+)(?:\\+|$)");

    private QrParser() {
    }

    public static String parse(String qr) {
        if (qr == null) return null;
        String code = qr.trim();

        // Распределитель E-ITN: «0101-00108732/0623» -> «00108732»
        if (code.contains("-") && code.contains("/")) {
            int dashIndex = code.indexOf('-');
            int slashIndex = code.indexOf('/');
            if (dashIndex < slashIndex) {
                return code.substring(dashIndex + 1, slashIndex);
            }
            return code;
        }

        // Qundis (Q water 5.5): поля через «+», серийный номер — поле «S»
        // «PWME5010N0011 00V00+10VLSE+12D20230607+9D2023+5D3112+S51768820» -> «51768820»
        Matcher serial = SERIAL_FIELD.matcher(code);
        if (serial.find()) {
            return serial.group(1);
        }

        // Водомер Apator: последние 8 цифр номера, без префикса и хвоста LC
        Matcher naxom = NAXOM.matcher(code.toUpperCase());
        if (naxom.matches()) {
            return naxom.group(1);
        }

        // Старый формат: номер с 7-го знака, если начинается на 1083
        if (code.length() > 6 && code.substring(6).startsWith("1083")) {
            return code.substring(6);
        }
        return code;
    }
}
