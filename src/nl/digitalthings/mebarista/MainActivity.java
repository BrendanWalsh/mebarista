package nl.digitalthings.mebarista;

/* Hint: If you are connecting to a Bluetooth serial board then try using
 * the well-known SPP UUID 00001101-0000-1000-8000-00805F9B34FB. However 
 * if you are connecting to an Android peer then please generate your own unique UUID. */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.StrictMode;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.tools.ZoomEvent;
import org.achartengine.tools.ZoomListener;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

public class MainActivity extends Activity
        implements View.OnClickListener, ZoomListener, SurfaceHolder.Callback {
    private static final String TAG = "MainActivity";
    private static final String TAG_SC = "ScreenCapture";

    // Constants
    private static final int REQUEST_ENABLE_BT = 105;
    public static final int MESSAGE_READ = 106;
    public static final int DEVICE_ON = 107;
    public static final int DEVICE_OFF = 108;
    public static final int MESSAGE_TOAST = 109;
    public static final int UPDATE = 110;
    public static final int SPINNER_ON = 111;
    public static final int SPINNER_OFF = 112;

    // Controls
    private TextView btemp, setpoint, logging;
    private ImageView indicator;
    private ImageView background;
    private GraphicalView mChartView;
    private Gauge meter1, meter2;
    private LinearLayout tempcontrol;
    private View video;
    private ProgressBar progress_bar;
    private WebView installation_manual;
    private Button minbutton, plusbutton;

    // State
    private long last_message;
    private Timer indicator_watchdog_timer;
    private int teller;
    private boolean lock_graph;
    boolean piddisabled = true;
    double curSetpoint;
    boolean logging_enabled = false;

    int time_scale = 1000;
    private Timer handleintentTimer;

    private ShareActionProvider mShareActionProvider;

    // Util
    SharedPreferences sharedPrefs;


    // Shot timer
    private long startTime = 0L;
    private Handler customHandler = new Handler();
    float preinfusionDelay = 0, preinfusionPump = 0; // This is a float because SliderPreference returns a float

    // Video
    // http://stackoverflow.com/questions/1817742/how-can-i-capture-a-video-recording-on-android
    // TODO: remove recorder, just use camera preview
    MediaRecorder mMediaRecorder, recorder;
    SurfaceHolder holder;
    boolean recording = false;

    // Screen recording
    // https://android.googlesource.com/platform/development/+/bf1e262/samples/ApiDemos/src/com/example/android/apis/media/projection/MediaProjectionDemo.java
    // http://www.truiton.com/2015/05/capture-record-android-screen-using-mediaprojection-apis/
    private static class Resolution {
        int x;
        int y;

        public Resolution(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return x + "x" + y;
        }
    }

    private static final int SCREEN_RECORDING_REQUEST_CODE = 666;
    private static final List<Resolution> RESOLUTIONS = new ArrayList<Resolution>() {{
        add(new Resolution(1280, 720));
        add(new Resolution(640, 360));
        add(new Resolution(960, 540));
        add(new Resolution(1366, 768));
        add(new Resolution(1600, 900));
    }};

    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private boolean mScreenSharing;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private Surface mSurface;
    private SurfaceView mSurfaceView;

    private boolean cameraToggleStatus = false;

    // Video
    private void prepareRecorder() {
        Log.i(TAG, "prepareRecorder");
        recorder.setPreviewDisplay(holder.getSurface());

        try {
            recorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            finish();
        } catch (IOException e) {
            e.printStackTrace();
            finish();
        }
    }

    private void initRecorder() {
        Log.i(TAG, "initRecorder");
        //recorder.setCamera(Camera.open( 0 ) );

        CamcorderProfile cpHigh = CamcorderProfile
                .get(CamcorderProfile.QUALITY_HIGH);

        recorder = new MediaRecorder();

        //recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);

        recorder.setProfile(cpHigh);
        recorder.setOutputFile(Environment.getExternalStorageDirectory().getPath() + "/videocapture_example.mp4");
        recorder.setMaxDuration(50000); // 50 seconds
        recorder.setMaxFileSize(5000000); // Approximately 5 megabytes

        background.setVisibility(View.INVISIBLE);

        prepareRecorder();

        recorder.start();
    }

    Camera camera;

    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");

        // prepareRecorder();

        camera = Camera.open();

        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            // previewRunning = true;
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }

    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        Log.i(TAG, "surfaceChanged");
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");

        if (recording) {

            recorder.stop();
            recording = false;

            recorder.release();

        }

        if (camera != null) {

            Log.i(TAG, "Camera released in surfaceDestroyed");
            camera.release();

            camera = null;

        }

        finish(); // ??
    }

    static float clamp(float v, float mn, float mx) {
        return Math.min(Math.max(v, mn), mx);
    }

    private Runnable updateTimerThread = new Runnable() {
        public void run() {

            float st_secs = (SystemClock.uptimeMillis() - startTime) /* 1000.0f */;

            if( sharedPrefs.getBoolean("pref_pinbl", false) ) {

                if( st_secs >= preinfusionPump && st_secs <= preinfusionPump + preinfusionDelay )
                    st_secs = preinfusionPump;

                if( st_secs > preinfusionPump + preinfusionDelay )
                    st_secs -= preinfusionDelay;

            }

            st_secs /= 1000.0f;

            meter2.setValue( clamp( st_secs, 0, 45) );

            customHandler.postDelayed( this, 500 );

        }

    };

    // This handler checks the last time a message was received
    // If it was too long ago, the indicator will be switched to off
    // and the readings will be grayed?
    final Handler indicator_watchdog = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (System.currentTimeMillis() - last_message > 3000) {

                indicator.setImageResource(R.drawable.silvia_off);

                btemp.setText("---,-- °");
                setpoint.setText("---,-- °");

                meter1.setValue(0);

                // bConnection.reset();
            }
        }
    };

    // Bookkeep when the graph handles touch events: do not repaint when in touchevent
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {

        int eventaction = event.getAction();

        switch (eventaction) {
            case MotionEvent.ACTION_DOWN:
                // finger touches the screen
                lock_graph = true;

                break;

            case MotionEvent.ACTION_MOVE:
                // finger moves on the screen
                break;

            case MotionEvent.ACTION_UP:
                // finger leaves the screen

                lock_graph = false;

                break;
        }

        super.dispatchTouchEvent(event);

        return false;
    }

    private void ShotTimer_show() {
        final float scale = getBaseContext().getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams lp = meter1.getLayoutParams();

        lp.width = (int) (175 * scale);
        lp.height = (int) (175 * scale);

        meter1.setLayoutParams(lp);

        meter2.setVisibility(View.VISIBLE);
    }

    private void ShotTimer_hide() {
        final float scale = getBaseContext().getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams lp = meter1.getLayoutParams();

        meter2.setValue(0f);

        lp.width = (int) (200 * scale);
        lp.height = (int) (200 * scale);

        meter1.setLayoutParams(lp);

        meter2.setVisibility(View.GONE);
    }

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            String message = (String) msg.obj;

            if (message == null)
                return;

            String parts[] = message.split(" ");

            switch (msg.what) {
                case SPINNER_ON:
                    Log.i( TAG, "Showing spinner" );
                    progress_bar.setVisibility( View.VISIBLE );
                    break;
                case SPINNER_OFF:
                    Log.i( TAG, "Hiding spinner" );
                    progress_bar.setVisibility( View.GONE );
                    break;

                case DEVICE_ON:
                    // Change indicator to on
                    indicator.setImageResource(R.drawable.silvia_on);
                    setTitle("meBarista: " + (String) msg.obj);
                    break;
                case UPDATE:
                    mChartView.repaint();
                    break;

                case DEVICE_OFF:
                    // Change indicator to off
                    indicator.setImageResource(R.drawable.silvia_off);

                    // Clear all readings
                    break;

                case MESSAGE_TOAST:
                    Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
                    break;


                case MESSAGE_READ:
                    last_message = System.currentTimeMillis();

                    indicator.setImageResource(R.drawable.silvia_on);

                    if( logging_enabled ) {

                        log( message );

                    }

                    // Graph is not enabled?
                    if (findViewById( R.id.graph ) == null)
                        break;

                    if (message.startsWith("pid ")) {

                        try {

                            float res = Integer.parseInt(parts[1]) + Integer.parseInt(parts[2]) + Integer.parseInt(parts[3]);

                            if (parts.length >= 5)
                                res += Integer.parseInt(parts[4]);

                            res = res / 65535 * 100.0f;

                            meter1.setValue(Math.min(res, 100.0f));

                            //System.out.println( "updated graph" );

                            return;
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse PID message");
                        }

                    }

                    if (message.startsWith("dem ")) {
                        time_scale = Integer.parseInt(parts[1]);
                    }

                    if (message.startsWith("sht ")) {

                        /* try { */
                            int shottime = Integer.parseInt(parts[2]);

                            if (shottime == 0) {
                                meter2.setValue(0f);
                                ShotTimer_show();
                                startTime = SystemClock.uptimeMillis();

                                // Setting can be changed from multiple locations: get it here
                                preinfusionDelay = 0;
                                preinfusionPump = 0;
                                if (sharedPrefs.getBoolean("pref_pinbl", false)) {

                                    preinfusionPump = sharedPrefs.getFloat( "pref_pistrt", 0);
                                    preinfusionDelay = sharedPrefs.getFloat("pref_piprd", 0);

                                    if( mBoundService.legacy ) {
                                        preinfusionDelay = preinfusionDelay * 1000;
                                        preinfusionPump = preinfusionPump * 1000;
                                    }

                                }

                                System.out.println("PI Delay " + preinfusionDelay);

                                customHandler.postDelayed(updateTimerThread, 1000);
                            } else {
                                customHandler.removeCallbacks(updateTimerThread);

                                // if shottime <= pipump : check setting if to increase
                                // if shottime > pipump : check setting for piprd

                                System.out.println( "Shot timer update " + shottime );

                                // Init with normal use case : no PI
                                float st_secs = shottime;

                                // Adjust for PI
                                if( sharedPrefs.getBoolean("pref_pinbl", false) ) {

                                    if( st_secs >= preinfusionPump && st_secs <= preinfusionPump + preinfusionDelay )
                                        st_secs = preinfusionPump;

                                    if( st_secs > preinfusionPump + preinfusionDelay )
                                        st_secs -= preinfusionDelay;
                                }

                                // Convert millisecs to seconds
                                st_secs /= 1000.0f;

                                meter2.setValue( clamp(st_secs, 0, 45) );

                                // Set timer to hide gauge after 30 secs
                                customHandler.postDelayed(new Runnable() {
                                    public void run() {
                                        ShotTimer_hide();
                                    }
                                }, 30 * 1000);
                            }

                        /* } catch (Exception e) {
                            Log.e(TAG, "Failed to parse SHT message" + e.getMessage());
                        } */

                    }

                    if (message.startsWith("T ") || message.startsWith("tmp ")) {

                        try {


                            // renderer.setPanLimits( new double[] { 0, max( mBoundService.teller, 100 ), -10, 120 } );
                            if (!lock_graph &&
                                    mBoundService.teller > mBoundService.renderer.getXAxisMin() &&
                                    mBoundService.teller <= mBoundService.renderer.getXAxisMax() + 50) {
                                double cur_width = mBoundService.renderer.getXAxisMax() - mBoundService.renderer.getXAxisMin();

                                mBoundService.renderer.setXAxisMin(mBoundService.teller - cur_width);
                                mBoundService.renderer.setXAxisMax(mBoundService.teller);


                                //mBoundService.renderer.setXAxisMin( mBoundService.gdataset.getSeries()[0].getMinX() );
                                //mBoundService.renderer.setXAxisMax( mBoundService.gdataset.getSeries()[0].getMaxX() );
                            }

                            double[] panlimits = mBoundService.renderer.getPanLimits();
                            //panlimits[0] = mBoundService.gdataset.getSeries()[0].getMinX() - 30 ;
                            panlimits[1] = mBoundService.teller + 30;
                            mBoundService.renderer.setPanLimits(panlimits);

                            double[] zoomlimits = mBoundService.renderer.getZoomLimits();
                            //panlimits[0] = mBoundService.gdataset.getSeries()[0].getMinX() - 30 ;
                            zoomlimits[1] = Math.max(600, mBoundService.teller + 30);
                            mBoundService.renderer.setZoomLimits(zoomlimits);

                            double yrange = mBoundService.renderer.getYAxisMax() - mBoundService.renderer.getYAxisMin();

                            if ((int) yrange == 150)
                                mBoundService.renderer.setZoomEnabled(true, false);

                            String[] parts2 = message.replaceAll("T ", "").replaceAll("tmp ", "").replaceAll("\n", "").split(" ");

                            if (parts2.length >= 3) {
                                // Double.parseDouble(parts[1]) / 100.0
                                DecimalFormat f = new DecimalFormat("#0.00");
                                double temp = Double.parseDouble(parts2[2]) / 100.0;
                                double sp = Double.parseDouble(parts2[1]) / 100.0;

                                btemp.setText(f.format(temp) + " °");
                                setpoint.setText(f.format(sp) + " °");

                                curSetpoint = sp;
                            }

                            if (!lock_graph  /* &&
                            mBoundService.teller > mBoundService.renderer.getXAxisMin() &&
                            mBoundService.teller <= mBoundService.renderer.getXAxisMax() + 5  */)

                                mChartView.repaint();

                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse TMP message");
                        }
                    }
                    break;

                default:
                    break;
            }
        }
    };


    public void execute2(Context context) {
        // Don't create the graph if it is disabled in the preference
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sharedPref.getBoolean("pref_ui_graph_enable", true))
            return;

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.achart);
        mChartView = ChartFactory.getTimeChartView(this, mBoundService.gdataset, mBoundService.renderer, "mm:ss");
        mChartView.setId(R.id.graph);
        //mChartView.setVisibility(View.INVISIBLE);
        layout.addView(mChartView);

        //
        // , new LayoutParams(LayoutParams.MATCH_PARENT,
        //        LayoutParams.MATCH_PARENT, LayoutParams.));
    }

    private BaristaService mBoundService;
    private Messenger mBoundMsg;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((BaristaService.LocalBinder) service).getService();
            mBoundService.setHandler(mHandler, MainActivity.this);

            // Tell the user about this for our demo.
            //Toast.makeText(Binding.this, R.string.local_service_connected,
            //        Toast.LENGTH_SHORT).show();

            execute2(MainActivity.this);

            // mBoundService.fillChartWithDemoData();

            service_call( "scan" );
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            //Toast.makeText(Binding.this, R.string.local_service_disconnected,
            //        Toast.LENGTH_SHORT).show();
        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(MainActivity.this,
                BaristaService.class), mConnection, Context.BIND_AUTO_CREATE);
        //mIsBound = true;
    }

    void doUnbindService() {
        //if (mIsBound) {
        // Detach our existing connection.
        unbindService(mConnection);
        //    mIsBound = false;
        //}
    }

    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BaristaService.UPDATE:
                    // mCallbackText.setText("Received from service: " + msg.arg1);
                    mChartView.repaint();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler());

    @Override
    protected void onResume() {
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        background.setVisibility(sharedPrefs.getBoolean("pref_ui_background", true) ? View.VISIBLE : View.INVISIBLE);

        if (sharedPrefs.getBoolean("pref_ui_screenwake", false))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        logging_enabled = sharedPrefs.getBoolean("pref_ui_logging", false);

        if (mBoundService != null)
            mBoundService.showSecondSensor(sharedPrefs.getBoolean("pref_ui_second_sensor", false));

        super.onResume();
    }

    @Override
    protected void onDestroy() {

        Log.i( TAG, "onDestroy" );

        if( mBoundService != null && mBoundService.blehandler != null && mBoundService.blehandler.mBluetoothGatt != null ) {

            mBoundService.blehandler.mBluetoothGatt.disconnect();

            mBoundService.blehandler.mBluetoothGatt.close();

        }

        doUnbindService();

        Intent i = new Intent(this, BaristaService.class);
        getApplicationContext().stopService(i);

        if (sharedPrefs.getBoolean("pref_bt_disable", false)) {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter.isEnabled())
                mBluetoothAdapter.disable();
        }

        super.onDestroy();

    }

    private void prefTestFloat( SharedPreferences sharedPrefs, SharedPreferences.Editor editor, String key ) {

        try {

            float val = sharedPrefs.getFloat(key, -42);

        }
        catch( Exception e )  {

            editor.remove(key);

        }

    }

    protected void onCreate_Preferences() {

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPrefs.edit();

        PreferenceManager.setDefaultValues( getApplication(), R.xml.preference, true );

        // Android: boolean-preferences with their default set to 'false'
        // don't get initialized. DO IT OURSELVES
        // SOL: http://stackoverflow.com/questions/2874276/initialize-preference-from-xml-in-the-main-activity

        if (!sharedPrefs.contains("pref_pinbl"))
            editor.putBoolean("pref_pinbl", false);

        if (!sharedPrefs.contains("pref_tmpcntns"))
            editor.putBoolean("pref_tmpcntns", false);

        if (!sharedPrefs.contains("pref_grndr_cnt"))
            editor.putInt("pref_grndr_cnt", 0);

        if (!sharedPrefs.contains("pref_tmron"))
            editor.putInt("pref_tmron", 0);

        if (!sharedPrefs.contains("pref_tmroff"))
            editor.putInt("pref_tmroff", 0);

        if (!sharedPrefs.contains("pref_o0"))
            editor.putString("pref_o0", "112");

        if (!sharedPrefs.contains("pref_o1"))
            editor.putString( "pref_o1", "98" );

        if (!sharedPrefs.contains("pref_o2"))
            editor.putString( "pref_o2", "118" );

        prefTestFloat( sharedPrefs, editor, "pref_tmpsp" );
        prefTestFloat( sharedPrefs, editor, "pref_tmpstm" );
        prefTestFloat( sharedPrefs, editor, "pref_tmppap" );
        prefTestFloat( sharedPrefs, editor, "pref_pd1p" );
        prefTestFloat( sharedPrefs, editor, "pref_pd1i" );
        prefTestFloat( sharedPrefs, editor, "pref_pd1d" );

        prefTestFloat( sharedPrefs, editor, "pref_pd1imn" );
        prefTestFloat( sharedPrefs, editor, "pref_pd1imx" );

        editor.commit();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        onCreate_Preferences();

        super.onCreate(savedInstanceState);

        // Why is this here ?
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // deprecated : requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);

        logging_enabled = sharedPrefs.getBoolean("pref_ui_logging", false);

        setpoint = (TextView) findViewById(R.id.SetPoint);
        btemp = (TextView) findViewById(R.id.BTemp);
        indicator = (ImageView) findViewById(R.id.Indicator);
        logging = (TextView) findViewById(R.id.logging);
        background = (ImageView) findViewById(R.id.background);
        tempcontrol = (LinearLayout) findViewById(R.id.tempcontrol);
        video = findViewById(R.id.video);
        progress_bar = (ProgressBar) findViewById( R.id.progress );

        teller = 0;

        minbutton = (Button) findViewById(R.id.MinButton);
        minbutton.setOnClickListener(this);

        plusbutton = (Button) findViewById(R.id.PlusButton);
        plusbutton.setOnClickListener(this);

        meter1 = (Gauge) findViewById(R.id.power);
        meter2 = (Gauge) findViewById(R.id.shottimer);

        meter1.setTranslationX(20f);
        meter1.setTranslationY(-40f);

        meter2.setTranslationX(-165f + 20f);
        meter2.setTranslationY(-45f + -40f);

        tempcontrol.setTranslationY(-40f);

        // Against material design guidelines, ugly
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        //    getActionBar().setDisplayShowHomeEnabled(true);
        // }
        //getActionBar().setLogo(R.mipmap.ic_launcher);
        //getActionBar().setDisplayUseLogoEnabled(true);


        //meter1.setValue(-50);
        //meter1.setValue(-50);
        if (!sharedPrefs.getBoolean("pref_ui_background", true))
            background.setVisibility(View.INVISIBLE);
        //background.setImageDrawable( null );
        if (sharedPrefs.getBoolean("pref_ui_screenwake", false))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Enable Bluetooth when it is not enabled!
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if ( mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled() ) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // http://stackoverflow.com/questions/32708374/bluetooth-le-scanfilters-dont-work-on-android-m
        // http://developer.radiusnetworks.com/2015/09/29/is-your-beacon-app-ready-for-android-6.html

        final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so meBarista can communicate with your meCoffee in the background.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }

        }


        // Start the Barista background service
        Intent i = new Intent(this, BaristaService.class);
