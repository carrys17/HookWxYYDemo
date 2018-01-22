package com.example.admin.hookwxyydemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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
    private static volatile int sMode = -1;


    // 每个AudioRecord对应的state状态，用于维护当前录音状态
    private HashMap<AudioRecord,Integer> mRecordingFlagMap = new HashMap<>();


    // 将语音录到指定的pcm文件
    private static String sRecordPcmFileName;

    // 发送指定的pcm文件
    private static String sSendPcmFileName;

    // 是否已经初始化完成（hook一次之后，以后就会不断的调用了）
    private boolean m_isInit = false;

    // 每个AudioRecord对应的文件输入流(FileInputStream指向要替换的pcm文件)
    private HashMap<AudioRecord,FileInputStream> mFisMap = new HashMap<>();


    /**
     *  使得每次微信发语音 都发出指定的pcm语音
     * @param pcmFile  要发送的pcm文件目录 例如：/data/local/tmp/1.pcm
     * @return
     */
    public static int pushAudio(String pcmFile){
        sSendPcmFileName = pcmFile;
        sMode = 0;
        return 0;
    }



    /**
     *  将微信语音录到指定目录下
     * @param pcmFile  输出到指定的文件目录 例如：/data/local/tmp/hhh.pcm
     * @return
     */
    public static int recordAudio(String pcmFile){
        sRecordPcmFileName = pcmFile;
        sMode = 1;
        return 0;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {

        try {


            if (loadPackageParam.packageName.equals("com.tencent.mm") && m_isInit==false) {
                Log.i(TAG, "handleLoadPackage: 进来了com.tencent.mm方法");

                Runtime.getRuntime().exec("su");

                
//                recordAudio("/data/local/tmp/zzz.pcm");
//                    pushAudio("/data/local/tmp/1.pcm");


                if (sMode == 1){
                    recordAudio(sRecordPcmFileName);
                }else if(sMode == 0){
                    pushAudio(sSendPcmFileName);
                }

                Log.i(TAG, "handleLoadPackage: sMode ---- " + sMode);



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
                XposedHelpers.findAndHookMethod("android.media.AudioRecord", loadPackageParam.classLoader, "release",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (sMode == 0) {
                                    Log.i(TAG, "AudioRecord # release beforeHookedMethod ");
                                    Object o = new Object();
                                    param.setResult(o);
                                }
                            }
                        });

                m_isInit = true;

            }

        } catch (Exception e) {
            Log.i(TAG, "handleLoadPackage Exception: " + e.getMessage());
            e.printStackTrace();
        }


    }


    // 当发送指定语音时需要hook这个函数需要在before操作，当录入自己的语音文件时需要在after操作
    private class ReadMethodHook extends XC_MethodHook{
        // 发送指定语音
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            try {
                if (sMode == 0) {
                    AudioRecord record = (AudioRecord) param.thisObject;
                    byte[] buffer = (byte[]) param.args[0];
                    int off = (int) param.args[1];
                    int size = (int) param.args[2];

                    FileInputStream fis;

                    // 指定发送的语音文件
                    if (mFisMap.get(record)==null) {
                        fis = new FileInputStream(sSendPcmFileName);
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
                if (sMode == 1) {
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
                if (sMode == 0) {
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
                if (sMode == 1) {
                    AudioRecord record = (AudioRecord) param.thisObject;
                    if (mFosMap.get(record) != null) {
                        // 关闭文件输出流，清理map
                        FileOutputStream fos = mFosMap.get(record);
                        fos.close();
                        mFosMap.remove(record);


                        // 修改指定输出文件的父目录权限。
                        File file = new File(sRecordPcmFileName);
                        String parentPath = file.getParent();
                        execCommand("chmod 777 "+parentPath,true);

                        // 覆盖拷贝到指定的文件目录
                        String pcmFileName = mPcmFileMap.get(record);
                        execCommand("chmod 777 /data/local/tmp/" + pcmFileName + ".pcm", true);
                        execCommand("\\cp /data/local/tmp/" + pcmFileName + ".pcm  " + sRecordPcmFileName, true);
                        Log.i(TAG, "命令 : \\cp /data/local/tmp/" + pcmFileName + ".pcm  " + sRecordPcmFileName);
			execCommand("rm /data/local/tmp/" + pcmFileName + ".pcm",true);
                        execCommand("chmod 777 " + sRecordPcmFileName, true);

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
            if (sMode == 0) {
                try {
                    // 修改要发送的语音文件的权限
                    File file = new File(sSendPcmFileName);
                    String parentName = file.getParent();
                    Log.i(TAG, "parentName: " + parentName);

                    //  创建并修改要发送的语音文件的父目录
                    File fileParent = file.getParentFile();
                    if (!fileParent.exists()) {
                        fileParent.mkdirs();
                    }
                    execCommand("chmod 777 " + parentName, true);
                    file.createNewFile();

                    execCommand("chmod 777 " + sSendPcmFileName, true);

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


                } catch (Exception e) {
                    Log.i(TAG, "AudioRecord #  startRecording  beforeHookedMethod  出错");
                    Log.i(TAG, "出错原因 —— " + e.getMessage());
                }
            }
        }
    }


    // getRecordingState，获取我们在startRecording和Stop中维护的state的值
    private class GetRecordingStateMethodHook extends  XC_MethodHook{
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if (sMode == 0) {
                try {
                    // 获取我们在startRecording和Stop中维护的state的值
                    AudioRecord record = (AudioRecord) param.thisObject;
                    int res = mRecordingFlagMap.get(record) == null ? AudioRecord.RECORDSTATE_STOPPED : mRecordingFlagMap.get(record);
                    // 将返回值给微信
                    param.setResult(res);
                    // 清理mRecordFlagMap
                    mRecordingFlagMap.remove(record);
                } catch (Exception e) {
                    Log.i(TAG, "AudioRecord # getRecordingState beforeHookedMethod 出错: " + e.getMessage());
                }
            }
        }
    }
}
