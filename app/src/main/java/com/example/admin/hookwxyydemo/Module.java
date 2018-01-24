package com.example.admin.hookwxyydemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaSyncEvent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static com.example.admin.hookwxyydemo.CommandUtils.execCommand;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.newInstance;

/**
 * Created by admin on 2018/1/11.
 */

public class Module implements IXposedHookLoadPackage {

    private static final String TAG = "Module";

    // 每个AudioRecord对应的文件输出流(FileOutputStream 指向自己录制语音的临时文件)
    private HashMap<AudioRecord, FileOutputStream> mFosMap = new HashMap<>();


    // 当前临时文件名的num值，例如 myPcmFile1 对应就是语音输入（自己）的第一个pcm文件
    int mNum = 0;

    // 每个AudioRecord对应的临时文件名
    private HashMap<AudioRecord, String> mPcmFileMap = new HashMap<>();


    // 指定模式  1为录音  0为发送指定语音
    private  int mMode = -1;


    // 每个AudioRecord对应的state状态，用于维护当前录音状态
    private HashMap<AudioRecord,Integer> mRecordingFlagMap = new HashMap<>();


    // 将语音录到指定的pcm文件
    private String mRecordPcmFileName;

    // 发送指定的pcm文件
    private String mSendPcmFileName;


    // 每个AudioRecord对应的文件输入流(FileInputStream指向要替换的pcm文件)
    private HashMap<AudioRecord,FileInputStream> mFisMap = new HashMap<>();



    Uri uri = Uri.parse("content://com.example.admin.hookwxyydemo.provider");

    ContentResolver mResolver;

    Context applicationContext;

    

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        try {

            if (loadPackageParam.packageName.equals("com.tencent.mm") ) {
                Log.i(TAG, "handleLoadPackage: 进来了com.tencent.mm方法");

                Runtime.getRuntime().exec("su");

                // 获取到当前进程的上下文
                try{
                    Class<?> ContextClass = findClass("android.content.ContextWrapper",loadPackageParam.classLoader);
                    findAndHookMethod(ContextClass, "getApplicationContext", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            applicationContext = (Context) param.getResult();
                            XposedBridge.log("得到上下文");

                            if (applicationContext!=null){
                                Log.i(TAG, "applicationContext 不为null ");
                                mResolver = applicationContext.getContentResolver();
                            }
                        }
                    });
                }catch (Throwable throwable){
                    XposedBridge.log("获取上下文失败 "+throwable);
                }



                // hook  startRecording 方法，当发送指定语音时需要hook这个函数,将微信的流程打断，自己维护整个发送过程
                XposedHelpers.findAndHookMethod("android.media.AudioRecord",
                        loadPackageParam.classLoader, "startRecording",new StartRecordingMethodHook());


                // hook  getRecordingState 方法，当发送指定语音时需要hook这个函数
                XposedHelpers.findAndHookMethod("android.media.AudioRecord",
                        loadPackageParam.classLoader, "getRecordingState",new GetRecordingStateMethodHook());


                // hook  read 方法，当发送指定语音时需要hook这个函数需要在before操作，当录入自己的语音文件时需要在after操作
                XposedHelpers.findAndHookMethod("android.media.AudioRecord",
                        loadPackageParam.classLoader, "read", byte[].class, int.class,
                        int.class, new ReadMethodHook());


                // hook  stop 方法，当发送指定语音时需要hook这个函数需要在before操作，当录入自己的语音文件时需要在after操作
                XposedHelpers.findAndHookMethod("android.media.AudioRecord",
                        loadPackageParam.classLoader, "stop",new StopMethodHook() );


