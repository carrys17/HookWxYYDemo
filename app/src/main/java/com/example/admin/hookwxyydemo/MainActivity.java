package com.example.admin.hookwxyydemo;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

import static android.widget.Toast.LENGTH_SHORT;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    ContentResolver mResolver;
    Uri uri = Uri.parse("content://com.example.admin.hookwxyydemo.provider");

    String mFilePath = null;

    RadioGroup mGroup;

    Button mRecordBtn;

    Button mSendBtn;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mGroup = findViewById(R.id.radioGroup);
        mGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radioButton1){
                    mFilePath = "/data/local/tmp/1.pcm";
                }
                if (checkedId == R.id.radioButton2){
                    mFilePath = "/data/local/tmp/2.pcm";
                }
                if (checkedId == R.id.radioButton3){
                    mFilePath = "/data/local/tmp/3.pcm";
                }
                if (checkedId == R.id.radioButton4){
                    mFilePath = "/data/local/tmp/4.pcm";
                }
                if (checkedId == R.id.radioButton5){
                    mFilePath = "/data/local/tmp/5.pcm";
                }
            }
        });


        mRecordBtn = findViewById(R.id.record);
        mRecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFilePath==null || mFilePath.equals("")){
                    Toast.makeText(MainActivity.this,"请选择要录制的语音",Toast.LENGTH_SHORT).show();
                }else {
                    Log.i(TAG, "mRecordBtn  onClick: mFilePath "+mFilePath);
                    recordAudio(mFilePath);
                    Toast.makeText(MainActivity.this,"请前往微信录制",Toast.LENGTH_SHORT).show();
                }
            }
        });

        mSendBtn = findViewById(R.id.send);
        mSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFilePath==null || mFilePath.equals("")){
                    Toast.makeText(MainActivity.this,"请选择要发送的语音",Toast.LENGTH_SHORT).show();
                }else {
                    Log.i(TAG, "mSendBtn  onClick: mFilePath "+mFilePath);
                    pushAudio(mFilePath);
                    Toast.makeText(MainActivity.this,"请前往微信发送",Toast.LENGTH_SHORT).show();
                }
            }
        });



//        findViewById(R.id.id_record).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                recordAudio("/data/local/tmp/zzz.pcm");
//            }
//        });
//
//
//        findViewById(R.id.id_replace).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                pushAudio("/data/local/tmp/1.pcm");
//            }
//        });

    }




    /**
     *  将微信语音录到指定目录下
     * @param pcmFile  输出到指定的文件目录 例如：/data/local/tmp/zzz.pcm
     * @return
     */
    private void recordAudio(String pcmFile){
        mResolver = getApplicationContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put("mode",1);
        values.put("path",pcmFile);
        // 调用ContentProvider更新数据
        mResolver.update(uri,values,null,null);
    }


    /**
     * 发送指定语音
     * @param pcmFile  指定语音的文件名 例如: /data/local/tmp/1.pcm
     */
    private void pushAudio(String pcmFile){
        mResolver = getApplicationContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put("mode",0);
        values.put("path",pcmFile);
        // 调用ContentProvider更新数据
        mResolver.update(uri,values,null,null);
    }



}
