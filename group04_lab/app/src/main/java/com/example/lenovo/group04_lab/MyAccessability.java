package com.example.lenovo.group04_lab;
import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;
public class MyAccessability extends AccessibilityService{
    Boolean hasNotification =false;
    //Boolean hasWXInforground = false;
    private KeyguardManager.KeyguardLock kl;
    private Handler handler = new Handler();
    @Override
    public void onCreate() {
        super.onCreate();
        //打开Accessibility
        Log.d("i","create sucessfully");
        if(!isAppForeground(this.getPackageName())) {
            bring2Front();
            Log.d("i","top sucessfully");
        }
    }
    @Override
    public void onAccessibilityEvent(final AccessibilityEvent event) {
        int eventType = event.getEventType();
        switch (eventType) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:// 通知栏事件
                Log.d("eventType","notification event");
                List<CharSequence> texts = event.getText();
                if (!texts.isEmpty()) {
                    for (CharSequence text : texts) {
                        String content = text.toString();
                        Log.v("content",content);
                        if(content.contains("[微信红包]")){
                            //如果锁定，先解锁
                            if(isScreenLocked()){
                                wakeAndUnlock();
                                Log.d("i","解锁");
                            }
                            hasNotification =  true;
                            //打开微信界面
                            Notification notification =(Notification)event.getParcelableData();
                            if (event.getParcelableData() != null && event.getParcelableData() instanceof Notification) {
                                PendingIntent pendingIntent = notification.contentIntent;
                                try {
                                    pendingIntent.send();
                                    Log.d("i","打开微信界面");
                                } catch (PendingIntent.CanceledException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED://某个view的内容发生的变化，对于红包而言：出现在打开微信却不是在com.tencent.mm.ui.LauncherUI界面或者出现红包雨的情况下或者只要微信有消息就行
                Log.d("eventType","View event");
                String className_view = event.getClassName().toString();
                Log.d("view变化",className_view);
                hasPacketInWXforground();//过滤掉微信主界面不是微信红包的信息，只进入有微信红包的聊天界面
                openPacket();
                hasNotification = true;
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED://窗口体状态，事件源:跳转到新的页面，或者弹出了window，dialog等
                Log.d("eventType","Window event");
                String className = event.getClassName().toString();
                Log.d("窗口体变化",className);
                if(hasNotification){//先判断hasNotification为true，说明是进入有红包的聊天界面，以免所有的通知信息都进入聊天界面
                    if(className.equals("com.tencent.mm.ui.LauncherUI")){
                        Log.d("e","进入打开红包界面");
                        openPacket();
                    }
                    else if(className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI") ){
                        Log.d("i","进入点击红包界面");
                        receivePacket();
                    }
                    else if(className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI")){
                        Log.d("e","进入红包记录界面");
                        release();
                        //可以在这里用handler进行抢红包延时
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    }
                }
                break;
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                Log.d("eventType","Text event");
                String className_text = event.getClassName().toString();
                Log.d("i",className_text);
                break;
        }
    }
    @Override
    public void onInterrupt() {
    }
    private void openPacket(){
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if(nodeInfo == null) return ;
        //获取该界面的包含“领取红包”Text的所有节点
        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText("领取红包");
        if(list.size()==0) return;
        Log.d("i",list.size()+"个红包可以领取");
        //最新的红包领起
        for (int i = list.size() - 1; i >= 0; i--) {
            AccessibilityNodeInfo parent = list.get(i).getParent();//获取该节点的父节点
            if (parent != null) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);//模拟点击
                break;
            }
        }
    }
    private void receivePacket() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo == null) {
            Log.d("node","数量为null");
            return;
        }
        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/c85");//打开红包按钮的id
        Log.d("i", list.size() + "个红包可以领取");
        for (AccessibilityNodeInfo n : list) {
            if (n.isClickable()) {
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d("i", "领取成功");
                break;
            }
        }
        //说明已经没有红包领了,让通知栏判断为false
        if (list.size()==0) {
            hasNotification = false;
            Log.d("d", "没有通知了");
        }
    }
    //当我们在微信主界面时，有新消息接收，不会以通知栏的形式出现，需要自行判断是否有红包（判断id:com.tencent.mm:id/apx）
    private void hasPacketInWXforground(){
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if(rootNode==null) return ;
        List<AccessibilityNodeInfo> list = rootNode.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/apx");//该id判断栏目的第二小行的第二列，如新消息里的XX:[微信红包]大吉大利，其中[微信红包]..就是
        for(int i=0;i<list.size();i++){
            String content = list.get(i).getText().toString();
            if(content.contains("[微信红包]")){
                AccessibilityNodeInfo parent = list.get(i).getParent();
                List<AccessibilityNodeInfo> list_new = parent.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/jj");//新消息的数量，含数字的小红球
                List<AccessibilityNodeInfo> list_mute = parent.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/apu");//已经屏蔽的群信息
                //过滤掉已阅览的群节点
                if((list_new.size()>0 || list_mute.size()>0) && parent.isClickable()){
                    Log.d("i","进入聊天界面");
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    break;
                }
            }
        }
    }
    //判断指定的应用是否在前台运行
    private boolean isAppForeground(String packageName) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ComponentName cn = am.getRunningTasks(1).get(0).topActivity;
        String currentPackageName = cn.getPackageName();
        if (!TextUtils.isEmpty(currentPackageName) && currentPackageName.equals(packageName)) {
            return true;
        }
        return false;
    }
    //将当前应用运行到前台
    private void bring2Front() {
        ActivityManager activtyManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTaskInfos = activtyManager.getRunningTasks(20);
        for (ActivityManager.RunningTaskInfo runningTaskInfo : runningTaskInfos) {
            Log.d("pageage",runningTaskInfo.topActivity.getPackageName());
            if (this.getPackageName().equals(runningTaskInfo.topActivity.getPackageName())) {
                activtyManager.moveTaskToFront(runningTaskInfo.id, ActivityManager.MOVE_TASK_WITH_HOME);
                return;
            }
        }
    }
    //回到系统桌面
    private void back2Home() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        home.addCategory(Intent.CATEGORY_HOME);
        startActivity(home);
    }
    //系统是否在锁屏状
    private boolean isScreenLocked() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return keyguardManager.inKeyguardRestrictedInputMode();
    }
    private void wakeAndUnlock() {
        //获取电源管理器对象
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        //获取PowerManager.WakeLock对象，后面的参数|表示同时传入两个值，最后的是调试用的Tag
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");
        //点亮屏幕
        wl.acquire(1000);
        //得到键盘锁管理器对象
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        kl = km.newKeyguardLock("unLock");
        //解锁
        kl.disableKeyguard();
    }
    private void release() {
        if (kl != null) {
            android.util.Log.d("maptrix", "release the lock");
            //得到键盘锁管理器对象
            kl.reenableKeyguard();
        }
    }
}
