package com.example.karaokedemo;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class AacEncoder {

    private int mDataLength;
    private int mSampleRate;
    private int mChannelCount;
    private int mBitRate;
    private int mFreqIdx;
    private String mSavePath;

    private MediaCodec.BufferInfo mEncodeBufferInfo;
    private ByteBuffer[] mEncodeInputBuffers;
    private ByteBuffer[] mEncodeOutputBuffers;
    private byte[] mBuffer;
    private MediaCodec mMediaEncode;

    private OutputStream mOutputStream;


    private final static String TAG = AacEncoder.class.getSimpleName();

    public void startRecord(String filePath, int dataLength, int sampleRate, int bitRate, int channelCount) {
        this.mSavePath = filePath;
        this.mDataLength = dataLength;
        this.mSampleRate = sampleRate;
        this.mChannelCount = channelCount;
        this.mBitRate = bitRate;
        this.mFreqIdx = getFreqIdxBySampleRate(sampleRate);

        if (mFreqIdx == -1) {
            Log.e(TAG, "sampleRate error");
            return;
        }

        configAACMediaEncode();
    }

    public void stopRecord() {
        if (mMediaEncode != null) {
            mMediaEncode.stop();
        }
    }

    private void configAACMediaEncode() {
        try {
            MediaFormat encodeFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, mSampleRate, 1);
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            encodeFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, mChannelCount);
            encodeFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mDataLength);
            mMediaEncode = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mMediaEncode.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mMediaEncode == null) {
            Log.e(TAG, "create mMediaEncode failed");
            return;
        }
        mMediaEncode.start();
        mEncodeInputBuffers = mMediaEncode.getInputBuffers();
        mEncodeOutputBuffers = mMediaEncode.getOutputBuffers();
        mEncodeBufferInfo = new MediaCodec.BufferInfo();
    }


    /**
     * PCM data encode to AAC
     */
    public void dstAudioFormatFromPCM(byte[] pcmData) {
        if (mMediaEncode == null) {
            return;
        }

        int inputIndex;
        ByteBuffer inputBuffer;
        int outputIndex;
        ByteBuffer outputBuffer;

        int outBitSize;
        int outPacketSize;

        try {

            if (mOutputStream == null) {
                mOutputStream = new FileOutputStream(new File(mSavePath));
            }

            inputIndex = mMediaEncode.dequeueInputBuffer(0);
            if (inputIndex == -1) {
                return;
            }
            inputBuffer = mEncodeInputBuffers[inputIndex];
            inputBuffer.clear();
            inputBuffer.limit(pcmData.length);
            inputBuffer.put(pcmData);
            mMediaEncode.queueInputBuffer(inputIndex, 0, pcmData.length, 0, 0);

            outputIndex = mMediaEncode.dequeueOutputBuffer(mEncodeBufferInfo, 0);
            while (outputIndex >= 0) {
                outBitSize = mEncodeBufferInfo.size;
                outPacketSize = outBitSize + 7;// 7 is the size of the ADTS header
                outputBuffer = mEncodeOutputBuffers[outputIndex];
                outputBuffer.position(mEncodeBufferInfo.offset);
                outputBuffer.limit(mEncodeBufferInfo.offset + outBitSize);
                mBuffer = new byte[outPacketSize];

                addADTStoPacket(mBuffer, outPacketSize); // add ADTS header data


                outputBuffer.get(mBuffer, 7, outBitSize);//Take the encoded AAC data and put it into byte[].

                mOutputStream.write(mBuffer, 0, mBuffer.length);
                mOutputStream.flush();

                outputBuffer.position(mEncodeBufferInfo.offset);
                mMediaEncode.releaseOutputBuffer(outputIndex, false);
                outputIndex = mMediaEncode.dequeueOutputBuffer(mEncodeBufferInfo, 0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = mFreqIdx;
        int chanCfg = mChannelCount;

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF1;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    private int getFreqIdxBySampleRate(int sampleRate) {
        switch (sampleRate) {
            case 96000:
                return 0;
            case 88200:
                return 1;
            case 64000:
                return 2;
            case 48000:
                return 3;
            case 44100:
                return 4;
            case 32000:
                return 5;
            case 24000:
                return 6;
            case 22050:
                return 7;
            case 16000:
                return 8;
            case 12000:
                return 9;
            case 11025:
                return 10;
            case 8000:
                return 11;
            case 7350:
                return 12;
            default:
                return -1;
        }

    }
}
