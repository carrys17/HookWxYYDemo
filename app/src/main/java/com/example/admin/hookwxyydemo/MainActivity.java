package com.example.admin.hookwxyydemo;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.io.IOException;

import static com.example.admin.hookwxyydemo.CommandUtils.execCommand;

public class MainActivity extends AppCompatActivity {

    int mFlag = -1;

    ContentResolver mResolver;
    Uri uri = Uri.parse("content://com.example.admin.hookwxyydemo.provider");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            e.printStackTrace();
        }

        mResolver = getApplicationContext().getContentResolver();

        refresh();

        findViewById(R.id.id_record).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                execCommand("chmod 777 /data/local/tmp", true);
                mFlag = 1;
                refresh();

            }
        });


        findViewById(R.id.id_replace).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFlag = 0;
                refresh();
            }
        });



    }

    private void refresh() {
        ContentValues values = new ContentValues();
        values.put("flag",mFlag);
        mResolver.insert(uri,values);
    }
}
