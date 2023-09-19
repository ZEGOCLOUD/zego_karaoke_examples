package com.example.karaokedemo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.zegocloud.lrcview.LrcView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.ZegoMediaPlayer;
import im.zego.zegoexpress.callback.IZegoAudioDataHandler;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.callback.IZegoMediaPlayerEventHandler;
import im.zego.zegoexpress.callback.IZegoMediaPlayerLoadResourceCallback;
import im.zego.zegoexpress.constants.ZegoAudioChannel;
import im.zego.zegoexpress.constants.ZegoAudioDataCallbackBitMask;
import im.zego.zegoexpress.constants.ZegoAudioSampleRate;
import im.zego.zegoexpress.constants.ZegoMediaPlayerState;
import im.zego.zegoexpress.constants.ZegoPublisherState;
import im.zego.zegoexpress.constants.ZegoReverbPreset;
import im.zego.zegoexpress.constants.ZegoUpdateType;
import im.zego.zegoexpress.entity.ZegoAudioFrameParam;
import im.zego.zegoexpress.entity.ZegoPlayStreamQuality;
import im.zego.zegoexpress.entity.ZegoPublishStreamQuality;
import im.zego.zegoexpress.entity.ZegoRoomConfig;
import im.zego.zegoexpress.entity.ZegoStream;
import im.zego.zegoexpress.entity.ZegoUser;

enum SingingStatus
{
    Singing,
    PauseSinging,
    StopSinging;
}

public class KaraokeActivity extends AppCompatActivity {
    // ZEGOCLOUD SDK
    private ZegoExpressEngine mSDKEngine = ZegoExpressEngine.getEngine();
    private ZegoMediaPlayer player;

    // UI
    private LrcView lrcView;
    private Button btnAccompaniment;
    private Button btnStartStop;
    private Button btnPauseResume;

    // Local status
    private boolean isSomeoneSinging;
    private SingingStatus singingStatus = SingingStatus.StopSinging;
    private  String mp3FilePath;

    // audio record
    private int sampleRate = 48000;
    private int channelCount = 2;
    private int bitRate = 128000;
    private AacEncoder aacEncoder;

    /***
     * Life cycle
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_karaoke);

        checkDevicePermission();
        configView();

        aacEncoder = new AacEncoder();

        setEventHandler();
        loginRoom();
        createMediaPlayer();

        showLRCFile("trouble-is-a-friend.lrc");
        copyAssetMusicFile("trouble-is-a-friend.mkv");
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /***
     * Interface interaction
     */
    // Set button click listener
    private void configView() {
        lrcView = findViewById(R.id.lrc_view);
        btnStartStop = findViewById(R.id.btn_start_stop);
        btnPauseResume = findViewById(R.id.btn_pause_resume);
        btnAccompaniment = findViewById(R.id.btn_accompaniment);

        btnStartStop.setOnClickListener(v -> {
            if (isSomeoneSinging) {
                Toast.makeText(this, "Someone is Singing!", Toast.LENGTH_LONG).show();
                return;
            }

            if (singingStatus == SingingStatus.StopSinging) {
                startSinging();
            } else if (singingStatus == SingingStatus.Singing){
                stopSinging();
            } else {
                Toast.makeText(this, "Please Resume Singing!", Toast.LENGTH_LONG).show();
            }
        });

        btnPauseResume.setOnClickListener(v -> {
            if (singingStatus == SingingStatus.Singing) {
                pauseSinging();
            } else if (singingStatus == SingingStatus.PauseSinging) {
                resumeSinging();
            } else {
                Toast.makeText(this, "Please Start Singing!", Toast.LENGTH_LONG).show();
            }
        });

        btnAccompaniment.setOnClickListener(v->{
            if (player.getAudioTrackCount() < 2) {
                return;
            }

            if (v.isSelected()) {
                player.setAudioTrackIndex(1);
                btnAccompaniment.setText("Accompaniment");
            } else {
                player.setAudioTrackIndex(0);
                btnAccompaniment.setText("Original Vocals");
            }
            v.setSelected(!v.isSelected());
        });
    }

    public String getFileName(){
        // Use time as filename
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return sdf.format(date) + ".aac";
    }

    // Start singing
    private void startSinging() {
        // Update singing status
        singingStatus = SingingStatus.Singing;
        btnStartStop.setText("Stop Singing");

        // User player load music file.
        loadMusicResource();

        // Generate a streamid
        Random random = new Random();
        int randomInt = random.nextInt(1000);
        String streamID = "stream1" + randomInt;

        // Set Preset Reverb https://docs.zegocloud.com/article/4915
        mSDKEngine.setReverbPreset(ZegoReverbPreset.LARGE_ROOM);
        // open microphone
        mSDKEngine.muteMicrophone(false);
        // start publishing stream
        mSDKEngine.startPublishingStream(streamID);

        // start record
        String filePath = getApplicationContext().getExternalFilesDir(null) + "/audio/";
        fileIsExist(filePath);
        aacEncoder.startRecord(filePath + getFileName(), 1920, sampleRate, bitRate, channelCount);

        // Enable the function of acquiring raw audio data
        startAudioDataObserver();
    }

