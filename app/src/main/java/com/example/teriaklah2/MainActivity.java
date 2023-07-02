package com.example.teriaklah2;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_CODE = 1;
    private static final int RECORDING_DURATION = 15000; // 15 detik

    private EditText playerNameEditText;
    private ListView scoreListView;
    private ArrayAdapter<String> scoreAdapter;

    private MediaRecorder mediaRecorder;
    private String filePath;

    private SQLiteOpenHelper dbHelper;
    private SQLiteDatabase database;

    private Handler handler;
    private Runnable stopRecordingRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playerNameEditText = findViewById(R.id.playerNameEditText);
        scoreListView = findViewById(R.id.scoreListView);

        scoreAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        scoreListView.setAdapter(scoreAdapter);

        dbHelper = new DatabaseHelper(this);
        database = dbHelper.getWritableDatabase();

        handler = new Handler();
        stopRecordingRunnable = this::stopRecording;
    }

    public void startRecording(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION_CODE);
        } else {
            startRecordingInternal();
        }
    }

    private void startRecordingInternal() {
        // Ubah direktori penyimpanan untuk Android 11 ke getExternalFilesDir()
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File audioFile = new File(storageDir, "recording.3gp");
        filePath = audioFile.getAbsolutePath();

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(filePath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            handler.postDelayed(stopRecordingRunnable, RECORDING_DURATION);
            Toast.makeText(this, "Rekaman dimulai", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Toast.makeText(this, "Rekaman berhenti", Toast.LENGTH_SHORT).show();
            calculateScore();
        }
    }

    private void calculateScore() {
        List<Integer> amplitudeList = getAmplitudeList();
        int score = 0;
        for (int amplitude : amplitudeList) {
            score += amplitude;
        }

        String playerName = playerNameEditText.getText().toString().trim();
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.ScoreEntry.COLUMN_PLAYER_NAME, playerName);
        values.put(DatabaseContract.ScoreEntry.COLUMN_SCORE, score);
        database.insert(DatabaseContract.ScoreEntry.TABLE_NAME, null, values);

        scoreAdapter.add(playerName + ": " + score);
    }

    private List<Integer> getAmplitudeList() {
        List<Integer> amplitudeList = new ArrayList<>();
        try {
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(filePath);

            int trackCount = extractor.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                    extractor.selectTrack(i);
                    ByteBuffer buffer = ByteBuffer.allocate(format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                    while (extractor.readSampleData(buffer, 0) >= 0) {
                        int amplitude = calculateAmplitude(buffer);
                        amplitudeList.add(amplitude);
                        buffer.clear();
                        extractor.advance();
                    }
                    break;
                }
            }
            extractor.release();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return amplitudeList;
    }

    private int calculateAmplitude(ByteBuffer buffer) {
        int maxAmplitude = 0;
        buffer.rewind();
        while (buffer.hasRemaining()) {
            short audioSample = buffer.getShort();
            int amplitude = Math.abs(audioSample);
            if (amplitude > maxAmplitude) {
                maxAmplitude = amplitude;
            }
        }
        return maxAmplitude;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecordingInternal();
            } else {
                Toast.makeText(this, "Izin rekaman suara ditolak", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void clearScores(View view) {
        database.delete(DatabaseContract.ScoreEntry.TABLE_NAME, null, null);
        scoreAdapter.clear();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (database != null) {
            database.close();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
