package com.bm.meidacodectest;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by 游天龙 on 2018/3/17.
 */

public class MediaCodecDecoder {

    private static final String TAG = MediaCodecDecoder.class.getSimpleName();
    private boolean mIsFirstFrame = true;
    private boolean isOpened;
    private static final String DEFAULT_MIME_TYPE = "audio/mp4a-latm";
    private static final int DEFAULT_PROFILE_LEVEL = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
    private MediaCodec mediaCodec;

    public boolean open() {
        try {
            mediaCodec = MediaCodec.createDecoderByType(DEFAULT_MIME_TYPE);

            MediaFormat mediaFormat = new MediaFormat();
            mediaFormat.setString(MediaFormat.KEY_MIME, DEFAULT_MIME_TYPE);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, DEFAULT_PROFILE_LEVEL);
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

            mediaCodec.configure(mediaFormat,null,null,0);
            mediaCodec.start();
            isOpened = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.e(TAG,"open audio decoder success");
        return true;
    }

    public void close(){
        Log.e(TAG,"close audio decoder (");
        if (!isOpened){
            return;
        }

        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
        isOpened = false;
        Log.e(TAG,"close audio decoder )");
    }

    public synchronized boolean decode(byte[] input,long presentationTime){
        Log.e(TAG,"decode lenght: " + input.length);
        if (!isOpened){
            return false;
        }

        try {
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(1000);
            if (inputBufferIndex >= 0){
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(input);
                if (mIsFirstFrame){
                    mediaCodec.queueInputBuffer(inputBufferIndex,
                            0,input.length,presentationTime,MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                    mIsFirstFrame = false;
                }else{
                    mediaCodec.queueInputBuffer(inputBufferIndex,
                            0,input.length,presentationTime,0);
                }
            }
        }catch (Throwable throwable){
            throwable.printStackTrace();
            return false;
        }
        Log.e(TAG," decode -");
        return true;
    }

    public synchronized boolean retrieve(){
        Log.e(TAG,"decode retrieve +");
        if (!isOpened){
            return false;
        }

        try {
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 1000);
            if (outputBufferIndex >= 0){
                Log.e(TAG,"decode retrieve frame :" + bufferInfo.size);
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);
                if (mAudioDecodeListener != null){
                    mAudioDecodeListener.onFrameDecoded(outData,bufferInfo.presentationTimeUs);
                }
                mediaCodec.releaseOutputBuffer(outputBufferIndex,false);
            }
        }catch (Throwable throwable){
            throwable.printStackTrace();
            return false;
        }
        Log.e(TAG,"decode retrieve -");
        return true;
    }

    interface AudioDecodedListener{
        void onFrameDecoded(byte[] outData,long time);
    }

    private AudioDecodedListener mAudioDecodeListener;

    public void setAudioDecodeListener(AudioDecodedListener listener){
        this.mAudioDecodeListener = listener;
    }
}
