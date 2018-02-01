package com.example.admin.hookwxyydemo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.io.IOException;

import static com.example.admin.hookwxyydemo.CommandUtils.execCommand;

/**
 * Created by admin on 2018/1/18.
 */

public class MyProvider extends ContentProvider {


    private static final String TAG = "MyProvider";

    private int mMode = -1;

    private String mPath = "";


    @Override
    public boolean onCreate() {
        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
//        SharedPreferences sp  = getContext().getSharedPreferences("tmp", Context.MODE_WORLD_READABLE);
//        int mode = sp.getInt("mode",-1);
//        String path = sp.getString("path","");

        Bundle bundle = new Bundle();
        bundle.putInt("mode",mMode);
        bundle.putString("path",mPath);
        Log.i(TAG, "查询query:  mMode -- "+mMode +", mPath -- "+mPath);


        MyCursor cursor = new MyCursor();
        cursor.setExtras(bundle);
        return cursor;
//        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        int mode = values.getAsInteger("mode");
        String path = values.getAsString("path");
//        SharedPreferences sp = getContext().getSharedPreferences("tmp",Context.MODE_WORLD_READABLE);
//        SharedPreferences.Editor editor = sp.edit();
//        editor.putInt("mode",mode);
//        editor.putString("path",path);
//        editor.commit();
        Log.i(TAG, "insert: 插入数据 -- mode = "+ mode);
        Log.i(TAG, "insert: 插入数据 -- path = "+ path);


        mMode = mode;
        mPath = path;
        Log.i(TAG, "插入insert: mMode -- "+mMode +", mPath -- "+mPath);
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        int mode = values.getAsInteger("mode");
        String path = values.getAsString("path");
//        SharedPreferences sharedPreferences  = getContext().getSharedPreferences("tmp", Context.MODE_WORLD_READABLE);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.putInt("mode",mode);
//        editor.putString("path",path);
//        editor.commit();
//        Log.i(TAG,"contentprovider更新数据 mode = "+mode + ",  path = "+path);

        mMode = mode;
        mPath = path;
        Log.i(TAG, "更新update: mMode -- "+mMode +", mPath -- "+mPath);
        return 0;
    }
}
