package com.example.qrscannerapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class HouseListActivity extends AppCompatActivity {

    ListView listView;
    ArrayList<String> houses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_house_list);

        listView = findViewById(R.id.listHouses);

        houses = new ArrayList<>();
        houses.add("Test korter 1");
        houses.add("Test korter 2");
        houses.add("Test korter 3");

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, houses);

        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedHouse = houses.get(position);

            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("HOUSE_ADDRESS", selectedHouse);
            startActivity(intent);
        });
    }
}
