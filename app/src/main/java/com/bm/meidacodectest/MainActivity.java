package com.bm.meidacodectest;

import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, MediaCoedecEncoder.OnAudioEncodedListener, MediaCodecDecoder.AudioDecodedListener, SurfaceHolder.Callback, Camera.PreviewCallback {

    private AudioPlayer mAudioPlayer;
    private String mPath;
    private MediaCoedecEncoder mediaCoedecEncoder;
    private boolean readingFile;
    private MediaCodecDecoder mediaCodecDecoder;
    private Camera mCamera;
    private VideoEncoder videoEncoder;
    private byte[] mH264 = new byte[460800];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnPlayAudio = (Button) findViewById(R.id.btn_play_audio);
        Button btnStopAudio = (Button) findViewById(R.id.btn_stop_audio);
        Button btn_take_photo = (Button) findViewById(R.id.btn_take_photo);
        Button btn_codec_encode_pcm = (Button) findViewById(R.id.btn_codec_encode_pcm);
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(this);

        btnPlayAudio.setOnClickListener(this);
        btnStopAudio.setOnClickListener(this);
        btn_codec_encode_pcm.setOnClickListener(this);
        btn_take_photo.setOnClickListener(this);

        mAudioPlayer = new AudioPlayer();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_play_audio:
                mPath = Environment.getExternalStorageDirectory() + "/audioRecord.pcm";
                if (mAudioPlayer.startPlayer(mPath)) {
                    Log.e("youtl：", "开始播放");
                }
                break;
            case R.id.btn_stop_audio:
                Log.e("youtl:", "停止播放");
                mAudioPlayer.stopPlayer();
                break;
            case R.id.btn_codec_encode_pcm:
                mediaCoedecEncoder = new MediaCoedecEncoder();
                mediaCodecDecoder = new MediaCodecDecoder();
                mediaCodecDecoder.setAudioDecodeListener(this);
                mediaCoedecEncoder.setAudioEncodedListener(this);
                mAudioPlayer = new AudioPlayer();
                mAudioPlayer.startPlayer();
                boolean open = mediaCoedecEncoder.open();
                boolean open1 = mediaCodecDecoder.open();
                if (open && open1) {
                    Log.e("youtl:", "codec init success");
                    mPath = Environment.getExternalStorageDirectory() + "/output.pcm";
                    // 开始一个线程读取pcm文件数据交给codec encoder的inbuffer进行编码
                    new Thread(new ReadFile()).start();
                    // 开启一个线程读取codec encoder的outBuffer中的数据
                    new Thread(new ReadCodecOutBuffer()).start();

                    new Thread(new Decoder()).start();
                }
                break;
            case R.id.btn_take_photo:
                mCamera.takePicture(null, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        Log.e("youtl:", "onPictureTaken+");
                        // 讲data写入sd卡保存为一个Jpeg图片
                        String path = Environment.getExternalStorageDirectory() + "/takephone.jpg";
                        FileOutputStream fileOutputStream = null;
                        try {
                            fileOutputStream = new FileOutputStream(path);
                            fileOutputStream.write(data);
                            Log.e("youtl:", "onPictureTaken -");
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                fileOutputStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
                break;
            default:
                break;
        }
    }

    // 将接收到的压缩数据交给mediacodecDecoder 进行解码
    @Override
    public void onFrameEncoded(byte[] encoded, long presentationTimeUs) {
        Log.e("youtl:", "编码完成的数据;" + encoded.length);
        mediaCodecDecoder.decode(encoded, presentationTimeUs);
    }

    /**
     * MediaCodecDecoder 解码出来的数据回调，把解码出来的pcm原始数据交给player直接播放
     *
     * @param outData
     * @param time
     */
    @Override
    public void onFrameDecoded(byte[] outData, long time) {
        Log.e("youtl:", "解码完成的数据 :" + outData.length);
        mAudioPlayer.play(outData, 0, outData.length);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // 初始化camera
        Log.e("youtl;", "surfaceCreated");
        mCamera = Camera.open();
        videoEncoder = new VideoEncoder();
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.setDisplayOrientation(90);
        mCamera.setPreviewCallback(this);
        mCamera.startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.e("youtl;", "surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e("youtl;", "surfaceDestroyed");
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Log.e("youtl:", "预览的数据" + data.length);
        int count = videoEncoder.offerEncoder(data, mH264);
        if (count > 0){
            Log.e("youtl：","h254 :" + mH264.length);
        }
    }

    private class Decoder implements Runnable {
        @Override
        public void run() {
            while (!readingFile) {
                mediaCodecDecoder.retrieve();
            }
        }
    }

    private class ReadCodecOutBuffer implements Runnable {

        @Override
        public void run() {
            while (!readingFile) {
                mediaCoedecEncoder.retrieve();
            }
        }
    }

    private class ReadFile implements Runnable {

        @Override
        public void run() {
            byte[] buffer = new byte[2048];
            FileInputStream inputStream = null;
            try {
                inputStream = new FileInputStream(mPath);
                int length = 0;
                while ((length = inputStream.read(buffer)) != -1) {
                    long presentationTimeUs = (System.nanoTime()) / 1000L;
                    Log.e("编码中,,,", length + "");
                    mediaCoedecEncoder.encodePCMToAAC(buffer, presentationTimeUs);
                }
                Log.e("youtl:", "文件已经读取完成");
                readingFile = true;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