                // hook  release 方法，当发送指定语音时需要hook这个函数，打断微信
                XposedHelpers.findAndHookMethod("android.media.AudioRecord", loadPackageParam.classLoader,
                        "release", new ReleaseMethodHook());


            }

        } catch (Exception e) {
            Log.i(TAG, "handleLoadPackage Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 获取当前的模式以及指定文件目录
    private void getCurrentModeAndPath() {
        int mode = -1;
        String path = "";
        if (mResolver!=null){
            Log.i(TAG, "mResolver 不为null ");
            Cursor cursor =  mResolver.query(uri,null,null,null,null);
            Bundle bundle = cursor.getExtras();
            mode = bundle.getInt("mode");
            path =bundle.getString("path");
        }
        Log.i(TAG, "query 得到的: mode = "+mode);
        Log.i(TAG, "query 得到的: path = "+path);
        if (mode ==1){
            Log.i(TAG, "mode ==1 ");
            mRecordPcmFileName = path;
            mMode = 1;
            Log.i(TAG, "mMode = : "+mMode);
        }
        if (mode ==0){
            Log.i(TAG, "mode ==0 ");
            mSendPcmFileName = path;
            mMode = 0;
            Log.i(TAG, "mMode = : "+mMode);
        }
        Log.i(TAG, "mMode ---- " + mMode);

    }


    // 当发送指定语音时需要hook这个函数需要在before操作，当录入自己的语音文件时需要在after操作
    private class ReadMethodHook extends XC_MethodHook{
        // 发送指定语音
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            try {
                getCurrentModeAndPath();

                if (mMode == 0) {
                    AudioRecord record = (AudioRecord) param.thisObject;
                    byte[] buffer = (byte[]) param.args[0];
                    int off = (int) param.args[1];
                    int size = (int) param.args[2];

                    FileInputStream fis;

                    // 指定发送的语音文件
                    if (mFisMap.get(record)==null) {
                        fis = new FileInputStream(mSendPcmFileName);
                        mFisMap.put(record,fis);
                    }else {
                        fis = mFisMap.get(record);
                    }

                    // 创建byte[]数据，用来替换微信的buffer
                    int min = Math.min(buffer.length - off, size);
                    byte[] bytes = new byte[min];
                    // 将指定的语音文件读取到微信的语音文件，实现替换发送指定语音
                    int res = fis.read(bytes);

                    if (res == -1) {
                        param.setResult(0);
                    } else {
                        for (int i = 0; i < bytes.length; i++) {
                            buffer[off + i] = bytes[i];
                        }
                        param.setResult(res);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 录入自己的语音文件时
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            try {
                getCurrentModeAndPath();
                if (mMode == 1) {

//                    File ff = new File(mRecordPcmFileName);
//                    if (ff.exists()){
//                        ff.delete();
//                    }
//                    // 问题就出在这，在这里修改父目录的权限就会报错,将这个操作放在stop方法也就是copy到指定的目录前再操作
//                    String  fp = ff.getParent();
//                    Log.i(TAG, "父目录 : "+fp);
//                    execCommand("chmod 777 "+fp,true);


                    FileOutputStream fileOutputStream ;
                    // 拿到当前的AudioRecord对象
                    AudioRecord record = (AudioRecord) param.thisObject;

                    byte[] buffer = (byte[]) param.args[0];
                    Integer integer = (Integer) param.args[1];
                    int offset = integer.intValue();
                    Integer integer2 = (Integer) param.args[2];
                    int size = integer2.intValue();

                    // 创建输出的临时文件流
                    if (mFosMap.get(record) == null) {
                        execCommand("chmod 777 /data/local/tmp",true);
                        String pcmFileName = "myPcmFile";
                        mNum++;
                        pcmFileName = pcmFileName + mNum;
                        File file = new File("/data/local/tmp/" + pcmFileName + ".pcm");
                        File fileParent = file.getParentFile();
                        if (!fileParent.exists()) {
                            fileParent.mkdirs();
                        }
                        file.createNewFile();
                        Log.i(TAG, "pcmFileName: " + pcmFileName);
                        fileOutputStream = new FileOutputStream("/data/local/tmp/" + pcmFileName + ".pcm");
                        mFosMap.put(record, fileOutputStream);
                        mPcmFileMap.put(record, pcmFileName);
                    } else {
                        fileOutputStream = mFosMap.get(record);
                    }

                    // 获取当前AudioRecord的read方法的返回值
                    int read = (int) param.getResult();

                    // read方法还在不断的执行中
                    if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                        // 新建一个byte[] ，用于拿到微信的buffer数据
                        byte[] bytes = new byte[read];
                        // 将微信的buffer数据赋予到自己的byte[]中
                        for (int i = 0; i < bytes.length; i++) {
                            bytes[i] = buffer[i + offset];
                        }

                        // 将byte[] 写到临时的语音文件中,这样既可拿到当前语音输入的内容
                        fileOutputStream.write(bytes);
                    }


                }
            } catch (Exception e) {
                Log.i(TAG, "afterHookedMethod Exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private class StopMethodHook extends XC_MethodHook{

        // 发送指定语音
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            try {
                getCurrentModeAndPath();
                if (mMode == 0) {
                    AudioRecord record = (AudioRecord) param.thisObject;
                    // 关闭自己的文件输入，清理map
                    if (mFisMap.get(record) != null) {
                        FileInputStream fis = mFisMap.get(record);
                        fis.close();
                        mFisMap.remove(record);
                    }

                    // 将录音状态设置为stopped
                    int flag = -1;
                    if (mRecordingFlagMap.get(record) == null || mRecordingFlagMap.get(record) != AudioRecord.RECORDSTATE_STOPPED) {
                        flag = AudioRecord.RECORDSTATE_STOPPED;
                        mRecordingFlagMap.put(record, flag);
                    }
                    //  打断微信，完成发送指定的语音文件
                    Object o = new Object();
                    param.setResult(o);
                }
            } catch (Exception e) {
                Log.i(TAG, "AudioRecord  # stop 里的 beforeHookedMethod出错 : " + e.getMessage());
            }
        }

        // 录入自己的语音文件
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            try {
                getCurrentModeAndPath();
                if (mMode == 1) {
                    AudioRecord record = (AudioRecord) param.thisObject;
                    if (mFosMap.get(record) != null) {
                        // 关闭文件输出流，清理map
                        FileOutputStream fos = mFosMap.get(record);
                        fos.close();
                        mFosMap.remove(record);

                        // 修改指定输出文件的父目录权限。
                        File file = new File(mRecordPcmFileName);
                        String parentPath = file.getParent();
                        execCommand("chmod 777 "+parentPath,true);

                        // 覆盖拷贝到指定的文件目录
                        String pcmFileName = mPcmFileMap.get(record);
                        execCommand("chmod 777 /data/local/tmp/" + pcmFileName + ".pcm", true);
                        execCommand("\\cp /data/local/tmp/" + pcmFileName + ".pcm  " + mRecordPcmFileName, true);
                        Log.i(TAG, "命令 : \\cp /data/local/tmp/" + pcmFileName + ".pcm  " + mRecordPcmFileName);
                        execCommand("chmod 777 " + mRecordPcmFileName, true);
                        // 删除临时文件
                        execCommand("rm /data/local/tmp/" + pcmFileName + ".pcm", true);

                        // 清理map
                        mPcmFileMap.remove(record);
                    }
                }
            } catch (Exception e) {
                Log.i(TAG, "AudioRecord  # stop 里的 afterHookedMethod出错 : " + e.getMessage());
            }
        }
    }


    // StartRecordingMethodHook类，当发送指定语音时需要hook这个函数
    private class StartRecordingMethodHook extends XC_MethodHook{

        // 将recordingState置为RECORDSTATE_RECORDING，打断微信的发送过程，为了发送自己指定文件。
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            try {
                getCurrentModeAndPath();
                if (mMode == 0) {
                    // 修改要发送的语音文件的权限
                    File file = new File(mSendPcmFileName);
                    String parentName = file.getParent();
                    Log.i(TAG, "parentName: " + parentName);

                    //  创建并修改要发送的语音文件的父目录
                    File fileParent = file.getParentFile();
                    if (!fileParent.exists()) {
                        fileParent.mkdirs();
                    }
                    execCommand("chmod 777 " + parentName, true);
                    file.createNewFile();

                    execCommand("chmod 777 " + mSendPcmFileName, true);

                    AudioRecord record = (AudioRecord) param.thisObject;
                    int flag = -1;

                    // 将录音状态置为RECORDSTATE_RECORDING状态
                    if (mRecordingFlagMap.get(record) == null || mRecordingFlagMap.get(record) != AudioRecord.RECORDSTATE_RECORDING) {
                        flag = AudioRecord.RECORDSTATE_RECORDING;
                        mRecordingFlagMap.put(record, flag);
                    }

                    // 打断微信的录音过程
                    Object o = new Object();
                    param.setResult(o);

                }
            } catch (Exception e) {
                Log.i(TAG, "AudioRecord #  startRecording  beforeHookedMethod  出错");
                Log.i(TAG, "出错原因 —— " + e.getMessage());
            }
        }
    }


    // getRecordingState，获取我们在startRecording和Stop中维护的state的值
    private class GetRecordingStateMethodHook extends  XC_MethodHook{
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            try {
                getCurrentModeAndPath();
                if (mMode == 0) {

                    // 获取我们在startRecording和Stop中维护的state的值
                    AudioRecord record = (AudioRecord) param.thisObject;
                    int res = mRecordingFlagMap.get(record) == null ? AudioRecord.RECORDSTATE_STOPPED : mRecordingFlagMap.get(record);
                    // 将返回值给微信
                    param.setResult(res);
                    // 清理mRecordFlagMap
                    mRecordingFlagMap.remove(record);

                }
            } catch (Exception e) {
                Log.i(TAG, "AudioRecord # getRecordingState beforeHookedMethod 出错: " + e.getMessage());
            }
        }
    }

    // release方法，打断微信的
    private class ReleaseMethodHook extends XC_MethodHook{
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            try {
                getCurrentModeAndPath();
                if (mMode == 0) {
                    Log.i(TAG, "AudioRecord # release beforeHookedMethod ");
                    Object o = new Object();
                    param.setResult(o);
                }
            }catch (Exception e){
                Log.i(TAG, "AudioRecord # release beforeHookedMethod 出错: " + e.getMessage());
            }

        }
    }
}
