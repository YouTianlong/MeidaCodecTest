package com.bm.meidacodectest;

import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    private AudioPlayer mAudioPlayer;
    private String mPath;
    private int a;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnPlayAudio = (Button) findViewById(R.id.btn_play_audio);
        Button btnStopAudio = (Button) findViewById(R.id.btn_stop_audio);

        btnPlayAudio.setOnClickListener(this);
        btnStopAudio.setOnClickListener(this);

        mAudioPlayer = new AudioPlayer();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_play_audio:
                mPath = Environment.getExternalStorageDirectory() + "/output.pcm";
                if (mAudioPlayer.startPlayer(mPath)){
                    Log.e("youtl：","开始播放");
                }
                break;
            case R.id.btn_stop_audio:
                Log.e("","停止播放");
                mAudioPlayer.stopPlayer();
                break;
            default:
                break;
        }
    }




}
