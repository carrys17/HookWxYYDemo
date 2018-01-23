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

/**
 * Created by admin on 2018/1/18.
 */

public class MyProvider extends ContentProvider {


    private static final String TAG = "MyProvider";

    @Override
    public boolean onCreate() {
        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SharedPreferences sp  = getContext().getSharedPreferences("tmp", Context.MODE_WORLD_READABLE);
        int flag = sp.getInt("flag",-1);

        Bundle bundle = new Bundle();
        bundle.putInt("flag",flag);
        Log.i(TAG, "query: 查询 -- i = "+ flag);

        MyCursor cursor = new MyCursor();
        cursor.setExtras(bundle);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        int flag = values.getAsInteger("flag");
        SharedPreferences sp = getContext().getSharedPreferences("tmp",Context.MODE_WORLD_READABLE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt("flag",flag);
        editor.commit();
        Log.i(TAG, "insert: 插入数据 -- i = "+ flag);
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
