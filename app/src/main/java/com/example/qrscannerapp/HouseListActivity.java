package com.example.qrscannerapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
    TextView tvLoading;
    ArrayList<House> houses = new ArrayList<>();

    private static final String TAG = "HouseListActivity";

    // 🔴 ТЕСТ: пока сервер не отдаёт "tasks", подставляем задачи сами.
    // Когда сервер начнёт отдавать задачи, поставить false (или удалить вместе с testTasks)
    private static final boolean USE_TEST_TASKS = true;

    private static JSONArray testTasks(int houseId) throws org.json.JSONException {
        switch (houseId) {
            case 1: // Kalamaja 55: одна задача
                return new JSONArray("[{\"device_type\":\"water_meter\",\"action_type\":\"replace\"}]");
            case 2: // Majaka 5: две задачи
                return new JSONArray("[{\"device_type\":\"allocator\",\"action_type\":\"install\"},"
                        + "{\"device_type\":\"water_meter\",\"action_type\":\"install\"}]");
            default: // остальные дома: без задачи, ручной выбор
                return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_house_list);

        View houseListRoot = findViewById(R.id.houseListRoot);
        final int initialPaddingLeft = houseListRoot.getPaddingLeft();
        final int initialPaddingTop = houseListRoot.getPaddingTop();
        final int initialPaddingRight = houseListRoot.getPaddingRight();
        final int initialPaddingBottom = houseListRoot.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(houseListRoot, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    initialPaddingLeft + systemBars.left,
                    initialPaddingTop + systemBars.top,
                    initialPaddingRight + systemBars.right,
                    initialPaddingBottom + systemBars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(houseListRoot);

        listView = findViewById(R.id.listHouses);
        tvLoading = findViewById(R.id.tvLoading);
        listView.setEmptyView(tvLoading);

        loadHousesFromServer();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            House selectedHouse = houses.get(position);

            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("HOUSE_ID", selectedHouse.id);
            intent.putExtra(
                    "HOUSE_ADDRESS",
                    selectedHouse.city + ", " + selectedHouse.address
            );
            if (selectedHouse.hasTask()) {
                intent.putExtra("DEVICE_TYPE", selectedHouse.deviceType);
                intent.putExtra("ACTION_TYPE", selectedHouse.actionType);
            }
            startActivity(intent);
        });
    }

    private void loadHousesFromServer() {
        new Thread(() -> {
            try {
                // --- SSL IGNORE (ТОЛЬКО ДЛЯ ТЕСТОВ) ---
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                };
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
                // -------------------------------------

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
                ArrayList<House> loaded = new ArrayList<>();

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);

                    House house = new House();
                    house.id = obj.getInt("id");
                    house.city = obj.getString("City");
                    house.address = obj.getString("address");

                    // Задачи из дашборда: каждая задача = отдельная строка в списке
                    JSONArray tasks = obj.optJSONArray("tasks");
                    if (USE_TEST_TASKS && (tasks == null || tasks.length() == 0)) {
                        tasks = testTasks(house.id);
                    }
                    if (tasks == null || tasks.length() == 0) {
                        loaded.add(house);
                    } else {
                        for (int t = 0; t < tasks.length(); t++) {
                            JSONObject task = tasks.getJSONObject(t);
                            loaded.add(house.withTask(
                                    task.optString("device_type", null),
                                    task.optString("action_type", null)
                            ));
                        }
                    }

                    // 🔴 ОТЛАДКА (можно удалить)
                    Log.d(TAG, "Loaded house id=" + house.id);
                }

                runOnUiThread(() -> {
                    houses.clear();
                    houses.addAll(loaded);
                    ArrayAdapter<House> adapter = new HouseAdapter(this, houses);
                    listView.setAdapter(adapter);
                });

            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки домов", e);

                runOnUiThread(() -> {
                    tvLoading.setText("Majade laadimine ebaõnnestus. Kontrolli internetti ja proovi uuesti.");
                    Toast.makeText(this, "Majade laadimine ebaõnnestus", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static class HouseAdapter extends ArrayAdapter<House> {

        private final LayoutInflater inflater;

        HouseAdapter(HouseListActivity context, ArrayList<House> houses) {
            super(context, R.layout.item_house, houses);
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_house, parent, false);
                holder = new ViewHolder();
                holder.title = convertView.findViewById(R.id.tvHouseTitle);
                holder.address = convertView.findViewById(R.id.tvHouseAddress);
                holder.task = convertView.findViewById(R.id.tvHouseTask);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            House house = getItem(position);
            if (house != null) {
                holder.title.setText(house.address);
                holder.address.setText(house.city);
                if (house.hasTask()) {
                    holder.task.setText(House.taskLabelRes(house.deviceType, house.actionType));
                    holder.task.setVisibility(View.VISIBLE);
                } else {
                    holder.task.setVisibility(View.GONE);
                }
            }

            return convertView;
        }

        private static class ViewHolder {
            TextView title;
            TextView address;
            TextView task;
        }
    }
}
