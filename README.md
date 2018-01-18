# HookWxYYDemo

hook微信语音，实现替换并发送指定语音

### 【2018.1.17】初步提交

确定需要hook的对象和方法，是AudioRecord而不是MediaRecorder。

熟悉文件的流操作，以及AudioRecord的工作过程。

Xposed的使用，注意是在before里面还是after中操作，另外这两者之间的操作也得掌握透彻。


### 【2018.1.18】 修改代码，实现进程间通信

1、通过控制一个在进程间通信的变量，把需要分开编译的代码全部整合在一起

2、模拟器上的hook需要给微信一个AudioRecord变量，并在微信语音输入的开始和结束时调用相应的操作
