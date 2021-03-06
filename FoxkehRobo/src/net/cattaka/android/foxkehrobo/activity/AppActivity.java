
package net.cattaka.android.foxkehrobo.activity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.cattaka.android.foxkehrobo.Constants;
import net.cattaka.android.foxkehrobo.FoxkehRoboService;
import net.cattaka.android.foxkehrobo.R;
import net.cattaka.android.foxkehrobo.core.IAppStub;
import net.cattaka.android.foxkehrobo.core.MyPreference;
import net.cattaka.android.foxkehrobo.core.ServiceWrapper;
import net.cattaka.android.foxkehrobo.data.FaceDetectionAlgorism;
import net.cattaka.android.foxkehrobo.data.FrPacket;
import net.cattaka.android.foxkehrobo.db.FoxkehRoboDatabase;
import net.cattaka.android.foxkehrobo.fragment.ActionListFragment;
import net.cattaka.android.foxkehrobo.fragment.AiModeFragment;
import net.cattaka.android.foxkehrobo.fragment.BaseFragment;
import net.cattaka.android.foxkehrobo.fragment.ConnectFragment;
import net.cattaka.android.foxkehrobo.fragment.ControllerFragment;
import net.cattaka.android.foxkehrobo.opencv.DetectionBasedTracker;
import net.cattaka.libgeppa.IActiveGeppaService;
import net.cattaka.libgeppa.IActiveGeppaServiceListener;
import net.cattaka.libgeppa.adapter.IDeviceAdapterListener;
import net.cattaka.libgeppa.data.ConnectionState;
import net.cattaka.libgeppa.data.DeviceEventCode;
import net.cattaka.libgeppa.data.DeviceInfo;
import net.cattaka.libgeppa.data.DeviceState;
import net.cattaka.libgeppa.data.PacketWrapper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class AppActivity extends FragmentActivity implements IAppStub, View.OnClickListener {
    private static final int EVENT_ON_DEVICE_STATE_CHANGED = 1;

    private static final int EVENT_ON_RECEIVE_PACKET = 2;

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceWrapper = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            IActiveGeppaService service = IActiveGeppaService.Stub.asInterface(binder);
            mServiceWrapper = new ServiceWrapper(service);
            getBaseFragment().onServiceConnected(mServiceWrapper);

            try {
                mGeppaServiceListenerSeq = service.registerServiceListener(mServiceListener);
                DeviceInfo deviceInfo = mServiceWrapper.getCurrentDeviceInfo();
                if (deviceInfo != null) {
                    mConnectionStateText.setText((deviceInfo != null) ? deviceInfo.getLabel()
                            : "null");
                } else if (getMyPreference().getStartupOnBoot()) {
                    deviceInfo = getAvailableDeviceInfo();
                    if (deviceInfo != null) {
                        mConnectionStateText.setText("connecting");
                        service.connect(deviceInfo);
                    } else {
                        mConnectionStateText.setText("null");
                    }
                }
            } catch (RemoteException e) {
                // Impossible, ignore
                Log.w(Constants.TAG, e.getMessage(), e);
            }
        }
    };

    class FragmentPagerAdapterEx extends FragmentPagerAdapter {

        public FragmentPagerAdapterEx(FragmentManager fm) {
            super(fm);
        }

        private Fragment[] fragments = new Fragment[] {
                new ConnectFragment(), new ActionListFragment(), new ControllerFragment(),
                new AiModeFragment(),
        };

        @Override
        public int getCount() {
            return fragments.length;
        }

        @Override
        public Fragment getItem(int idx) {
            return fragments[idx];
        }
    };

    private IActiveGeppaServiceListener mServiceListener = new IActiveGeppaServiceListener.Stub() {
        @Override
        public void onReceivePacket(PacketWrapper packet) throws RemoteException {
            sHandler.obtainMessage(EVENT_ON_RECEIVE_PACKET, new Object[] {
                    me, packet.getPacket()
            }).sendToTarget();
        }

        public void onDeviceStateChanged(final DeviceState state, final DeviceEventCode code,
                final DeviceInfo deviceInfo) throws RemoteException {
            sHandler.obtainMessage(EVENT_ON_DEVICE_STATE_CHANGED, new Object[] {
                    me, state, code, deviceInfo
            }).sendToTarget();
        }
    };

    private static Handler sHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            Object[] objs = (Object[])msg.obj;
            AppActivity target = (AppActivity)objs[0];
            if (msg.what == EVENT_ON_RECEIVE_PACKET) {
                for (IDeviceAdapterListener<FrPacket> listener : target.mDeviceAdapterListeners) {
                    listener.onReceivePacket((FrPacket)objs[1]);
                }
            } else if (msg.what == EVENT_ON_DEVICE_STATE_CHANGED) {
                DeviceState deviceState = (DeviceState)objs[1];
                DeviceEventCode deviceEventCode = (DeviceEventCode)objs[2];
                DeviceInfo deviceInfo = (DeviceInfo)objs[3];
                target.mConnectionStateText.setText((deviceInfo != null) ? deviceInfo.getLabel()
                        : "null");
                for (IDeviceAdapterListener<FrPacket> listener : target.mDeviceAdapterListeners) {
                    listener.onDeviceStateChanged(deviceState, deviceEventCode, (DeviceInfo)objs[3]);
                }
            }
        };
    };

    private OnPageChangeListener mOnPageChangeListener = new OnPageChangeListener() {
        private int lastPosition = -1;

        @Override
        public void onPageSelected(final int position) {
            BaseFragment fragment = getBaseFragment();
            if (fragment.isResumed()) {
                if (lastPosition != position) {
                    if (lastPosition >= 0) {
                        getBaseFragment(lastPosition).onPageDeselected();
                    }
                    if (position >= 0) {
                        getBaseFragment(position).onPageSelected();
                    }
                    lastPosition = position;
                }
            } else if (mMyIsResumed) {
                sHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onPageSelected(position);
                    }
                }, 1);
            }
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }
    };

    // private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this)
    // {
    // @Override
    // public void onManagerConnected(int status) {
    // switch (status) {
    // case LoaderCallbackInterface.SUCCESS: {
    // Log.i(Constants.TAG, "OpenCV loaded successfully");
    //
    // // Load native library after(!) OpenCV initialization
    // System.loadLibrary("detection_based_tracker");
    //
    // loadCascade();
    // }
    // break;
    // default: {
    // super.onManagerConnected(status);
    // }
    // break;
    // }
    // }
    // };

    @Override
    public void loadCascade(FaceDetectionAlgorism algorism) {
        try {
            // load cascade file from application resources
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            mCascadeFile = new File(cascadeDir, algorism.filename);
            // mCascadeFile = new File(cascadeDir,
            // "haarcascade_frontalface_alt_tree.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);

            cascadeDir.delete();

        } catch (IOException e) {
            Log.e(Constants.TAG, "Failed to load cascade. Exception thrown: " + e);
            throw new RuntimeException(e);
        }
    };

    private AppActivity me = this;

    private File mCascadeFile;

    private DetectionBasedTracker mNativeDetector;

    private int mGeppaServiceListenerSeq = -1;

    private ServiceWrapper mServiceWrapper;

    private TextView mConnectionStateText;

    private FoxkehRoboDatabase mDbHelper;

    private ViewPager mBodyPager;

    private MyPreference mMyPreference;

    private List<IDeviceAdapterListener<FrPacket>> mDeviceAdapterListeners;

    private boolean mMyIsResumed = false;

    @Override
    protected void onCreate(Bundle arg0) {
        super.onCreate(arg0);
        setContentView(R.layout.app);

        mDeviceAdapterListeners = new ArrayList<IDeviceAdapterListener<FrPacket>>();

        mConnectionStateText = (TextView)findViewById(R.id.connectionStateText);

        // Setting initial values
        mConnectionStateText.setText(ConnectionState.UNKNOWN.name());

        {
            mBodyPager = (ViewPager)findViewById(R.id.bodyPager);
            FragmentPagerAdapter adapter = new FragmentPagerAdapterEx(getSupportFragmentManager());
            mBodyPager.setAdapter(adapter);

            mBodyPager.setOnPageChangeListener(mOnPageChangeListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMyIsResumed = true;
        mMyPreference = new MyPreference(PreferenceManager.getDefaultSharedPreferences(this));

        loadCascade(mMyPreference.getFaceDetectionAlgorism());

        { // opening DB
            if (mDbHelper != null) {
                mDbHelper.close();
                ;
            }
            if (mDbHelper == null) {
                mDbHelper = new FoxkehRoboDatabase(this);
            }
        }

        Intent service = new Intent(this, FoxkehRoboService.class);
        bindService(service, mServiceConnection, BIND_AUTO_CREATE);

        if (getMyPreference().getAiModeOnStart()) {
            FragmentPagerAdapter adapter = (FragmentPagerAdapter)mBodyPager.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                Fragment fragment = adapter.getItem(i);
                if (fragment instanceof AiModeFragment) {
                    mBodyPager.setCurrentItem(i);
                    break;
                }
            }
        }
        mOnPageChangeListener.onPageSelected(mBodyPager.getCurrentItem());
    }

    @Override
    protected void onPause() {
        mMyIsResumed = false;
        mOnPageChangeListener.onPageSelected(-1);
        super.onPause();
        { // closing DB
            mDbHelper.close();
            mDbHelper = null;
        }

        if (mGeppaServiceListenerSeq != -1) {
            try {
                mServiceWrapper.getService().unregisterServiceListener(mGeppaServiceListenerSeq);
            } catch (RemoteException e) {
                // impossible, ignore
                Log.w(Constants.TAG, e.getMessage(), e);
            }
            mGeppaServiceListenerSeq = -1;
        }

        mMyPreference = null;

        unbindService(mServiceConnection);
    }

    public BaseFragment getBaseFragment() {
        return (BaseFragment)((FragmentPagerAdapter)mBodyPager.getAdapter()).getItem(mBodyPager
                .getCurrentItem());
    }

    public BaseFragment getBaseFragment(int position) {
        return (BaseFragment)((FragmentPagerAdapter)mBodyPager.getAdapter()).getItem(position);
    }

    @Override
    public ServiceWrapper getServiceWrapper() {
        return mServiceWrapper;
    }

    @Override
    public void onClick(View v) {
    }

    @Override
    public void replacePrimaryFragment(Fragment fragment, boolean withBackStack) {
        // TODO
    }

    @Override
    public boolean registerDeviceAdapterListener(IDeviceAdapterListener<FrPacket> listener) {
        if (!mDeviceAdapterListeners.contains(listener)) {
            mDeviceAdapterListeners.add(listener);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean unregisterDeviceAdapterListener(IDeviceAdapterListener<FrPacket> listener) {
        return mDeviceAdapterListeners.remove(listener);
    }

    @Override
    public void setKeepScreen(boolean flag) {
        if (flag) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public FoxkehRoboDatabase getDroiballDatabase() {
        return mDbHelper;
    }

    @Override
    public MyPreference getMyPreference() {
        return mMyPreference;
    }

    @Override
    public DetectionBasedTracker getNativeDetector() {
        return mNativeDetector;
    }

    private DeviceInfo getAvailableDeviceInfo() {
        int idVendor = getResources().getInteger(R.integer.idVendor);
        int idProduct = getResources().getInteger(R.integer.idProduct);
        UsbManager usbManager = (UsbManager)getSystemService(USB_SERVICE);
        for (Map.Entry<String, UsbDevice> entry : usbManager.getDeviceList().entrySet()) {
            UsbDevice device = entry.getValue();
            if (device.getVendorId() == idVendor && device.getProductId() == idProduct) {
                return DeviceInfo.createUsb(entry.getKey(), true);
            }
        }
        return null;
    }
}
