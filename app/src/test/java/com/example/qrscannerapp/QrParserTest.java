package com.example.qrscannerapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class QrParserTest {

    @Test
    public void naxomWaterMeterKeepsEightDigitSerial() {
        // Реальный код с водомера Apator Naxom (на корпусе: 8 APA1A 10597091, LC 2507)
        assertEquals("10597091", QrParser.parse("8APA1A10597091LC2507"));
        assertEquals("10831234", QrParser.parse("8APA1A10831234LC2507"));
        assertEquals("10597091", QrParser.parse("8APA1A10597091"));
        assertEquals("10597091", QrParser.parse(" 8apa1a10597091lc2507 "));
    }

    @Test
    public void qundisWaterMeterTakesSerialField() {
        // Реальный код с водомера Qundis Q water 5.5 (на корпусе: 51768820)
        assertEquals("51768820", QrParser.parse("PWME5010N0011 00V00+10VLSE+12D20230607+9D2023+5D3112+S51768820"));
        // Порядок полей может быть другим
        assertEquals("51768820", QrParser.parse("PWME5010N0011 00V00+S51768820+12D20230607"));
    }

    @Test
    public void allocatorTakesNumberBetweenDashAndSlash() {
        assertEquals("00108732", QrParser.parse("0101-00108732/0623"));
    }

    @Test
    public void oldFormatAndUnknownCodesStayAsBefore() {
        assertEquals("10831234", QrParser.parse("ABCDEF10831234"));
        assertEquals("12345", QrParser.parse("12345"));
    }
}
