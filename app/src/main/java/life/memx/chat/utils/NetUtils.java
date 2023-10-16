package life.memx.chat.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

public class NetUtils {
    private static final long DELAY_TINE = 500;
    private static final long RECYCLE_TIME = 1588;//默认循环时间问隔(尧秒)
    private Context mContext;
    private Timer mTimer;
    private long mLastTotalRxBytes;
    private long mLastTimeStamp;
    private long mDelayTime = DELAY_TINE;//延时启动时间
    private long mRecyclerTime = RECYCLE_TIME;//循环时间间隔
    private String mobileSignal = "";

    public NetUtils(Context context) {
        this.mContext = context;
    }

    public NetUtils setDelayTime(long delayTime) {
        mDelayTime = delayTime;
        return this;
    }

    public NetUtils setRecyclerTime(long recyclerTime) {
        mRecyclerTime = recyclerTime;
        return this;
    }

    public void start(TextView view) {
        final int[] lastSignal = {0};
        if (mTimer == null) {
            mTimer = new Timer();
        }
        TelephonyManager tm = ((TelephonyManager) view.getContext().getSystemService(Context.TELEPHONY_SERVICE));
        WifiManager wifiManager = (WifiManager) view.getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (mContext != null) {
                    ((AppCompatActivity) mContext).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            String speed = getNetSpeed();
                            String signal = "";
                            if (isWifi(view.getContext())) {
                                int i = wifiManager.getConnectionInfo().getRssi();
                                signal = "Signal intensity: " + i;
                                if (i >= -50) {
                                    signal += " Good signal";
                                } else if (i < -50 && i >= -70) {
                                    signal += " Weak signal";
                                } else {
                                    signal += " Bad signal";
                                }
                            } else if (isMobile(view.getContext())) {
                                signal =  mobileSignal;
                            }
                            view.setText("Net speed: " + speed + "\n" + signal + (isWifi(view.getContext()) ? "\nWIFI connected" : isMobile(view.getContext()) ? "\nMobile network" : "\nNetwork not connected"));
                        }
                    });
                }
            }
        }, mDelayTime, mRecyclerTime);
    }

    public void cancel() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    public  void getPhoneState(Context context) {
        final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        PhoneStateListener MyPhoneListener = new PhoneStateListener() {
            @Override//获取对应网络的ID，这个方法在这个程序中没什么用处
            public void onCellLocationChanged(CellLocation location) {
                if (location instanceof GsmCellLocation) {
                    int CID = ((GsmCellLocation) location).getCid();
                } else if (location instanceof CdmaCellLocation) {
                    int ID = ((CdmaCellLocation) location).getBaseStationId();
                }
            }//系统自带的服务监听器，实时监听网络状态

            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                super.onServiceStateChanged(serviceState);
            }//这个是我们的主角，就是获取对应网络信号强度

            @SuppressLint("MissingPermission")
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {//这个ltedbm 是4G信号的值
//                telephonyManager.re
                String signalinfo = signalStrength.toString();
                String[] parts = signalinfo.split(" ");
                String ltedbm = String.valueOf(parts[9]);//这个dbm 是2G和3G信号的值
                int asu = signalStrength.getGsmSignalStrength();
                int dbm = -113 + 2 * asu;
                if (telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_LTE) {
                    Log.i("NetWorkUtil", "网络：LTE 信号强度：" + ltedbm + "======Detail:" + signalinfo);
                    mobileSignal = "网络信号值："+ltedbm;
                } else if (telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSDPA || telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSPA || telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_HSUPA || telephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_UMTS) {
                    String bin;
//                    dbm = signalStrength.getCdmaDbm();
                    if (dbm > -75) {
                        bin = "网络很好";
                    } else if (dbm > -85) {
                        bin = "网络不错";
                    } else if (dbm > -95) {
                        bin = "网络还行";
                    } else if (dbm > -100) {
                        bin = "网络很差";
                    } else {
                        bin = "网络错误";
                    }
                    Log.i("NetWorkUtil", "网络：WCDMA 信号值：" + dbm + "========强度：" + bin + "======Detail:" + signalinfo);
                    mobileSignal = "网络信号值："+dbm+" "+bin;

                } else {
                    String bin;
                    dbm = signalStrength.getCdmaDbm();
                    if (dbm > 200) {
                        return;
                    }
                    asu = Math.round((dbm + 113) / 2.0F);
                    if (asu < 0 || asu >= 99) bin = "网络错误";
                    else if (asu >= 16) bin = "网络很好";
                    else if (asu >= 8) bin = "网络不错";
                    else if (asu >= 4) bin = "网络还行";
                    else bin = "网络很差";
                    Log.i("NetWorkUtil", "网络：GSM 信号值：" + dbm + "========强度：" + bin + "======Detail:" + signalinfo);
                    mobileSignal = "网络信号值："+dbm+" "+bin;
                }
                super.onSignalStrengthsChanged(signalStrength);
            }
        };
        try {
            telephonyManager.listen(MyPhoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public String getNetSpeed() {
        long nowTotalRxBytes = getTotalRxBytes();
        long nowTimeStamp = System.currentTimeMillis();
        long differTimeStamp = nowTimeStamp - mLastTimeStamp;
        long speed = differTimeStamp != 0 ? ((nowTotalRxBytes - mLastTotalRxBytes) * 1000 / differTimeStamp) : 0;
        mLastTimeStamp = nowTimeStamp;
        mLastTotalRxBytes = nowTotalRxBytes;
        String netSpeed = formatNetSpeed(speed);
        return netSpeed;
    }

    public String formatNetSpeed(long bytes) {
        String speedString = null;
        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        if (bytes >= 1048576d) {
            speedString = decimalFormat.format(bytes / 1048576d) + " MB/s";
        } else if (bytes > 1024d) {
            speedString = decimalFormat.format(bytes / 1024d) + " KB/s";
        } else if (bytes >= 0d) {
            speedString = decimalFormat.format(bytes) + " K/s";
        }
        return speedString;
    }

    private long getTotalRxBytes() {
        int uid = mContext.getApplicationInfo().uid;
        return TrafficStats.getUidRxBytes(uid) == TrafficStats.UNSUPPORTED ? 0 : (TrafficStats.getTotalRxBytes() / 1024);
    }

    /**
     * check is3G
     *
     * @return boolean
     */
    public static boolean isMobile(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetInfo != null && activeNetInfo.getType() == ConnectivityManager.TYPE_MOBILE;
    }

    /**
     * check is Wifi
     *
     * @return boolean
     */
    public static boolean isWifi(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetInfo != null && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

}
