package com.jhj37.sensorlogger;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ExecutionException;

/**
 * All log files are stored at /storage/emulated/0/Android/data/com.jhj37.sensorlogger/files
 */
public class MainActivity extends AppCompatActivity implements MessageClient.OnMessageReceivedListener {

    private static final String TAG = "MainActivity";

    private TextView messageView;
    private long startTime;
    private FileWriter fw;
    private String nodeId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        messageView = findViewById(R.id.tv_text);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Please ensure that this device is connected to your smartwatch.")
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Wearable.getMessageClient(this).addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.getMessageClient(this).removeListener(this);
        //End recording when the app goes out of sight.
        String logTitle = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".log";
        messageView.setText(R.string.mobile_instructions);
        try {
            fw.flush();
            fw.close();
            Toast.makeText(this, "New log file " + logTitle + " created.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            //flush() will throw an IOException if the FileWriter has already been closed.
            Log.i(TAG, "Current log already saved.");
        }

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    CapabilityInfo info = Tasks.await(Wearable.getCapabilityClient(MainActivity.this).getCapability(
                            getString(R.string.logger_capability_name), CapabilityClient.FILTER_REACHABLE
                    ));
                    nodeId = info.getNodes().iterator().next().getId();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Capability search failed: " + e);
                } catch (ExecutionException e) {
                    Log.e(TAG, "Capability search interrupted: " + e);
                }

                try {
                    Task<Integer> stopTask = Wearable.getMessageClient(MainActivity.this).sendMessage(
                            nodeId, getString(R.string.stop_message_path), null);
                    Tasks.await(stopTask);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Stop message delivery failed: " + e);
                } catch (ExecutionException e) {
                    Log.e(TAG, "Stop message delivery interrupted: " + e);
                }
            }
        });

        t.start();

    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        String logTitle = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".log";
        switch (messageEvent.getPath()) {
            case "/start": {
                startTime = new Date().getTime();
                messageView.setText(getString(R.string.message_receiving));
                //Create new log file for current recording.
                try {
                    fw = new FileWriter(new File(getExternalFilesDir(null), logTitle));
                } catch (IOException e) {
                    Log.e(TAG, "Error creating log file: " + e);
                }
                break;
            }
            case "/accel": {
                byte[] debug = messageEvent.getData();
                double[] acceleration = toDoubleArray(debug);
                Log.d(TAG, "Accelerometer reading received: " + Arrays.toString(acceleration));
                long time = new Date().getTime() - startTime;
                String logLine = new StringBuilder().append("accel").append(",")
                        .append(time).append(",")
                        .append(acceleration[0]).append(",")
                        .append(acceleration[1]).append(",")
                        .append(acceleration[2]).append("\n").toString();
                writeLine(logLine);
                break;
            }
            case "/gyro": {
                byte[] debug = messageEvent.getData();
                double[] gyro = toDoubleArray(debug);
                Log.d(TAG, "Gyroscope reading receieved: " + Arrays.toString(gyro));
                long time = new Date().getTime() - startTime;
                String logLine = new StringBuilder().append("gyro").append(",")
                        .append(time).append(",")
                        .append(gyro[0]).append(",")
                        .append(gyro[1]).append(",")
                        .append(gyro[2]).append("\n").toString();
                writeLine(logLine);
                break;
            }
            case "/stop": {
                messageView.setText(getString(R.string.mobile_instructions));
                try {
                    fw.close();
                    Toast.makeText(this, "New log file " + logTitle + " created.", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Log.e(TAG, "Error whlie closing log file: " + e);
                }
                break;
            }
        }
    }

    private void writeLine(String line) {
        try {
            fw.write(line);
        } catch (IOException e) {
            Log.e(TAG, "Error writing log line: " + e);
        }
    }

    private double[] toDoubleArray(byte[] byteArray) {
        int times = Double.SIZE / Byte.SIZE;
        double[] doubles = new double[byteArray.length / times];
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] = ByteBuffer.wrap(byteArray, i * times, times).getDouble();
        }
        return doubles;
    }
}