    // stop singing
    private void stopSinging() {
        // update singing status
        singingStatus = SingingStatus.StopSinging;
        btnStartStop.setText("Start Singing");

        mSDKEngine.stopAudioDataObserver();

        // stop publish stream
        mSDKEngine.stopPublishingStream();
        // mute microphone
        mSDKEngine.muteMicrophone(true);
        // stop media player
        player.stop();
        // reset lyrics progress
        lrcView.updateTime(0);
        aacEncoder.stopRecord();
    }

    // Pause singing
    private void pauseSinging() {
        // update singing status
        singingStatus = SingingStatus.PauseSinging;
        btnPauseResume.setText("Resume Singing");
        mSDKEngine.stopAudioDataObserver();

        // mute microphone
        mSDKEngine.muteMicrophone(true);

        // pause media player
        player.pause();
    }

    // Resume Singing
    private void resumeSinging() {
        // update singing status
        singingStatus = SingingStatus.Singing;
        btnPauseResume.setText("Pause Singing");
        startAudioDataObserver();
        // open microphone
        mSDKEngine.muteMicrophone(false);
        // resume media player
        player.resume();
    }

    private  void startAudioDataObserver() {
        ZegoAudioFrameParam param=new ZegoAudioFrameParam();
        param.channel = ZegoAudioChannel.STEREO;
        param.sampleRate = ZegoAudioSampleRate.ZEGO_AUDIO_SAMPLE_RATE_48K;
        int bitmask = 0;
        bitmask |= ZegoAudioDataCallbackBitMask.CAPTURED.value();
        mSDKEngine.startAudioDataObserver(bitmask, param);
    }

