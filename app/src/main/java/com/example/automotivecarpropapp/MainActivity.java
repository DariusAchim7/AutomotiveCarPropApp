package com.example.automotivecarpropapp;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CarPropApp";
    private static final int REQUEST_CODE_PERMISSIONS = 100;

    private Car car;
    private CarPropertyManager carPropertyManager;

    private TextView tvSpeed, tvFuel, tvGear, tvMake, tvModel, tvYear, tvRpm, tvDoors;

    private float fuelCapacityMl = 0f;

    private TextView tvRpmLabel;
    private boolean rpmEstimated = false;
    private int currentGear = 0;
    private float currentSpeedKmh = 0f;
    private SpeedGraphView speedGraph;

    // =========================================================
    // LIFECYCLE
    // =========================================================

    private static final int[] DOOR_AREAS = {
            0x00000001,  // ROW_1_LEFT (șofer)
            0x00000004,  // ROW_1_RIGHT
            0x00000010,  // ROW_2_LEFT
            0x00000040   // ROW_2_RIGHT
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        tvSpeed = findViewById(R.id.tvSpeed);
        tvFuel = findViewById(R.id.tvFuel);
        tvGear = findViewById(R.id.tvGear);
        tvMake = findViewById(R.id.tvMake);
        tvModel = findViewById(R.id.tvModel);
        tvYear = findViewById(R.id.tvYear);
        tvRpm = findViewById(R.id.tvRpm);
        tvDoors = findViewById(R.id.tvDoors);
        tvRpmLabel = findViewById(R.id.tvRpmLabel);
        speedGraph = findViewById(R.id.speedGraph);

        requestCarPermissions();
    }

    private final android.os.Handler graphHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable graphSampler = new Runnable() {
        @Override
        public void run() {
            if (speedGraph != null) {
                speedGraph.addSpeed(currentSpeedKmh);
            }
            graphHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onDestroy() {
        if (carPropertyManager != null) {
            carPropertyManager.unregisterCallback(propertyCallback);
        }
        if (car != null) {
            car.disconnect();
        }
        graphHandler.removeCallbacks(graphSampler);
        super.onDestroy();
    }

    // =========================================================
    // PERMISSIONS
    // =========================================================

    private void requestCarPermissions() {
        String[] permissions = {
                Car.PERMISSION_SPEED,
                Car.PERMISSION_POWERTRAIN,
                Car.PERMISSION_ENERGY,
                Car.PERMISSION_CAR_ENGINE_DETAILED,
                Car.PERMISSION_CONTROL_CAR_DOORS
        };

        boolean needsPermission = false;
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                needsPermission = true;
                break;
            }
        }

        if (needsPermission) {
            requestPermissions(permissions, REQUEST_CODE_PERMISSIONS);
        } else {
            connectToCar();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            connectToCar();
        }
    }

    // =========================================================
    // CAR CONNECTION
    // =========================================================

    private void connectToCar() {
        car = Car.createCar(this);
        if (car == null) {
            Log.e(TAG, "Conexiunea la Car API a eșuat!");
            return;
        }

        carPropertyManager = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
        if (carPropertyManager == null) {
            Log.e(TAG, "CarPropertyManager indisponibil!");
            return;
        }

        Log.d(TAG, "Conectat la Car API");

        readStaticInfo();
        readInitialValues();
        subscribeToVehicleData();
        graphHandler.post(graphSampler);
    }

    // =========================================================
    // STATIC INFO (Manufacturer, Model, Year)
    // =========================================================

    private void readStaticInfo() {
        try {
            CarPropertyValue<String> makeValue =
                    carPropertyManager.getProperty(String.class, VehiclePropertyIds.INFO_MAKE, 0);
            CarPropertyValue<String> modelValue =
                    carPropertyManager.getProperty(String.class, VehiclePropertyIds.INFO_MODEL, 0);
            CarPropertyValue<Integer> yearValue =
                    carPropertyManager.getProperty(Integer.class, VehiclePropertyIds.INFO_MODEL_YEAR, 0);

            String make = makeValue != null ? makeValue.getValue() : "--";
            String model = modelValue != null ? modelValue.getValue() : "--";
            String year = yearValue != null ? String.valueOf(yearValue.getValue()) : "--";

            Log.d(TAG, "Make: " + make + ", Model: " + model + ", Year: " + year);
            tvMake.setText(make);
            tvModel.setText(model);
            tvYear.setText(year);


        } catch (Exception e) {
            Log.e(TAG, "Eroare la citirea info vehicul", e);
        }
    }

    // =========================================================
    // INITIAL VALUES (Speed, Fuel, Gear)
    // =========================================================

    private void readInitialValues() {
        try {
            CarPropertyValue<Float> speed = carPropertyManager.getProperty(
                    Float.class, VehiclePropertyIds.PERF_VEHICLE_SPEED, 0);
            if (speed != null && speed.getValue() != null) {
                updateSpeed(speed.getValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "Eroare la citirea vitezei initiale", e);
        }

        try {
            Float capacity = carPropertyManager.getFloatProperty(
                    VehiclePropertyIds.INFO_FUEL_CAPACITY, 0);
            if (capacity != null && capacity > 0) {
                fuelCapacityMl = capacity;
                Log.d(TAG, "Capacitate rezervor: " + capacity + " ml");
            }
        } catch (Exception e) {
            Log.e(TAG, "Nu am putut citi capacitatea rezervorului", e);
        }

        try {
            CarPropertyValue<Float> fuel = carPropertyManager.getProperty(
                    Float.class, VehiclePropertyIds.FUEL_LEVEL, 0);
            if (fuel != null && fuel.getValue() != null) {
                updateFuel(fuel.getValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "Eroare la citirea combustibilului initial", e);
        }

        try {
            CarPropertyValue<Integer> gear = carPropertyManager.getProperty(
                    Integer.class, VehiclePropertyIds.GEAR_SELECTION, 0);
            if (gear != null && gear.getValue() != null) {
                updateGear(gear.getValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "Eroare la citirea treptei initiale", e);
        }

        try {
            CarPropertyValue<Float> rpm = carPropertyManager.getProperty(
                    Float.class, VehiclePropertyIds.ENGINE_RPM, 0);
            if (rpm != null && rpm.getValue() != null) {
                updateRpm(rpm.getValue());
            }
        } catch (SecurityException se) {
            Log.w(TAG, "RPM: permisiune privileged refuzată -> folosim estimare");
            enableRpmEstimation();
        } catch (Exception e) {
            Log.e(TAG, "Eroare la citirea RPM", e);
            enableRpmEstimation();
        }

        readDoorStatus();
    }

    private void enableRpmEstimation() {
        rpmEstimated = true;
        runOnUiThread(() -> tvRpmLabel.setText("Engine RPM (est.)"));
        updateEstimatedRpm();
    }

    private void updateEstimatedRpm() {
        if (!rpmEstimated) {
            return;
        }
        int rpm;
        switch (currentGear) {
            case 2:  // Reverse - o singură treaptă, viteze mici
                rpm = 1000 + (int) (currentSpeedKmh * 80);
                break;
            case 8:  // Drive - simulăm treptele unei cutii automate
                rpm = estimateRpmForDrive(currentSpeedKmh);
                break;
            default: // Park / Neutral -> ralanti
                rpm = 800;
        }
        rpm = Math.min(rpm, 6500);
        final int finalRpm = rpm;
        runOnUiThread(() -> tvRpm.setText(String.format(Locale.US, "%d", finalRpm)));
    }

    private int estimateRpmForDrive(float speedKmh) {
        if (speedKmh < 1f) {
            return 800;                                   // stă pe loc, ralanti
        } else if (speedKmh < 20f) {                      // treapta 1
            return 1100 + (int) (speedKmh * 90);
        } else if (speedKmh < 40f) {                      // treapta 2
            return 1400 + (int) ((speedKmh - 20f) * 55);
        } else if (speedKmh < 65f) {                      // treapta 3
            return 1500 + (int) ((speedKmh - 40f) * 40);
        } else if (speedKmh < 90f) {                      // treapta 4
            return 1600 + (int) ((speedKmh - 65f) * 30);
        } else if (speedKmh < 120f) {                     // treapta 5
            return 1700 + (int) ((speedKmh - 90f) * 22);
        } else {                                          // treapta 6
            return 1800 + (int) ((speedKmh - 120f) * 18);
        }
    }

    private void readDoorStatus() {
        try {
            boolean anyOpen = false;
            for (int area : DOOR_AREAS) {
                CarPropertyValue<Integer> pos = carPropertyManager.getProperty(
                        Integer.class, VehiclePropertyIds.DOOR_POS, area);
                if (pos != null && pos.getValue() != null && pos.getValue() > 0) {
                    anyOpen = true;
                }
            }
            final boolean open = anyOpen;
            runOnUiThread(() -> tvDoors.setText(open ? "Open!" : "Closed"));
        } catch (SecurityException se) {
            Log.w(TAG, "Doors: permisiune refuzată (privileged)");
            runOnUiThread(() -> tvDoors.setText("N/A"));
        } catch (Exception e) {
            Log.e(TAG, "Eroare la citirea usilor", e);
            runOnUiThread(() -> tvDoors.setText("N/A"));
        }
    }

    private void updateRpm(float rpm) {
        Log.d(TAG, "RPM = " + rpm);
        runOnUiThread(() -> tvRpm.setText(String.format(Locale.US, "%.0f", rpm)));
    }
    // =========================================================
    // REAL-TIME SUBSCRIPTIONS
    // =========================================================

    private void subscribeToVehicleData() {
        try {
            carPropertyManager.registerCallback(propertyCallback,
                    VehiclePropertyIds.PERF_VEHICLE_SPEED,
                    CarPropertyManager.SENSOR_RATE_UI);
        } catch (Exception e) {
            Log.e(TAG, "Nu m-am putut abona la Speed", e);
        }

        try {
            carPropertyManager.registerCallback(propertyCallback,
                    VehiclePropertyIds.FUEL_LEVEL,
                    CarPropertyManager.SENSOR_RATE_UI);
        } catch (Exception e) {
            Log.e(TAG, "Nu m-am putut abona la Fuel", e);
        }

        try {
            carPropertyManager.registerCallback(propertyCallback,
                    VehiclePropertyIds.GEAR_SELECTION,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE);
        } catch (Exception e) {
            Log.e(TAG, "Nu m-am putut abona la Gear", e);
        }

        try {
            carPropertyManager.registerCallback(propertyCallback,
                    VehiclePropertyIds.ENGINE_RPM,
                    CarPropertyManager.SENSOR_RATE_UI);
        } catch (Exception e) {
            Log.e(TAG, "Nu m-am putut abona la RPM", e);
        }

        try {
            carPropertyManager.registerCallback(propertyCallback,
                    VehiclePropertyIds.DOOR_POS,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE);
        } catch (Exception e) {
            Log.e(TAG, "Nu m-am putut abona la Doors", e);
        }
    }

    private final CarPropertyManager.CarPropertyEventCallback propertyCallback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    Object raw = value.getValue();
                    if (!(raw instanceof Number)) {
                        return;
                    }

                    switch (value.getPropertyId()) {
                        case VehiclePropertyIds.PERF_VEHICLE_SPEED:
                            updateSpeed(((Number) raw).floatValue());
                            break;
                        case VehiclePropertyIds.FUEL_LEVEL:
                            updateFuel(((Number) raw).floatValue());
                            break;
                        case VehiclePropertyIds.GEAR_SELECTION:
                            updateGear(((Number) raw).intValue());
                            break;
                        case VehiclePropertyIds.ENGINE_RPM:
                            updateRpm(((Number) raw).floatValue());
                            break;
                        case VehiclePropertyIds.DOOR_POS:
                            readDoorStatus();
                            break;
                    }
                }

                @Override
                public void onErrorEvent(int propertyId, int areaId) {
                    Log.e(TAG, "Eroare pe proprietatea: " + propertyId);
                }
            };

    // =========================================================
    // UI UPDATES
    // =========================================================

    private void updateSpeed(float speedMs) {
        float speedKmh = speedMs * 3.6f;
        currentSpeedKmh = speedKmh;
        updateEstimatedRpm();
        Log.d(TAG, "Speed = " + speedKmh + " km/h");
        runOnUiThread(() ->
                tvSpeed.setText(String.format(Locale.US, "%.0f", speedKmh)));
    }

    private void updateFuel(float fuelMl) {
        final String text;
        final boolean lowFuel;

        if (fuelCapacityMl > 0) {
            float pct = (fuelMl / fuelCapacityMl) * 100f;
            pct = Math.max(0f, Math.min(100f, pct));
            text = String.format(Locale.US, "%.0f%%", pct);
            lowFuel = pct < 20f;
        } else {
            float fuelL = fuelMl / 1000f;
            text = String.format(Locale.US, "%.0f L", fuelL);
            lowFuel = false;
        }

        Log.d(TAG, "Fuel = " + text);
        runOnUiThread(() -> {
            tvFuel.setText(lowFuel ? text + " ⚠" : text);
            tvFuel.setTextColor(lowFuel
                    ? 0xFFFF1744
                    : getColor(R.color.dash_value_text));
        });
    }

    private void updateGear(int gear) {
        currentGear = gear;
        updateEstimatedRpm();
        Log.d(TAG, "Gear = " + gear);
        runOnUiThread(() -> tvGear.setText(gearToString(gear)));
    }

    private String gearToString(int gear) {
        switch (gear) {
            case 1:  return "N (Neutral)";
            case 2:  return "R (Reverse)";
            case 4:  return "P (Park)";
            case 8:  return "D (Drive)";
            default: return "Gear " + gear;
        }
    }
}