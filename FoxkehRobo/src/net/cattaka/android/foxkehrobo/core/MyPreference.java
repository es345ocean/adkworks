
package net.cattaka.android.foxkehrobo.core;

import net.cattaka.android.foxkehrobo.entity.Vector3s;
import net.cattaka.libgeppa.data.DeviceInfo;
import net.cattaka.libgeppa.data.DeviceInfo.DeviceType;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.core.Size;

import android.content.SharedPreferences;

public class MyPreference {
    private static final String OFFSET_ACCEL = "offsetAccel";

    private static final String ACCESS_TOKEN = "accessToken";

    private static final String ACCESS_TOKEN_SECRET = "accessTokenSecret";

    private static final String TRACK_WORDS = "trakWords";

    private static String KEY_PREVIEW_SIZE = "PreviewSize";

    private static String KEY_DEVICE_INFO = "DeviceInfo";

    private SharedPreferences pref;

    private SharedPreferences.Editor editor;

    public MyPreference(SharedPreferences pref) {
        super();
        this.pref = pref;
    }

    public void edit() {
        editor = pref.edit();
    }

    public void commit() {
        editor.commit();
        editor = null;
    }

    public Vector3s getOffsetAccel() {
        Vector3s val = new Vector3s();
        val.setX((short)pref.getInt(OFFSET_ACCEL + "_X", 0));
        val.setY((short)pref.getInt(OFFSET_ACCEL + "_Y", 0));
        val.setZ((short)pref.getInt(OFFSET_ACCEL + "_Z", 0));
        return val;
    }

    public void putOffsetAccel(Vector3s val) {
        editor.putInt(OFFSET_ACCEL + "_X", val.getX());
        editor.putInt(OFFSET_ACCEL + "_Y", val.getY());
        editor.putInt(OFFSET_ACCEL + "_Z", val.getZ());
    }

    public String getAccessToken() {
        return pref.getString(ACCESS_TOKEN, null);
    }

    public void putAccessToken(String accessToken) {
        editor.putString(ACCESS_TOKEN, accessToken);
    }

    public String getAccessTokenSecret() {
        return pref.getString(ACCESS_TOKEN_SECRET, null);
    }

    public void putAccessTokenSecret(String accessTokenSecret) {
        editor.putString(ACCESS_TOKEN_SECRET, accessTokenSecret);
    }

    public String getTrackWords() {
        return pref.getString(TRACK_WORDS, null);
    }

    public void putTrackWords(String accessTokenSecret) {
        editor.putString(TRACK_WORDS, accessTokenSecret);
    }

    public String getPreviewSize() {
        return pref.getString(KEY_PREVIEW_SIZE, "800x600");
    }

    public void putPreviewSize(String previewSize) {
        editor.putString(KEY_PREVIEW_SIZE, previewSize);
    }

    public Size getPreviewSizeAsSize() {
        Size result = null;
        String str = getPreviewSize();
        if (str.indexOf('x') >= 0) {
            String[] ts = str.split("x");
            if (ts.length >= 2) {
                try {
                    double w = Double.parseDouble(ts[0]);
                    double h = Double.parseDouble(ts[1]);
                    result = new Size(w, h);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        if (result == null) {
            result = new Size(800, 600);
        }
        return result;
    }

    public DeviceInfo getDeviceInfo() {
        String str = pref.getString(KEY_DEVICE_INFO, null);
        if (str != null) {
            try {
                JSONObject jo = new JSONObject(str);
                DeviceType deviceType = DeviceType.valueOf(jo.getString("deviceType"));
                boolean supportCamera = jo.getBoolean("supportCamera");
                String tcpHostName = jo.getString("tcpHostName");
                int tcpPort = jo.getInt("tcpHostName");
                String usbDeviceKey = jo.getString("tcpHostName");
                switch (deviceType) {
                    case DUMMY:
                        return DeviceInfo.createDummy(supportCamera);
                    case TCP:
                        return DeviceInfo.createTcp(tcpHostName, tcpPort, supportCamera);
                    case USB:
                        return DeviceInfo.createUsb(usbDeviceKey, supportCamera);
                }

            } catch (JSONException e) {
                // ignore
            } catch (IllegalArgumentException e) {
                // ignore
            } catch (NullPointerException e) {
                // ignore
            }
        }
        return null;
    }

    public void setDeviceInfo(DeviceInfo deviceInfo) {
        if (deviceInfo != null) {
            JSONObject jo = new JSONObject();
            try {
                jo.put("deviceType", deviceInfo.getDeviceType());
                jo.put("supportCamera", deviceInfo.isSupportCamera());
                jo.put("tcpHostName", deviceInfo.getTcpHostName());
                jo.put("tcpPort", deviceInfo.getTcpPort());
                jo.put("usbDeviceKey", deviceInfo.getUsbDeviceKey());
                editor.putString(KEY_DEVICE_INFO, jo.toString());
            } catch (JSONException e) {
                editor.putString(KEY_DEVICE_INFO, null);
            }
        } else {
            editor.putString(KEY_DEVICE_INFO, null);
        }
    }
}
