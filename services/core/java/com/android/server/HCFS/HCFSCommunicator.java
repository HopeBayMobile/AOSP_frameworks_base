package com.android.server.HCFS;

import com.hopebaytech.hcfsmgmt.terafonnapiservice.AppStatus;
import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Scanner;
import java.util.InputMismatchException;
import java.util.regex.MatchResult;
import android.util.Log;

public class HCFSCommunicator {
	static final String TAG = "HCFSCommunicator";
	static final String appDataPath = "/data/data/";
	static final String appApkPath = "/data/app/";
	static final String appLibPath = "/data/app-lib/";
	static final String appExtPath = "/storage/emulated/0/Android/data/";
	static final int DATA_ALL_LOCAL = 0;
	static final int DATA_HYBRID = 1;
	static final int OFFLINE = 0;
	static final int ONLINE = 1;
	static final int APK_FOLDER = 0;
	static final int APK_FILE = 1;
	static final int NO_ENTRY = 2;
        private static TeraApiService mTeraApiService = null;

        public final class AppAvailableStatus {
                public static final int AVAILABLE = 0;
                public static final int PARTIAL_AVAILABLE = 1;
                public static final int UNAVAILABLE = 2;
        };

        public HCFSCommunicator(Context context) {
                mTeraApiService = TeraApiService.getInstance(context);
        }

	public static int HCFSCmd(String cmd) {
		try {
			java.lang.Process p = Runtime.getRuntime().exec("HCFSvol "+ cmd);
			BufferedReader outputDump = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String tmpLine;
			String resultLine;
			int resultVal = -1;
			while ((tmpLine = outputDump.readLine()) != null) {
				Log.i(TAG, "HCFS command dumps " + tmpLine);
				if (tmpLine.startsWith("Returned value is ")) {
					resultLine = tmpLine.substring("Returned value is ".length());
					resultVal = Integer.parseInt(resultLine);
					Log.i(TAG, "HCFS command dumping result " + resultLine + " int val " + resultVal);
				} else if (tmpLine.startsWith("Num: ")) {
					int local, cloud, hybrid;
					Scanner s = new Scanner(tmpLine);
					s.findInLine("Num: local (-?\\d+), cloud (-?\\d+), hybrid (-?\\d+)");
					MatchResult result = s.match();
					if (result.groupCount() < 3) {
						Log.e(TAG, "Error matching integer. Num of match is " + result.groupCount());
						throw new IOException();
					}
					local = Integer.parseInt(result.group(1));
					cloud = Integer.parseInt(result.group(2));
					hybrid = Integer.parseInt(result.group(3));
					if (cloud <= 0 && hybrid <= 0)
						resultVal = DATA_ALL_LOCAL;
					else
						resultVal = DATA_HYBRID;
				}
			}
			int status = p.waitFor();
			Log.i(TAG, "HCFS command: " + cmd + ". Return status: " + status);
			if (status > 0)
				return -status;
			return resultVal;
		} catch (IOException e) {
			Log.e(TAG, "Error calling HCFS cmd " + cmd);
		} catch (InterruptedException e) {
			Log.e(TAG, "Error calling HCFS cmd " + cmd);
		} catch (InputMismatchException e) {
			Log.e(TAG, "Error calling HCFS cmd " + cmd);
		}

		return -1;
	}

        private static boolean HCFSConnAvailable() {
                boolean result = true;
                int retResult;

                retResult = HCFSCmd("cloudstat");
                switch (retResult) {
                        case ONLINE:
                                result = true;
                                break;
                        case OFFLINE:
                                result = false;
                                break;
                        default:
                                Log.e(TAG, "Failed to check backend status");
                }

                return result;
        }

	private static boolean packagePathAvailable(String path, int type) throws IOException {
		boolean retResult = true;
		int result;

		if (type == APK_FOLDER) {
			result = HCFSCmd("checknode " + path);
                        switch (result) {
                        case DATA_ALL_LOCAL:
                                retResult = true;
                                break;
                        case DATA_HYBRID:
                                retResult = false;
                                break;
                        case -NO_ENTRY:
                                return true;
                        default:
				Log.e(TAG, "When checking " + path + ", result is " + result);
				throw new IOException("Failed to check dir");
                        }
		} else {
			result = HCFSCmd("location " + path);
                        switch (result) {
                        case 0:
                                retResult = true;
                                break;
                        case 1:
                        case 2:
                                retResult = false;
                                break;
                        case -NO_ENTRY:
                                return true;
                        default:
			        Log.e(TAG, "When checking " + path + ", result is " + result);
			        throw new IOException("Failed to check apk");
                        }
		}

		return retResult;
	}

	public static int isAppAvailable(String packageName) {
                /* In Android 7.1, just check /data/data/<pkg>, /data/app/<pkg>, and external */
		String dataPath = appDataPath + packageName;
		String appPath = appApkPath + packageName + "-1";
		//String libPath = appLibPath + packageName + "-1";
                String externalPath = appExtPath + packageName;
                //String dalvikPath = "data@app@" + packageName + "-1.apk@classes.dex";

                if (HCFSConnAvailable())
                        return AppAvailableStatus.AVAILABLE;

		try {
                        if (mTeraApiService == null) {
                                Log.e(TAG, "TeraApiService is not bound.");
                                return AppAvailableStatus.AVAILABLE;
                        }

                        /* <package>.apk is critical condition */
                        if (!packagePathAvailable(appPath, APK_FOLDER)) {
                                Log.i(TAG, "Apk of " + packageName + " is on cloud.");
                                return AppAvailableStatus.UNAVAILABLE;
                        }

                        boolean isPass = packagePathAvailable(dataPath, APK_FOLDER) &&
                                mTeraApiService.isAppAvailable(packageName) == AppStatus.STATUS_AVAILABLE;
                        if (isPass) {
			        Log.i(TAG, packageName + " is now available.");
                                return AppAvailableStatus.AVAILABLE;
                        } else {
			        Log.i(TAG, packageName + " is partially available.");
                                return AppAvailableStatus.PARTIAL_AVAILABLE;
                        }
		} catch (IOException e) {
			return AppAvailableStatus.AVAILABLE;
		}
	}
};
