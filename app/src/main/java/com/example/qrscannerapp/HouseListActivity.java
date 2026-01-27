package com.example.qrscannerapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HouseListActivity extends AppCompatActivity {

    ListView listView;
    ArrayList<String> houses = new ArrayList<>();

    private static final String TAG = "HouseListActivity"; // логи ошибок

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_house_list);

        listView = findViewById(R.id.listHouses);

        loadHousesFromServer();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedHouse = houses.get(position);

            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("HOUSE_ADDRESS", selectedHouse);
            startActivity(intent);
        });
    }

    private void loadHousesFromServer() {
        new Thread(() -> {
            try {
                // --- Игнорируем проверку SSL (для теста/эмулятора) ---
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                            public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                        }
                };
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
                // ---------------------------------------------------------

                URL url = new URL("https://arvestused.agr-torud.ee/get_house_list");
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream())
                );

                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();

                JSONArray array = new JSONArray(json.toString());
                houses.clear();

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String city = obj.getString("City");
                    String address = obj.getString("address");
                    houses.add(city + ", " + address);
                }

                runOnUiThread(() -> {
                    ArrayAdapter<String> adapter =
                            new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, houses);
                    listView.setAdapter(adapter);
                });

            } catch (Exception e) {
                e.printStackTrace(); // вывод полной ошибки в Logcat
                Log.e(TAG, "Viga majade laadimisel " + e.getMessage());

                runOnUiThread(() ->
                        Toast.makeText(
                                this,
                                "Viga majade laadimisel " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }).start();
    }
}
