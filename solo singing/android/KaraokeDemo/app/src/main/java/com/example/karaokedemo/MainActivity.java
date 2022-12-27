package com.example.karaokedemo;


import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import java.util.Random;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.entity.ZegoEngineProfile;


public class MainActivity extends AppCompatActivity {
    ZegoExpressEngine engine;
    // Get from https://console.zegocloud.com
    long appID = Your App ID;
    String appSign = "Your App Sign";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createEngine();
        findViewById(R.id.join_ktv).setOnClickListener(v -> {
            showKtvActivity();
        });
    }

    private void createEngine() {
        ZegoEngineProfile profile = new ZegoEngineProfile();
        profile.appID = appID;
        profile.appSign = appSign;
        profile.scenario = ZegoScenario.KARAOKE;
        profile.application = getApplication();
        engine = ZegoExpressEngine.createEngine(profile, null);
    }

    private void showKtvActivity() {
        Intent intent = new Intent(MainActivity.this, KaraokeActivity.class);
        startActivity(intent);
    }
}