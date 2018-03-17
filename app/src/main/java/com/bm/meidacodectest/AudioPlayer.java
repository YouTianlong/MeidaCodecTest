package com.bm.meidacodectest;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by youtl on 2018/3/16.
 */

public class AudioPlayer {

    private static final String TAG = "AudioPlayer";
    private static final int DEFAULT_STRAM_TYPE = AudioManager.STREAM_MUSIC;
    private static final int DEFAULT_SIMPLE_RATE = 44100;
    private static final int DEFAULT_CHANNE_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int DEFAULT_PALY_MODE = AudioTrack.MODE_STREAM;
    private boolean isPlayStarted = false;
    private AudioTrack mAudioTrack;
    private int minBufferSize;
    private ThreadPoolExecutor threadPoolExecutor;
    private String mPath;

    public boolean startPlayer(String path) {
        this.mPath = path;
        ArrayBlockingQueue<Runnable> arrayBlockingQueue = new ArrayBlockingQueue<Runnable>(5);
        CustomThreadFactory customThreadFactory = new CustomThreadFactory();
        threadPoolExecutor = new ThreadPoolExecutor(5,
                10, 200, TimeUnit.MINUTES, arrayBlockingQueue, customThreadFactory);
        boolean b = startPlayer(DEFAULT_STRAM_TYPE, DEFAULT_SIMPLE_RATE, DEFAULT_CHANNE_CONFIG, DEFAULT_AUDIO_FORMAT);
        if (b){
            threadPoolExecutor.execute(new ReadFile());
        }
        return b;
    }

    public boolean startPlayer(){
        return startPlayer(DEFAULT_STRAM_TYPE, DEFAULT_SIMPLE_RATE, DEFAULT_CHANNE_CONFIG, DEFAULT_AUDIO_FORMAT);
    }

    private boolean startPlayer(int streamType, int simpleRateInHz, int channeConfig, int audioFormat) {
        if (isPlayStarted) {
            Log.e(TAG, "player is already started !");
            return false;
        }

        minBufferSize = AudioRecord.getMinBufferSize(simpleRateInHz, channeConfig, audioFormat);
        if (minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid parameter !");
            return false;
        }

        Log.e(TAG , "getMinBufferSize = "+ minBufferSize +" bytes !");
        mAudioTrack = new AudioTrack(streamType,simpleRateInHz,channeConfig,audioFormat, minBufferSize,DEFAULT_PALY_MODE);
        if (mAudioTrack.getState() == AudioTrack.STATE_UNINITIALIZED){
            Log.e(TAG, "AudioTrack initialize fail !");
            return false;
        }

        isPlayStarted = true;
        Log.e(TAG, "Start audio player success !");
        return true;
    }

    public int getMinBufferSize(){
        return minBufferSize;
    }

    public void stopPlayer(){

        if (!isPlayStarted){
            return;
        }
        threadPoolExecutor.shutdown();
        if (mAudioTrack.getState() == AudioTrack.PLAYSTATE_PLAYING){
            mAudioTrack.stop();
        }

        mAudioTrack.release();
        isPlayStarted = false;
        Log.e(TAG, "Stop audio player success !");
    }

    public boolean play(byte[] audioData,int offsetInBytes,int sizeInBytes){
        if (!isPlayStarted){
            Log.e(TAG, "Player not started !");
            return false;
        }

        if (sizeInBytes < minBufferSize){
            Log.e(TAG, "audio data is not enough !");
            return false;
        }

        if (mAudioTrack.write(audioData,offsetInBytes,sizeInBytes) != sizeInBytes){
            Log.e(TAG, "Could not write all the samples to the audio device !");
        }

        mAudioTrack.play();
        Log.e(TAG , "OK, Played "+sizeInBytes+" bytes !");
        return true;
    }

    class ReadFile implements Runnable{
        @Override
        public void run() {
            Log.e("run:","------------");
            byte[] bytes = new byte[minBufferSize];
            FileInputStream inputStream = null;
            try {
                inputStream = new FileInputStream(mPath);
                int length = 0;
                while ((length = inputStream.read(bytes))!= -1){
                    Log.e("length:","------------" + length);
                    play(bytes,0,length);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class CustomThreadFactory implements ThreadFactory {

        private AtomicInteger count = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            String threadName = MainActivity.class.getSimpleName() + count.addAndGet(1);
            t.setName(threadName);
            return t;
        }
    }
}
