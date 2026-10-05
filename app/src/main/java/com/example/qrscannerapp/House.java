package com.example.qrscannerapp;

public class House {
    public static final String DEVICE_ALLOCATOR = "allocator";
    public static final String DEVICE_WATER_METER = "water_meter";
    public static final String ACTION_INSTALL = "install";
    public static final String ACTION_REPLACE = "replace";

    public int id;
    public String city;
    public String address;

    // Задача, которую контора выставила в дашборде. null = не задано, выбор вручную
    public String deviceType;
    public String actionType;

    public boolean hasTask() {
        return deviceType != null && actionType != null;
    }

    // Копия дома с конкретной задачей (одна строка в списке на каждую задачу)
    public House withTask(String deviceType, String actionType) {
        House copy = new House();
        copy.id = id;
        copy.city = city;
        copy.address = address;
        copy.deviceType = DEVICE_ALLOCATOR.equals(deviceType) || DEVICE_WATER_METER.equals(deviceType)
                ? deviceType : null;
        copy.actionType = ACTION_INSTALL.equals(actionType) || ACTION_REPLACE.equals(actionType)
                ? actionType : null;
        return copy;
    }

    public static int taskLabelRes(String deviceType, String actionType) {
        boolean allocator = DEVICE_ALLOCATOR.equals(deviceType);
        boolean replace = ACTION_REPLACE.equals(actionType);
        if (allocator) {
            return replace ? R.string.task_allocator_replace : R.string.task_allocator_install;
        }
        return replace ? R.string.task_water_meter_replace : R.string.task_water_meter_install;
    }

    @Override
    public String toString() {
        return city + ", " + address;
    }
}
