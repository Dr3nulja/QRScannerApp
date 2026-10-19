package com.example.qrscannerapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import androidx.appcompat.app.AlertDialog;

public class HouseListActivity extends AppCompatActivity {

    ListView listView;
    TextView tvLoading;
    TextView tvInstaller;
    private boolean loading = false;
    ArrayList<House> houses = new ArrayList<>();

    private static final String TAG = "HouseListActivity";

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
        // Ошибка или пустой список — нажатие загружает заново
        tvLoading.setOnClickListener(v -> loadHousesFromServer());

        tvInstaller = findViewById(R.id.tvInstaller);
        tvInstaller.setOnClickListener(v -> askInstallerName());
        updateInstallerLabel();
        if (InstallerPrefs.getName(this).isEmpty()) {
            askInstallerName();
        }

        loadHousesFromServer();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (InstallerPrefs.getName(this).isEmpty()) {
                askInstallerName();
                return;
            }
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

    @Override
    protected void onRestart() {
        super.onRestart();
        // Вернулись из формы — контора могла добавить или снять задачи
        loadHousesFromServer();
    }

    private void updateInstallerLabel() {
        String name = InstallerPrefs.getName(this);
        tvInstaller.setText(getString(R.string.installer_change, name.isEmpty() ? "—" : name));
    }

    // Имя монтажника: обязательно один раз, потом можно сменить нажатием в шапке
    private void askInstallerName() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(R.string.installer_hint);
        input.setText(InstallerPrefs.getName(this));
        input.setSelection(input.getText().length());

        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(padding, 0, padding, 0);
        container.addView(input);

        boolean firstTime = InstallerPrefs.getName(this).isEmpty();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.installer_dialog_title)
                .setMessage(R.string.installer_dialog_message)
                .setView(container)
                .setCancelable(!firstTime)
                .setPositiveButton(R.string.btn_ok, null)
                .create();
        // Своя проверка, чтобы пустое имя не закрывало диалог
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError(getString(R.string.installer_required));
                return;
            }
            InstallerPrefs.setName(this, name);
            updateInstallerLabel();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void loadHousesFromServer() {
        if (loading) return;
        loading = true;
        tvLoading.setText(R.string.house_list_loading);

        new Thread(() -> {
            try {
                URL url = new URL(ServerConfig.BASE_URL + "/get_house_list");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new java.io.IOException("HTTP " + conn.getResponseCode());
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                );

                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();
                conn.disconnect();

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
                }

                runOnUiThread(() -> {
                    loading = false;
                    tvLoading.setText(R.string.house_list_empty);
                    houses.clear();
                    houses.addAll(loaded);
                    ArrayAdapter<House> adapter = new HouseAdapter(this, houses);
                    listView.setAdapter(adapter);
                });

            } catch (Exception e) {
                Log.e(TAG, "Ошибка загрузки домов", e);

                runOnUiThread(() -> {
                    loading = false;
                    houses.clear();
                    listView.setAdapter(null);
                    tvLoading.setText(R.string.house_list_error);
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