    /***
     * ZEGOCLOUD SDK
     */
    // The audience needs to monitor the lead singer event
    void setEventHandler() {
        mSDKEngine.setEventHandler(new IZegoEventHandler() {
            @Override
            public void onRoomStreamUpdate(String roomID, ZegoUpdateType updateType, ArrayList<ZegoStream> streamList, JSONObject extendedData) {
                super.onRoomStreamUpdate(roomID, updateType, streamList, extendedData);

                ZegoStream stream = streamList.get(0);
                String playStreamID = stream.streamID;
                if (updateType == ZegoUpdateType.ADD) {

                    // When a new stream is added, it means that the lead singer has started to sing, and the audio stream needs to be pulled and played
                    // Set the tream buffer to avoid the sound not being smooth due to network problems
                    mSDKEngine.setPlayStreamBufferIntervalRange(playStreamID, 500, 4000);
                    // Pull the audio stream pushed by the host and play it
                    mSDKEngine.startPlayingStream(playStreamID);
                } else {
                    // Stop pulling the audio stream pushed by the host
                    mSDKEngine.stopPlayingStream(playStreamID);
                    // Reset lyrics progress
                    lrcView.updateTime(0);
                }
                // Update the singing status, used for demo demonstration, can be deleted when the business is realized
                updateSingingStatus(updateType == ZegoUpdateType.ADD);
            }

            // Update the singing status, used for demo demonstration, can be deleted when the business is realized
            @Override
            public void onPlayerRecvSEI(String streamID, byte[] data) {
                String dataString = new String(data);
                try {
                    JSONObject jsonObject = new JSONObject(dataString);
                    String KEY_PROGRESS_IN_MS = "KEY_PROGRESS_IN_MS";
                    long progress = jsonObject.getLong(KEY_PROGRESS_IN_MS);
                    // Update lyrics progress
                    lrcView.updateTime(progress);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        mSDKEngine.setAudioDataHandler(new IZegoAudioDataHandler() {
            @Override
            public void onCapturedAudioData(ByteBuffer data, int dataLength, ZegoAudioFrameParam param) {
                System.out.println("onCapturedAudioData: " + dataLength);

                int audioSize = data.remaining();
                byte[] audioData = new byte[audioSize];
                data.get(audioData);
                aacEncoder.dstAudioFormatFromPCM(audioData);
            }
        });
    }



    // Login karaoke room
    private void loginRoom() {
        // Generate a room id
        Random random = new Random();
        String userID = "userid_" + random.nextInt(100);
        String userName = userID + "_Name";
        String roomID = "test_room_id";

        // Create a user model
        ZegoUser user = new ZegoUser(userID, userName);
        ZegoRoomConfig roomConfig = new ZegoRoomConfig();
        roomConfig.isUserStatusNotify = true;

        // login karaoke room
        mSDKEngine.loginRoom(roomID, user, roomConfig, (int error, JSONObject extendedData)->{
            if (error != 0) {
                Toast.makeText(this, "login ktv fail", Toast.LENGTH_LONG).show();
            }
        });
    }
    // create a media player
    private  void createMediaPlayer() {
        ZegoMediaPlayer mediaPlayer = mSDKEngine.createMediaPlayer();
        if (mediaPlayer == null) {
            Toast.makeText(this, "createMediaPlayer failed.", Toast.LENGTH_LONG).show();
        }

        player = mediaPlayer;
        // Set to mix media audio stream and microphone audio stream into one stream
        player.enableAux(true);

        // set media player event handler
        player.setEventHandler(new IZegoMediaPlayerEventHandler() {
            @Override
            public void onMediaPlayerPlayingProgress(ZegoMediaPlayer mediaPlayer, long millisecond) {
                super.onMediaPlayerPlayingProgress(mediaPlayer, millisecond);
                // update Lyric progress
                lrcView.updateTime(millisecond);
                // send lyrics progress infomation to remote
                sendProgressToRemote(millisecond);
            }

            public void onMediaPlayerStateUpdate(ZegoMediaPlayer mediaPlayer, ZegoMediaPlayerState state, int errorCode) {
                if (state == ZegoMediaPlayerState.PLAY_ENDED) {
                    stopSinging();
                }
            }
        });
    }
    // user media player load music file
    private void loadMusicResource() {
        player.loadResource(mp3FilePath, new IZegoMediaPlayerLoadResourceCallback() {
            @Override
            public void onLoadResourceCallback(int errorcode) {
                // This event callback is called on the UI thread. Developers can make corresponding UI changes here.
                if (errorcode == 0) {
                    player.start();
                } else {
                    Toast.makeText(KaraokeActivity.this, "Load music file failed.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }
    // send lyrics progress infomation to remote
    private void sendProgressToRemote(long millisecond) {
        try {
            JSONObject localMusicProcessStatusJsonObject = new JSONObject();
            String KEY_PROGRESS_IN_MS = "KEY_PROGRESS_IN_MS";
            localMusicProcessStatusJsonObject.put(KEY_PROGRESS_IN_MS, millisecond);
            mSDKEngine.sendSEI(localMusicProcessStatusJsonObject.toString().getBytes());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /***
     * Lyrics display
     */
    // use lrcview show lyrics file
    private void showLRCFile(String lrcFileName) {
        // Load LRC File
        String mainLrcText = readLrcText(lrcFileName);
        lrcView.loadLrc(mainLrcText);
    }
    // Read local lyrics file
    private String readLrcText(String fileName) {
        String lrcText = null;
        try {
            InputStream is = getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            lrcText = new String(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lrcText;
    }

    /***
     * Other methods
     */
    // Check Microphone and camera permission
    private void checkDevicePermission(){
        String[] permissionNeeded = {
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.WRITE_EXTERNAL_STORAGE"};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.CAMERA") != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(permissionNeeded, 101);
            }
        }
    }
    // When someone is singing, update UI
    private void updateSingingStatus(boolean someoneSinging) {
        isSomeoneSinging = someoneSinging;
        int isVisible = someoneSinging ? View.INVISIBLE : View.VISIBLE;
        btnAccompaniment.setVisibility(isVisible);
        btnStartStop.setVisibility(isVisible);
        btnPauseResume.setVisibility(isVisible);
    }
    // Copy music files from asset, Used for Demo demonstration. It needs to be downloaded from the server when implementing the business
    private void copyAssetMusicFile(String fileName) {
        String dirPath = ContextCompat.getExternalFilesDirs(KaraokeActivity.this,
                Environment.DIRECTORY_DCIM)[0].getAbsolutePath() + File.separator + "cache";
        try {
            copyAssetToFile(fileName, dirPath, "/" + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mp3FilePath = dirPath + "/" + fileName;
    }
    // Copy files from asset，Used for Demo demonstration. It needs to be downloaded from the server when implementing the business
    public void copyAssetToFile(String assetName, String savepath, String savename) throws IOException {
        InputStream myInput;
        File dir = new File(savepath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dbf = new File(savepath + savename);
        if (dbf.exists()) {
            dbf.delete();
        }
        String outFileName = savepath + savename;
        OutputStream myOutput = new FileOutputStream(outFileName);
        myInput = this.getAssets().open(assetName);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = myInput.read(buffer)) > 0) {
            myOutput.write(buffer, 0, length);
        }
        myOutput.flush();
        myInput.close();
        myOutput.close();
    }

    boolean fileIsExist(String fileName)
    {
        // Check whether the path exist.
        File file=new File(fileName);
        if (file.exists())
            return true;
        else{
            return file.mkdirs();
        }
    }
}