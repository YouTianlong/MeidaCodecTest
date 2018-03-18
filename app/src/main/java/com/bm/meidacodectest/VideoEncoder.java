package com.bm.meidacodectest;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by 游天龙 on 2018/3/18.
 */

public class VideoEncoder {

    private static final String mime = "video/avc";
    private static final String TAG = "VideoEncoder";
    private MediaCodec mediaCodec;
    private int width = 320;
    private int height = 240;
    private byte[] rotateYuv420 = null;
    private byte[] yuv420 = null;
    private long presentationTimeUs = 0;
    private byte[] m_info = null;

    public VideoEncoder() {
        int colorFormat = selectColorFormat(selectCodec(mime), mime);
        Log.e(TAG, "colorFormat result:" + colorFormat);

        Log.e(TAG, "width * height:" + width * height);
        Log.e(TAG, "bufferSize:" + getYuvBuffer(width, height));
        rotateYuv420 = new byte[getYuvBuffer(width, height)];
        yuv420 = new byte[getYuvBuffer(width, height)];

        try {
            mediaCodec = MediaCodec.createEncoderByType(mime);

            MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime, height, width);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 125000);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 10);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);

            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
            Log.e(TAG, "video encoder 初始化成功");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getYuvBuffer(int width, int height) {
        int stride = (int) (Math.ceil(width / 16.0) * 16);
        int y_size = stride * height;
        int c_stride = (int) (Math.ceil(width / 32.0) * 16);
        int c_size = c_stride * height / 2;
        return y_size + c_size * 2;
    }

    public void close() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int offerEncoder(byte[] input, byte[] output) {
        int pos = 0;
        NV21ToNV12(input, rotateYuv420, width, height);
        YUV420spRotate90Anticlockwise(rotateYuv420, yuv420, width, height);
        try {
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            Log.e(TAG, "inputBufferIndex:" + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(yuv420);

                long pts = computePresentationTime(presentationTimeUs);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, yuv420.length, pts, 0);
                presentationTimeUs += 1;
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            Log.e(TAG, "outputBufferIndex:" + outputBufferIndex);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size];
                output = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if (m_info == null) {
                    ByteBuffer spsPPsBuffer = ByteBuffer.wrap(outData);
                    if (spsPPsBuffer.getInt() == 0x00000001) {
                        m_info = new byte[output.length];
                        System.arraycopy(outData, 0, m_info, 0, outData.length);
                    } else {
                        return -1;
                    }
                } else {
                    System.arraycopy(outData, 0, output, pos, outData.length);
                    pos += outData.length;
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }

            if (output != null && output[4] == 0x65) {
                System.arraycopy(output, 0, yuv420, 0, pos);
                System.arraycopy(m_info, 0, output, 0, m_info.length);
                System.arraycopy(yuv420, 0, output, m_info.length, pos);
                pos += m_info.length;
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return pos;
    }

    private long computePresentationTime(long presentationTimeUs) {
        return 132 + presentationTimeUs * 1000000 / 10;
    }

    private void YUV420spRotate90Anticlockwise(byte[] src, byte[] dst, int width, int height) {
        int wh = width * height;
        int uvHeight = height >> 1;

        int k = 0;
        for (int i = 0; i < width; i++) {
            int nPos = width - 1;
            for (int j = 0; j < height; j++) {
                dst[k] = src[nPos - i];
                k++;
                nPos += width;
            }
        }

        for (int i = 0; i < width; i += 2) {
            int nPos = wh + width - 1;
            for (int j = 0; j < uvHeight; j++) {
                dst[k] = src[nPos - i - 1];
                dst[k + 1] = src[nPos - i];
                k += 2;
                nPos += width;
            }
        }
    }

    private void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) {
            return;
        }

        int frameSize = width * height;
        int i = 0, j = 0;
        // TODO 这里格式转换没明白
        System.arraycopy(nv21, 0, nv12, 0, frameSize);
        for (i = 0; i < frameSize; i++) {
            nv12[i] = nv21[i];
        }

        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j - 1] = nv21[frameSize + j];
        }

        for (j = 0; j < frameSize / 2; j += 2) {
            nv12[frameSize + j] = nv21[frameSize + j - 1];
        }
    }

    private int selectColorFormat(MediaCodecInfo mediaCodecInfo, String mime) {
        MediaCodecInfo.CodecCapabilities capabilitiesForType = mediaCodecInfo.getCapabilitiesForType(mime);
        for (int i = 0; i < capabilitiesForType.colorFormats.length; i++) {
            int colorFormat = capabilitiesForType.colorFormats[i];
            Log.e(TAG, "colorformat:" + colorFormat);
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e(TAG, "could not found a good color format for" + mediaCodecInfo.getName() + "/ " + mime);
        return 0;
    }

    private boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private MediaCodecInfo selectCodec(String mime) {
        int codecCount = MediaCodecList.getCodecCount();
        for (int i = 0; i < codecCount; i++) {
            MediaCodecInfo mediaCodecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!mediaCodecInfo.isEncoder()) {
                continue;
            }
            String[] supportedTypes = mediaCodecInfo.getSupportedTypes();
            for (int j = 0; j < supportedTypes.length; j++) {
                Log.e(TAG, "selectCodec:" + supportedTypes[j]);
                if (TextUtils.equals(mime, supportedTypes[j])) {
                    return mediaCodecInfo;
                }
            }

            Log.e(TAG, "-------------------------------------------");
        }
        return null;
    }
}
