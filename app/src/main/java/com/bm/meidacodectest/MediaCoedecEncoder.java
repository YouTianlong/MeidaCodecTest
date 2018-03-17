package com.bm.meidacodectest;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by youtl on 2018/3/16.
 */

public class MediaCoedecEncoder {

    private static final String TAG = MediaCoedecEncoder.class.getSimpleName() + "@:";
    private String AUDIO_MIME = "audio/mp4a-latm";
    private static final int DEFAULT_MAX_BUFFER_SIZE = 16384;
    private MediaCodec mediaCodec;
    private boolean mIsOpened = false;
    private OnAudioEncodedListener mAudioEncodedListener;

    public boolean open() {
        if (mIsOpened) {
            return true;
        }
        try {
            mediaCodec = MediaCodec.createEncoderByType(AUDIO_MIME);
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, AUDIO_MIME);
            // TODO  CHANNEL_OUT_STEREO ? CHANNEL_IN_STEREO
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128 * 1000);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, DEFAULT_MAX_BUFFER_SIZE);
            // TODO surface是干嘛的
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            mediaCodec.start();
            mIsOpened = true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        Log.e(TAG, "configure success");
        return true;
    }

    public void close() {
        Log.e(TAG, "close audio encoder + ");
        if (!mIsOpened) {
            return;
        }

        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
        mIsOpened = false;
        Log.e(TAG, "close audio encoder -");
    }


    public boolean isOpened() {
        return mIsOpened;
    }

    public synchronized boolean encodePCMToAAC(byte[] bytes, long presentationTimeUs) {
        Log.e(TAG, "encode length: " + bytes.length);
        if (!mIsOpened) {
            return false;
        }

        try {
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(1000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(bytes);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, bytes.length, presentationTimeUs, 0);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return false;
        }
        Log.e(TAG, "encode -");
        return false;
    }

    public synchronized boolean retrieve() {
        Log.e(TAG, "encode retrieve +");
        if (!mIsOpened) {
            return false;
        }
        try {
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 1000);
            if (outputBufferIndex >= 0) {
                Log.e(TAG, "encode retrieve frame  " + bufferInfo.size);
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                outputBuffer.position(bufferInfo.offset);
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                byte[] frame = new byte[bufferInfo.size];
                outputBuffer.get(frame, 0, bufferInfo.size);
                if (mAudioEncodedListener != null) {
                    mAudioEncodedListener.onFrameEncoded(frame, bufferInfo.presentationTimeUs);
                }
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }

        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return false;
        }
        Log.e(TAG, "encode retrieve -");
        return true;
    }

    public void setAudioEncodedListener(OnAudioEncodedListener listener) {
        this.mAudioEncodedListener = listener;
    }

    public interface OnAudioEncodedListener {
        void onFrameEncoded(byte[] encoded, long presentationTimeUs);
    }
}
