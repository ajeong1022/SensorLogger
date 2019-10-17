package com.jhj37.sensorlogger;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;

import static com.google.android.gms.wearable.Wearable.getMessageClient;

public class LoggerActivity extends WearableActivity implements SensorEventListener, MessageClient.OnMessageReceivedListener {

    private static final String TAG = "LoggerActivity";

    private Button mLogButton;
    private EditText mSamplingRateText;
    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private boolean recording = false;
    private double[] accelData;
    private double[] gyroData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logger);
        //Prevent the smartwatch display from going to sleep while recording.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mLogButton = findViewById(R.id.bt_log_button);
        mSamplingRateText = findViewById(R.id.et_sampling_rate);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        final boolean logAccel = getIntent().getBooleanExtra("accel", false);
        final boolean logGyro = getIntent().getBooleanExtra("gyro", false);

        if (logAccel) accelerometer =
                mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        if (logGyro) gyroscope =
                mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        mLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recording) stopRecording();
                else {
                    Wearable.getMessageClient(LoggerActivity.this).addListener(LoggerActivity.this);
                    mLogButton.setText(R.string.stop);
                    if (logAccel)
                        mSensorManager.registerListener(LoggerActivity.this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                    if (logGyro)
                        mSensorManager.registerListener(LoggerActivity.this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
                    //Initiate DataDeliveryTask
                    recording = !recording;
                    new Thread(new DataDeliveryTask()).start();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        //End recording if this activity goes out of sight.
        super.onPause();
        if (recording) stopRecording();
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        switch (messageEvent.getPath()) {
            case "/stop":
                //Same effect as when pressing the record button while recording.
                stopRecording();
                break;
        }
    }

    class DataDeliveryTask implements Runnable {

        private String loggerId;

        @Override
        public void run() {
            //Search for node with the sensor logger capability i.e. the smartphone
            try {
                CapabilityInfo info = Tasks.await(Wearable.getCapabilityClient(LoggerActivity.this).getCapability(
                        getString(R.string.logger_capability_name), CapabilityClient.FILTER_REACHABLE
                ));
                loggerId = info.getNodes().iterator().next().getId();
            } catch (InterruptedException e) {
                Log.e(TAG, "Capability search failed: " + e);
            } catch (ExecutionException e) {
                Log.e(TAG, "Capability search interrupted: " + e);
            }

            //Signal begin of data transmission
            try {
                Task<Integer> startTask = Wearable.getMessageClient(LoggerActivity.this).sendMessage(
                        loggerId, getString(R.string.start_message_path), null);
                Tasks.await(startTask);
            } catch (InterruptedException e) {
                Log.e(TAG, "Start message delivery failed: " + e);
            } catch (ExecutionException e) {
                Log.e(TAG, "Start message delivery interrupted: " + e);
            }

            sendData();

            //Signal end of data transmission
            try {
                Task<Integer> stopTask = Wearable.getMessageClient(LoggerActivity.this).sendMessage(
                        loggerId, getString(R.string.stop_message_path), null);
                Tasks.await(stopTask);
            } catch (InterruptedException e) {
                Log.e(TAG, "Stop message delivery failed: " + e);
            } catch (ExecutionException e) {
                Log.e(TAG, "Stop message delivery interrupted: " + e);
            }

            Log.d(TAG, "Thread terminated");
        }

        private void sendData() {
            int samplingRate = Integer.parseInt(mSamplingRateText.getText().toString());
            while (recording) {
                if (loggerId != null) {
                    HashSet<Task<Integer>> tasks = new HashSet<>();
                    if (accelData != null) {
                        byte[] payload = toByteArray(accelData);
                        tasks.add(getMessageClient(LoggerActivity.this).sendMessage(
                                loggerId, getString(R.string.accelerometer_path), payload));
                    }
                    if (gyroData != null) {
                        byte[] payload = toByteArray(gyroData);
                        tasks.add(getMessageClient(LoggerActivity.this).sendMessage(
                                loggerId, getString(R.string.gyroscope_path), payload));
                    }
                    for (Task<Integer> task : tasks) {
                        try {
                            Tasks.await(task);
                        } catch (ExecutionException e) {
                            Log.e(TAG, "Data delivery failed: " + e);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Data delivery interrupted: " + e);
                        }
                    }
                    try {
                        Thread.sleep(samplingRate);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Thread interrupted: " + e);
                    }
                }
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == accelerometer) {
            accelData = new double[3];
            for (int i = 0; i < 3; i++) accelData[i] = event.values[i];
        } else if (event.sensor == gyroscope) {
            gyroData = new double[3];
            for (int i = 0; i < 3; i++) gyroData[i] = event.values[i];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //Don't do anything for now when the sensor accuracy changes.
    }

    private byte[] toByteArray(double[] doubles) {
        int times = Double.SIZE / Byte.SIZE;
        byte[] bytes = new byte[doubles.length * times];
        for (int i = 0; i < doubles.length; i++) {
            ByteBuffer.wrap(bytes, i * times, times).putDouble(doubles[i]);
        }
        return bytes;
    }

    private void stopRecording() {
        Wearable.getMessageClient(LoggerActivity.this).removeListener(LoggerActivity.this);
        mLogButton.setText(R.string.start);
        mSensorManager.unregisterListener(LoggerActivity.this);
        recording = !recording;
    }
}