//         i.putExtra("action", "scan");

        getApplicationContext().startService(i);
        doBindService();

        handleintentTimer = new Timer();
        handleintentTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        handleintent();
                    }
                });
            }

        }, 1000);

        // Start watchdog timer to detect if connection is lost
        indicator_watchdog_timer = new Timer();
        indicator_watchdog_timer.schedule(new TimerTask() {
            @Override
            public void run() {
                indicator_watchdog.sendEmptyMessage(0);
            }
        }, 0, 1000);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            ScreenRecording_onCreate();

        }
        else
        {
            MenuItem item = (MenuItem) findViewById(R.id.menu_item_video);
            if( item != null )
                item.setVisible(false);
            this.invalidateOptionsMenu();
        }

        setProgressBarIndeterminateVisibility(true);

        /* TODO: switch to specific function
           add menu item

        installation_manual = (WebView) findViewById(R.id.installation_manual);
        WebSettings webSettings = installation_manual.getSettings();
        webSettings.setJavaScriptEnabled(true);

        installation_manual.setWebViewClient(new WebViewClient());

        installation_manual.loadUrl( "https://mecoffee.nl/mecoffee/installation/?embedded=true" );
        */




    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);

        return true;
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.MinButton:

                mBoundService.write(("cmd set tmpsp " + (int) ((curSetpoint - 0.5) * 100) + "\n")); // TODO: remove nl

                break;

            case R.id.PlusButton:

                mBoundService.write(("cmd set tmpsp " + (int) ((curSetpoint + 0.5) * 100) + "\n")); // TODO: remove nl

                break;

            default:
                break;

        }

    }

    // Installation manual

    private boolean IM_Open( ) {

        /*


        if( installation_manual == null ) {
            installation_manual = (WebView) findViewById(R.id.installation_manual);
            WebSettings webSettings = installation_manual.getSettings();
            webSettings.setJavaScriptEnabled(true);

            installation_manual.setWebViewClient(new WebViewClient());

            installation_manual.loadUrl("https://mecoffee.nl/mecoffee/installation/?embedded=true");
        }

        installation_manual.setVisibility(WebView.VISIBLE);
        meter1.setVisibility(WebView.INVISIBLE);
        meter2.setVisibility(WebView.INVISIBLE);
//        logging.setVisibility(WebView.INVISIBLE);

        btemp.setTextColor(Color.BLACK);
        setpoint.setTextColor(Color.BLACK);
        //minbutton.setTextColor(Color.BLACK); ziet er niet uit
        //plusbutton.setTextColor(Color.BLACK);

*/

        return true;
    }

    private boolean IM_Close( ) {

        installation_manual.setVisibility( WebView.INVISIBLE );

        meter1.setVisibility(WebView.VISIBLE);

        btemp.setTextColor(Color.WHITE);
        setpoint.setTextColor(Color.WHITE);
        //minbutton.setTextColor(Color.WHITE); ziet er niet uit
        //plusbutton.setTextColor(Color.WHITE);

        return true;

    }

    private boolean rest_post() {
        Toast.makeText(MainActivity.this, "Please wait for your upload to finish...", Toast.LENGTH_LONG).show();

        String mecoffee_url = "Something went wrong with uploading the file...";
        // http://stackoverflow.com/questions/2793150/using-java-net-urlconnection-to-fire-and-handle-http-requests

        try {
            String param = "value";
            String charset = "utf-8";
            String boundary = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.
            String CRLF = "\r\n"; // Line separator required by multipart/form-data.
            URLConnection connection = new URL("https://mecoffee.nl/share/post.php").openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            OutputStream output = connection.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);

            // Send normal param.
            writer.append("--" + boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"param\"").append(CRLF);
            writer.append("Content-Type: text/plain; charset=" + charset).append(CRLF);
            writer.append(CRLF).append(param).append(CRLF).flush();

            // Send text file.
            writer.append("--" + boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"logfile\"; filename=\"" + "logfile" + "\"").append(CRLF);
            writer.append("Content-Type: text/plain; charset=" + charset).append(CRLF); // Text file itself must be saved in this charset!
            writer.append(CRLF).flush();

            // Files.copy(textFile.toPath(), output);
            output.write(mBoundService.log.toString().getBytes());

            output.flush(); // Important before continuing with writer!
            writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.

            // End of multipart/form-data.
            writer.append("--" + boundary + "--").append(CRLF).flush();

            // connection.connect();

            InputStream in = connection.getInputStream();


            BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset));

            for (String line; (line = reader.readLine()) != null; ) {
                mecoffee_url = line; //System.out.println(line);
            }

        } catch (Exception e) {
            System.out.println("ex" + e.getMessage());
        }

        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "My meCoffee log...");
        i.putExtra(Intent.EXTRA_TEXT, "See my espresso log at " + mecoffee_url + "\n\n");

        try {
            startActivity(Intent.createChooser(i, "Share your meCoffee experience..."));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(MainActivity.this, "There are clients installed.", Toast.LENGTH_SHORT).show();
        }


        return false;
    }

    public void processRecording() {
        String file_location = "/sdcard/Download/screenrecording.mp4";

        // http://stackoverflow.com/questions/18603524/share-intent-does-not-work-for-uploading-video-to-youtube
        // http://stackoverflow.com/questions/15965246/how-to-upload-video-to-youtube-in-android

        MediaScannerConnection.scanFile(this, new String[]{file_location}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Log.d(TAG, "onScanCompleted uri " + uri);

                        Intent shareIntent = new Intent(
                                Intent.ACTION_SEND);
                        shareIntent.setType("video/*");
                        shareIntent.putExtra(
                                Intent.EXTRA_SUBJECT, "meCoffee in action on %s");
                        shareIntent.putExtra(
                                Intent.EXTRA_TITLE, "meCoffee in action to %s");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                        shareIntent
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                        startActivity(Intent.createChooser(shareIntent,
                                        /* getString(R.string.str_share_this_video) */ "Do you want to share your video ?"));

                    }
                });
    }


    // https://developer.android.com/training/permissions/requesting.html

    final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    final int PERMISSION_REQUEST_CAMERA = 2;
    final int PERMISSION_REQUEST_MICROPHONE = 3;
    final int PERMISSION_REQUEST_STORAGE = 4;

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    Log.i(TAG, "Permission Coarse Location granted.");

                    // TODO:

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            case PERMISSION_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    Log.i(TAG, "Permission Camera granted.");

                    do_the_permission_dance();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            case PERMISSION_REQUEST_MICROPHONE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    Log.i(TAG, "Permission Microphone granted.");

                    do_the_permission_dance();

                    // TODO:

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            case PERMISSION_REQUEST_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    Log.i(TAG, "Permission Storage granted, starting video from onRequestPermissionsResult.");

                    // This is the last permission to be granted to video recording
                    // It is the only way to get here too: start video recording

                    cameraToggle();


                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public boolean do_the_permission_dance() {

        boolean we_are_good = true;


        // Android M Permission check 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // Camera
            if (this.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                we_are_good = false;

                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("To display video, meBarista needs access to your camera");
                builder.setMessage("Please grant camera access so meBarista can continue into augmented reality.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);

                        // do_the_permission_dance();
                    }
                });
                builder.show();

                return we_are_good;
            }

            // Microphone
            if (this.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

                we_are_good = false;

                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("To add sound to your video, meBarista needs access to your microphone");
                builder.setMessage("Please grant microphone access so meBarista can hear your delicious espresso being poured into your cup.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_MICROPHONE);

                        // do_the_permission_dance();
                    }
                });
                builder.show();

                return we_are_good;
            }

            // Storage
            if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                we_are_good = false;

                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("To save or share your video, meBarista needs access to your storage");
                builder.setMessage("Please grant storage access so meBarista can get on your cloud.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_STORAGE);
                    }
                });
                builder.show();

                return we_are_good;
            }

        }

        return we_are_good;

    }

    public boolean cameraToggle() {

        if (!do_the_permission_dance())
            return false;

        // Start and stop video preview & screen recording

        if ( cameraToggleStatus == true ) {

            Log.i(TAG, "Video stop");

            stopScreenSharing();

            if( camera != null ) {
                camera.setPreviewCallback(null);
                camera.stopPreview();
            }

            if (camera != null) {

                Log.i(TAG, "Camera released by GUI action");

                // If you don't remove the callback, the activity will minimize
                holder.removeCallback(this);

                camera.release();

                camera = null;

            }

            background.setVisibility(View.VISIBLE);
            video.setVisibility(View.INVISIBLE);

            processRecording();

            cameraToggleStatus = false;

            return true;
        }

        Log.i(TAG, "Video start");
        background.setVisibility(View.INVISIBLE);
        video.setVisibility(View.VISIBLE);

        SurfaceView cameraView = (SurfaceView) video;
        holder = cameraView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        camera = Camera.open(); // TODO: try java.lang.RuntimeException

        if( camera != null ) {

            try {
                camera.setPreviewDisplay(holder);
            } catch (IOException e) {
                e.printStackTrace();
            }

            camera.startPreview();

        }

        shareScreen();

        cameraToggleStatus = true;

        return true;

    }

    private void service_call( String s ) {

        Intent i = new Intent(this, BaristaService.class);
        i.putExtra("action", s );
        startService(i);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {


            //case R.id.menu_item_screenrecord:
            //    Log.i(TAG, "Toggle screenrecord");

            //    if( mScreenSharing )
            //        stopScreenSharing();
            //    else
            //        shareScreen();

            //    break;

            case R.id.menu_item_video:
                // Request permissions, bail out if not met

                cameraToggle();

                break;

            case R.id.menu_settings:

                Intent a = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(a);
                break;

            case R.id.menu_scan:

                Intent i2 = new Intent(this, BaristaService.class);
                i2.putExtra("action", "scan");
                startService(i2);
                break;

            case R.id.menu_fw_reset:

                service_call( "fw_reset" );
                break;

            case R.id.menu_fw_stop:

                service_call("fw_stop");
                break;

            case R.id.menu_fw_start:

                service_call("fw_start");
                break;

            case R.id.menu_fw_close:

                service_call("fw_close");
                break;

            case R.id.menu_fw_open:

                service_call("fw_open");
                break;

            case R.id.menu_fw_flash:

                service_call("fw_flash");
                break;

            case R.id.menu_demo:

                mBoundService.fillChartWithDemoData();

                // Set start viewport for demo
                mBoundService.renderer.setXAxisMin(0);
                mBoundService.renderer.setXAxisMax(600);

                mBoundService.renderer.setYAxisMin(10);
                mBoundService.renderer.setYAxisMax(140);

                mChartView.repaint();
                break;

            case R.id.menu_item_share:
                rest_post();

                break;
/*
            case R.id.menu_im:

                if( installation_manual == null || installation_manual.getVisibility() != WebView.VISIBLE ) {
                    IM_Open();

                    item.setTitle( "Back to meBarista" );
                }
                else {
                    IM_Close( );
                    item.setTitle( "Installation manual" );
                }

                break;
*/

        }

        return true;
    }

    public void log(String line) {
        final String out = line;

        //if( ! ( out.contains("address") || out.contains("Address") )  )
        //    return ;
        //if( !out.contains( "Memory address to load" ) )
        //    return ;

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                logging.setText((out + "\n" + logging.getText()).substring(0, Math.min(logging.getText().length() + out.length() + 1, 250)));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {

            case REQUEST_ENABLE_BT:
                break;

            case SCREEN_RECORDING_REQUEST_CODE:
                Log.i(TAG_SC, "onActivityResult: getting MediaProjection");

                // So we got permission: get the MediaProjection
                mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);

                // and run shareScreen again, which will continue this time
                shareScreen();

                break;


            default:
                break;
        }
    }

    // Implement zoomListener
    public void zoomReset() {

    }

    public void zoomApplied(ZoomEvent e) {
        if (e.isZoomIn()) {
            mBoundService.renderer.setZoomEnabled(true, true);
            mBoundService.renderer.setYAxisMax(140);
            mBoundService.renderer.setYAxisMin(10);
        }
    }

    void handleintent() {

        // Check if we were started with a specific intent:
        Uri intent_uri = getIntent().getData();

        if (intent_uri == null)
            return;

        String datastr = intent_uri.toString();

        if (datastr == null)
            return;

        if (datastr.equals("supportticket")) {
            supportticket();
            this.setIntent(null);
        }

        if (datastr.equals("flashfirmware_V4")) {
            setIntent( null );
            // http://stackoverflow.com/questions/4116110/clearing-intent/38864614#38864614
            flashfirmware( "meCoffee-V4.hex" );
        }

        if (datastr.equals("flashfirmware_V9")) {
            setIntent( null );
            // http://stackoverflow.com/questions/4116110/clearing-intent/38864614#38864614
            flashfirmware( "meCoffee-V9.hex" );
        }

        if (datastr.equals("flashfirmware_V10")) {
            setIntent( null );
            // http://stackoverflow.com/questions/4116110/clearing-intent/38864614#38864614
            flashfirmware( "meCoffee-V10.hex" );
        }

        return;
    }

    void supportticket() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("message/rfc822");
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{sharedPref.getString("pref_support_email", "")});
        i.putExtra(Intent.EXTRA_SUBJECT, "My espresso machine is acting weird...");
        i.putExtra(Intent.EXTRA_TEXT, "can you take a look?\n\nkind regards,\n\n");

        // Take screenshot
        RelativeLayout page = (RelativeLayout) findViewById(R.id.root2);
        page.setDrawingCacheEnabled(true);
        page.buildDrawingCache();
        Bitmap bmp = Bitmap.createBitmap(page.getDrawingCache(true));
        page.destroyDrawingCache();
        page.setDrawingCacheEnabled(false);

        File file = new File(getApplicationContext().getFilesDir(), "screenshot.png");
        file.setReadable(true, false);

        if (file.exists()) {
            file.delete();
            file = new File(getApplicationContext().getFilesDir(), "screenshot.png");
            file.setReadable(true, false);
            //Log.e("file exist", "" + file + ",Bitmap= " + filename);
        }

        try {
            // FileOutputStream out = new FileOutputStream(file);
            FileOutputStream out = openFileOutput("screenshot.png", Context.MODE_WORLD_READABLE);
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));

        try {
            startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(MainActivity.this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
        }

        return;
    }

    void flashfirmware( String firmware_file ) {
        Intent i_fw = new Intent(this, BaristaService.class);

// moet dit wel?        mBoundService.stop();

        logging.setText("firmware intent");

        i_fw.putExtra("action", "firmware");
        i_fw.putExtra( "firmware_file", firmware_file );

        startService(i_fw);
    }

    // Screen recording

    void ScreenRecording_onCreate() {

        if (mSurfaceView != null)
            return;

        // From the onCreate
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        // mSurfaceView = (SurfaceView) findViewById(R.id.screenrecording);
        mSurfaceView = new SurfaceView(this);
        mSurface = mSurfaceView.getHolder().getSurface();
        mProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // From ResolutionSelector
        Resolution r = RESOLUTIONS.get(0); // (Resolution) parent.getItemAtPosition(pos);
        ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();

        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            mDisplayHeight = r.y;
            mDisplayWidth = r.x;
        } else {
            mDisplayHeight = r.x;
            mDisplayWidth = r.y;
        }

        if (false) {
            lp.height = mDisplayHeight;
            lp.width = mDisplayWidth;
            mSurfaceView.setLayoutParams(lp);

        }
    }

    private void shareScreen() {

        if (mSurface == null) {
            return;
        }

        if (mMediaProjection == null) {
            // startActivityForResult( mProjectionManager.getScreenCaptureIntent(), PERMISSION_CODE );
            // Log.i( TAG, "Started Screen Capture" );

            Log.i(TAG_SC, "Request permission for MediaProjection");
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), SCREEN_RECORDING_REQUEST_CODE);

            return;
        }

        Log.i(TAG_SC, "Started");
        mScreenSharing = true;

        // TODO: not sure if belongs here
        ScreenRecording_initRecorder();

        mVirtualDisplay = createVirtualDisplay();
        Log.i(TAG_SC, "Start MediaRecorder");
        mMediaRecorder.start();
    }

    private void stopScreenSharing() {
        Log.i(TAG_SC, "Stopped");

        mScreenSharing = false;
        if (mVirtualDisplay == null) {
            return;
        }

        Log.i(TAG_SC, "Stop MediaRecorder");
        try {
            mMediaRecorder.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mMediaRecorder.reset();
        mMediaRecorder = null;

        mVirtualDisplay.release();
        mVirtualDisplay = null;

    }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("ScreenSharingDemo",
                mDisplayWidth, mDisplayHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null /*Handler*/);
    }

    private void ScreenRecording_initRecorder() {
        try {
            mMediaRecorder = new MediaRecorder();

            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setOutputFile(Environment
                    .getExternalStoragePublicDirectory(Environment
                            .DIRECTORY_DOWNLOADS) + "/screenrecording.mp4");

            Log.i(TAG_SC, "Dimensions: " + mDisplayWidth + "x" + mDisplayHeight);
            mMediaRecorder.setVideoSize(mDisplayWidth, mDisplayHeight);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaRecorder.setVideoEncodingBitRate(1024 * 1000);
            mMediaRecorder.setVideoFrameRate(30);
            //int rotation = getWindowManager().getDefaultDisplay().getRotation();
            //int orientation = ORIENTATIONS.get(rotation + 90);
            //mMediaRecorder.setOrientationHint(orientation);
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
