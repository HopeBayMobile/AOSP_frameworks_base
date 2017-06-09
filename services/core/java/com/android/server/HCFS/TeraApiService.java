package com.android.server.HCFS;

//import android.app.Notification;
//import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
//import android.support.v4.app.NotificationCompat;
//import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

//import com.android.launcher3.compat.UserHandleCompat;
import com.hopebaytech.hcfsmgmt.terafonnapiservice.AppInfo;
import com.hopebaytech.hcfsmgmt.terafonnapiservice.AppStatus;
//import com.hopebaytech.hcfsmgmt.terafonnapiservice.ICheckAppAvailableListener;
import com.hopebaytech.hcfsmgmt.terafonnapiservice.ITeraFonnApiService;
import com.hopebaytech.hcfsmgmt.terafonnapiservice.ITrackAppStatusListener;

import java.util.ArrayList;
import java.util.List;

public class TeraApiService {
    private static Context mContext;
    private final static String TAG = "HCFSTeraApiService";

    private static TeraApiService mTeraApiService;
    private static final Object lock = new Object();
    private static ITeraFonnApiService mTeraService = null;
    //private static LauncherModel mModel = null;
    private static boolean mIsBound = false;
    private static boolean wait_for_connected = false;
    private static boolean hcfsEnable = false;
    private static boolean isPinUnpinEnabled = false;
    private static boolean initPinUnpinEnabled = false;
    //final static UserHandleCompat user = UserHandleCompat.myUserHandle();
    private static final ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "on Service connected");
            mTeraService = ITeraFonnApiService.Stub.asInterface(service);
            wait_for_connected = false;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "on Service disconnected");
            clearParam();
            bindAPIService();
        }

    };

    private static void clearParam() {
        mTeraService = null;
        mIsBound = false;
    }


    private TeraApiService(Context context) {
        mContext=context;
    }

    public static TeraApiService getInstance(Context context) {
        if (mTeraApiService == null) {
            synchronized (TeraApiService.class) {
                if (mTeraApiService == null) {
                    mTeraApiService = new TeraApiService(context);
                    bindAPIService();
                }
            }
        }
        return mTeraApiService;
    }

    public static void bindAPIService() {
        synchronized (lock) {
            if (mTeraService == null && !wait_for_connected) {
                Log.d(TAG, "Binding to API Service");
                Intent intent = new Intent();
                final String packageName = "com.hopebaytech.hcfsmgmt";
                final String className = "com.hopebaytech.hcfsmgmt.terafonnapiservice.TeraFonnApiService";
                intent.setComponent(new ComponentName(packageName, className));
                ComponentName serviceComponentName = mContext.startService(intent);
                if (serviceComponentName != null) {
                    mIsBound = mContext.bindService(intent, mConn, Context.BIND_AUTO_CREATE);
                    if (mIsBound)
                        wait_for_connected = true;
                } else {
                    Log.e(TAG, "Failed to startService");
                }
            }
        }
    }

    public String getHCFSStat() {
        String result="{\"result\":\"false\"}";
        try {
            result = mTeraService.getHCFSStat();
        } catch (DeadObjectException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            clearParam();
            bindAPIService();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return result;
    }

    public boolean hcfsEnabled() {
        if (hcfsEnable)
            return hcfsEnable;
        boolean enabled = false;
        if (!serviceReady()) {
            return false;
        }
        try {
            enabled = mTeraService.hcfsEnabled();
            hcfsEnable = enabled;
        } catch (DeadObjectException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            clearParam();
            bindAPIService();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            hcfsEnable = false;
        }
        return enabled;
    }

    public boolean pinApp(String packageName) {
        boolean isSuccess = false;
        try {
            isSuccess = mTeraService.pinApp(packageName);
        } catch (DeadObjectException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            clearParam();
            bindAPIService();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

        return isSuccess;
    }

    public boolean unpinApp(String packageName) {
        boolean isSuccess = false;
        try {
            isSuccess = mTeraService.unpinApp(packageName);
        } catch (DeadObjectException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            clearParam();
            bindAPIService();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return isSuccess;
    }

    public boolean isAppPinned(String packageName) {
        boolean pinned = false;
        AppInfo appInfo;
        List<String> packagesName = new ArrayList<String>();
        packagesName.add(packageName);
        try {
            appInfo = mTeraService.getAppInfo(packagesName);
            List<AppStatus> appStatus = appInfo.getAppStatusList();
            for (int i=0; i < appStatus.size(); i++) {
                pinned = appStatus.get(i).isPin();
            }
        } catch (DeadObjectException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            clearParam();
            bindAPIService();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return pinned;
    }

    public int isAppAvailable(String packageName) {
        int status = AppStatus.STATUS_AVAILABLE;
        if (!serviceReady()) {
            Log.e(TAG, "Tera api service is not bound.");
            return status;
        }
        List<String> packagesName = new ArrayList<String>();
        packagesName.add(packageName);
        try {
            status = mTeraService.checkAppAvailable(packageName);
        } catch (DeadObjectException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            bindAPIService();
            clearParam();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return status;
    }

/*    public void isAppAvailablePostResponse(String packageName, ICheckAppAvailableListener listener) {
        try {
            mTeraService.postCheckAppAvailable(packageName, listener);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
*/

    public static boolean serviceReady() {
        if (mTeraService == null) {
            clearParam();
            bindAPIService();
            if (mTeraService == null)
                    return false;
            else
                    return true;
        } else {
            return true;
        }
    }

    public static void unbind() {
        synchronized (lock) {
            if (mIsBound) {
                Log.d(TAG, "HCFS API Service unbind");
                mContext.unbindService(mConn);
                mIsBound = false;
            }
        }
    }

/*
    public static void notify(Context context, int notifyId, String notifyTitle, String notifyMessage) {
        NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle();
        bigStyle.bigText(notifyMessage);

        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_tera_app_default);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder = builder
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.icon_tera_logo_status_bar)
                .setLargeIcon(largeIcon)
                .setTicker(notifyTitle)
                .setContentTitle(notifyTitle)
                .setContentText(notifyMessage)
                .setStyle(bigStyle);
        int defaults = 0;
        defaults |= NotificationCompat.DEFAULT_VIBRATE;
        builder.setAutoCancel(true)
                .setOngoing(false)
                .setDefaults(defaults)
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(), 0)).build();
        Notification notification = builder.build();
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        notificationManagerCompat.notify(notifyId, notification);
    }
    */

    public boolean isTeraWifiOnly() {
        boolean isWifiOnly = true;
        try {
            isWifiOnly = mTeraService.isWifiOnly();
        } catch (DeadObjectException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            clearParam();
            bindAPIService();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return isWifiOnly;
    }

    public int getConnStatus() {
        int status = 1;
        try {
            status = mTeraService.getConnStatus();
        } catch (DeadObjectException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            clearParam();
            bindAPIService();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return status;
    }

    public boolean isAllowPinUnpinApps() {
        if (initPinUnpinEnabled)
            return isPinUnpinEnabled;
        try {
            isPinUnpinEnabled = mTeraService.isAllowPinUnpinApps();
            initPinUnpinEnabled = true;
        } catch (DeadObjectException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            clearParam();
            bindAPIService();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return isPinUnpinEnabled;
    }

    public void setAllowPinUnpinApps(boolean enabled) {
        isPinUnpinEnabled = enabled;
    }

    public int getAppBoostStatus(String packageName) {
        int status = AppStatus.BoostStatus.UNBOOSTED;
        try {
            status = mTeraService.getAppBoostStatus(packageName);
        } catch (DeadObjectException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            clearParam();
            bindAPIService();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return status;
    }

    public static boolean isBoostOrUnboostInProgress(String packageName) {
        boolean status = false;
        try {
            status = mTeraService.isBoostOrUnboostInProgress(packageName);
        } catch (DeadObjectException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            clearParam();
            bindAPIService();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return status;
    }
}
