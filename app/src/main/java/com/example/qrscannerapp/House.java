package com.example.qrscannerapp;

public class House {
    public int id;
    public String city;
    public String address;

    @Override
    public String toString() {
        return city + ", " + address;
    }
}
