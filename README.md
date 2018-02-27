# HookWxYYDemo

hook微信语音，实现替换并发送指定语音

### 【2018.1.17】初步提交

确定需要hook的对象和方法，是AudioRecord而不是MediaRecorder。

熟悉文件的流操作，以及AudioRecord的工作过程。

Xposed的使用，注意是在before里面还是after中操作，另外这两者之间的操作也得掌握透彻。


### 【2018.1.18】 修改代码，实现进程间通信

1、通过控制一个在进程间通信的变量，把需要分开编译的代码全部整合在一起

2、模拟器上的hook需要给微信一个AudioRecord变量，并在微信语音输入的开始和结束时调用相应的操作

###  【2018.1.20】实现在模拟器中替换语音

由于模拟器点击录制语音时弹不出话筒，前两天的例子是自己实现一个AudioRecord，所以这次直接hook微信的AudioRecord的startRecording方法，并设置返回值，让微信的录制流程不能正常执行。然后hookgetRecordState方法，自己维护AudioRecord的开始和停止。在startRecording中设为starting状态，在stop时设置为stopped状态，这样微信就可以正常执行read函数了，我们在read中再hook改变发送的语音，即可实现在模拟器中替换发送的语音了。



### 【2018.1.22】 Xposed模块中封装两个Api接口

1、int pushAudio(String pcmfile);//使得每次微信发语音 都发出指定的pcm语音

2、int recordAudio(String pcmfile);//将微信录音 录到指定目录下

### 【2018.1.22】 自定义类继承XC_MethodHook，提高代码简洁性

1、将文件父目录的权限修改的从read中移动到stop操作

2、去掉多余的代码， 在stop删除临时文件

3、修改调用Api的逻辑，实现相应的功能调用

### 【2018.1.24】 实现在进程间调用封装好的Api接口

1、修改主界面布局。目前有五个语音可以录制，发送

2、利用ContentProvider来实现进程间通信

### 【2018.2.27】 目前的最终版

删除没有必要的类，简化代码
