package com.jhj37.sensorlogger;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

public class MainActivity extends WearableActivity {

    private Button mSensorSelectionButton;
    private CheckBox mAccelBox;
    private CheckBox mGyroBox;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSensorSelectionButton = findViewById(R.id.bt_sensor_selection);
        mAccelBox = findViewById(R.id.cb_accel);
        mGyroBox = findViewById(R.id.cb_gyro);

        mSensorSelectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean accel = mAccelBox.isChecked();
                boolean gyro = mGyroBox.isChecked();

                if (!accel && !gyro) Toast
                        .makeText(MainActivity.this, "Select at least one sensor", Toast.LENGTH_SHORT).show();
                else {
                    Intent intent = new Intent(MainActivity.this, LoggerActivity.class);
                    intent.putExtra("accel", accel);
                    intent.putExtra("gyro", gyro);
                    startActivity(intent);
                }
            }
        });

        // Enables Always-on
        setAmbientEnabled();
    }
}
