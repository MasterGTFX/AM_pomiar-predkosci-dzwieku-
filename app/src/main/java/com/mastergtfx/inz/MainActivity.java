package com.mastergtfx.inz;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;

import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor mLight;

    private CameraManager camManager;
    private String cameraId = null;;

    private TextView currentLuxText;
    private TextView luxDifferenceText;
    private TextView flashDetectedText;
    private TextView soundDetectedText;
    private TextView soundSpeedText;
    private TextView startTimestampText;
    private TextView distanceText;
    private SeekBar seekBar;
    private TextView amplitudeText;
    private SwitchCompat detectorSwitch;


    private boolean isDetector = false;
    private boolean waitingForSound = false;
    private boolean soundDetected = false;
    private float lastLux;
    private Long startTimestamp;
    private Long detectedFlashTimestamp;
    private Long detectedSoundTimestamp;
    private float soundSpeed;

    Timer timer = new Timer("Timer");
    MediaRecorder mediaRecorder = null;
    int amplitude;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentLuxText = (TextView) findViewById(R.id.luxValue);
        luxDifferenceText = (TextView) findViewById(R.id.luxDifference);
        flashDetectedText = (TextView) findViewById(R.id.flashDetected);
        soundDetectedText = (TextView) findViewById(R.id.soundDetected);
        startTimestampText = (TextView) findViewById(R.id.startTimestampText);
        soundSpeedText = (TextView) findViewById(R.id.soundSpeedText);
        distanceText = (EditText) findViewById(R.id.distanceInputField);
        seekBar = (SeekBar) findViewById(R.id.seekBar);
        amplitudeText = (TextView) findViewById(R.id.amplitudeText);
        detectorSwitch = (SwitchCompat) findViewById(R.id.isDetector);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (mLight == null) {
            currentLuxText.setText("Light sensor not available!");
        }

        camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = camManager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (ActivityCompat.checkSelfPermission(MainActivity.this, RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(MainActivity.this, WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MainActivity.this, new String[]{RECORD_AUDIO, WRITE_EXTERNAL_STORAGE}, 1);
        }
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if(soundDetected){
            soundDetected = !soundDetected;
            soundDetected();
        }
        if(isDetector) {
            if (lastLux == 0.0f) {
                lastLux = event.values[0];
            }
            float currentlux = event.values[0];
            float luxDifference = currentlux - lastLux;
            boolean flashDetected = false;
            if (luxDifference > 10f) {
                flashDetected = true;
            }

            if (flashDetected) {
                flashDetectedText.setText(String.format("Flash detected at %s", detectedFlashTimestamp));
                detectedFlashTimestamp = System.currentTimeMillis();
                try {
                    waitForSound();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                isDetector = false;
                detectorSwitch.setChecked(false);
            } else {
                flashDetectedText.setText("No flash detected");
            }

            if (luxDifference > 0.0f) {
                luxDifferenceText.setText(String.format("%s", luxDifference));
            } else {
                luxDifferenceText.setText("0%");
            }
            currentLuxText.setText(String.format("%s", currentlux));

            lastLux = currentlux;
        }
    }


    public void changeDetector(View view){
        isDetector = !isDetector;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startMeasurement(View view){
        if(!isDetector) {
            MediaPlayer mediaPlayer = MediaPlayer.create(getBaseContext(), R.raw.sound2);
            try {
                camManager.setTorchMode(cameraId, true);   //Turn ON
                startTimestamp = System.currentTimeMillis();
                startTimestampText.setText(String.format("Measurement started at %s", startTimestamp));


            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            timer.schedule (new TimerTask()
            {
                @Override public void run()
                {
                        mediaPlayer.start();
                }
            }, 0);

            timer.schedule (new TimerTask()
            {
                @Override public void run()
                {
                    try {
                        camManager.setTorchMode(cameraId, false);   //Turn OFF after delay

                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }, 1000);
        }

    }
    public void waitForSound() throws IOException {

        setupMediaRecorder();
        waitingForSound = true;
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (waitingForSound) {
                    amplitude = mediaRecorder.getMaxAmplitude();
                    seekBar.setProgress(amplitude);
                    amplitudeText.setText(Integer.toString(amplitude));
                    if (amplitude > 800) {
                        detectedSoundTimestamp = System.currentTimeMillis();
                        soundDetected = true;
                        waitingForSound = false;
                        mediaRecorder.stop();
                        mediaRecorder.release();
                        mediaRecorder = null;
                        cancel();
                    }
                } else {
                    mediaRecorder.stop();
                    mediaRecorder.release();
                    mediaRecorder = null;
                    waitingForSound = false;
                    cancel();
                }
            }
        };
        timer.scheduleAtFixedRate(task, 0, 10);
    }

    public void setupMediaRecorder() {
        File outputDir = getBaseContext().getCacheDir();
        File outputFile = null;
        try {
            outputFile = File.createTempFile("temp_sound", ".3gp", outputDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(outputFile);
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaRecorder.start();
    }

    public void soundDetected(){
        flashDetectedText.setText(String.format("Sound detected at %s", detectedSoundTimestamp));
        long timeDiff = (detectedSoundTimestamp - detectedFlashTimestamp);
        float distance = Float.parseFloat(distanceText.getText().toString());
        soundSpeed = distance / (timeDiff/1000.0f);
        soundDetectedText.setText(String.format("Delta time: %s", timeDiff));
        soundSpeedText.setText(String.format("Sound speed: %s", soundSpeed));
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
}
