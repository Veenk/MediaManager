package com.example.test;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SlidingDrawer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJICameraError;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.FetchMediaTaskScheduler;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

import static com.example.test.MainActivity.FLAG_CONNECTION_CHANGE;

public class Media extends AppCompatActivity {
    private static final String TAG = Media.class.getName();
    private Button syncButton, backButton, refreshButton;


    private List<MediaFile> mediaFileList = new ArrayList<MediaFile>();

    private MediaManager mMediaManager;
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;
    private FetchMediaTaskScheduler scheduler;
    private ProgressDialog mLoadingDialog;
    private ProgressDialog mDownloadDialog;
    private Handler mHandler;
    private int lastProcess = -1;
    private ProgressBar progressBar;
    private File destDir;
    private Handler mHander = new Handler();
    private Thread t1;
    private int currIndex;


    private int currentProgress = -1;

    private int lastClickViewIndex =-1;
    private View lastClickView;
    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;
    private SharedPreferences sPref;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private static BaseProduct mProduct;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
//            setTurnScreenOn(true);
//            setShowWhenLocked(true);
//        } else {
//            Window window = getWindow();
//            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
//            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
//        }

        // When the compile and target version is higher than 22, please request the following permission at runtime to ensure the SDK works well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkAndRequestPermissions();
        }


//        boolean success = false;
//        if(!destDir.exists()) {
//            success = destDir.mkdirs();
//        }
//        if (success){
//            System.out.println("YEAH");
//        }

//        try {
//            File [] filelist = destDir.listFiles();
//            if (null != filelist) {
//                for (File f : filelist) {
//                    setResultToToast("Testing Filename : " + f.getName());
//                }
//            }
//        }catch(NullPointerException e){
//            System.out.println(e.getMessage());
//        }
        mHandler = new Handler(Looper.getMainLooper());
        setContentView(R.layout.file_manager_view);
        initUI();
        System.out.println("Testing iniUI()");
        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                t1 = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        while(running.get()) {
                            initMediaManager();
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ie) {

                            }
                        }
                    }
                });
                if (t1.isAlive()){
                    running.set(false);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            syncButton.setText("Start Sync");
                            syncButton.setBackgroundColor(getResources().getColor(R.color.colorGreen));
                        }
                    });

                    lastClickView = null;
                    if (mMediaManager != null) {
                        mMediaManager.stop(null);
                        mMediaManager.removeFileListStateCallback(new MediaManager.FileListStateListener() {
                            @Override
                            public void onFileListStateChange(MediaManager.FileListState state) {
                                currentFileListState = state;
                            }
                        });
                        mMediaManager.exitMediaDownloading();
                        if (scheduler!=null) {
                            scheduler.removeAllTasks();
                        }
                    }
                    getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError mError) {
                            if (mError != null){
                                setResultToToast("Set Shoot Photo Mode Failed" + mError.getDescription());
                            }
                        }
                    });
                    if (mediaFileList != null) {
                        mediaFileList.clear();
                    }
                }else {
                    running.set(true);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            syncButton.setText("Stop sync");
                            syncButton.setBackgroundColor(getResources().getColor(R.color.colorRed));
                        }
                    });
                    t1.start();
                }

            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (t1.isAlive()){
                    running.set(false);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            syncButton.setText("Start Sync");
                            syncButton.setBackgroundColor(getResources().getColor(R.color.colorGreen));
                        }
                    });
//                    try {
//                        t1.join();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
                    lastClickView = null;
                    if (mMediaManager != null) {
                        mMediaManager.stop(null);
                        mMediaManager.removeFileListStateCallback(new MediaManager.FileListStateListener() {
                            @Override
                            public void onFileListStateChange(MediaManager.FileListState state) {
                                currentFileListState = state;
                            }
                        });
                        mMediaManager.exitMediaDownloading();
                        if (scheduler!=null) {
                            scheduler.removeAllTasks();
                        }
                    }
                    getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError mError) {
                            if (mError != null){
                                setResultToToast("Set Shoot Photo Mode Failed" + mError.getDescription());
                            }
                        }
                    });
                    if (mediaFileList != null) {
                        mediaFileList.clear();
                    }
                }
            }
        });


    }
    private void saveDate(String date) {
        sPref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor ed = sPref.edit();
        ed.putString("latest date so far", date);
        ed.apply();
    }
    private void savePrevDate(String date){
        sPref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor ed = sPref.edit();
        ed.putString("previous date", date);
        ed.apply();
    }
    private String loadPrevDate(){
        sPref = getPreferences(MODE_PRIVATE);
        String savedDate = sPref.getString("previous date", "");
        return savedDate;
    }

    private String loadDate() {
        sPref = getPreferences(MODE_PRIVATE);
        String savedDate = sPref.getString("latest date so far", "");
        return savedDate;
    }
    @Override
    protected void onDestroy() {
//        if (mediaFileList.size() != 0) {
//            raisePrevMediaFileDate();
//        }
        lastClickView = null;
        if (mMediaManager != null) {
            mMediaManager.stop(null);
            mMediaManager.removeFileListStateCallback(this.updateFileListStateListener);
//            mMediaManager.removeMediaUpdatedVideoPlaybackStateListener(updatedVideoPlaybackStateListener);
            mMediaManager.exitMediaDownloading();
            if (scheduler!=null) {
                scheduler.removeAllTasks();
            }
        }
        getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError mError) {
                if (mError != null){
                    setResultToToast("Set Shoot Photo Mode Failed" + mError.getDescription());
                }
            }
        });

        if (mediaFileList != null) {
            mediaFileList.clear();
        }
        super.onDestroy();
    }

    void initUI() {
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        //Init Loading Dialog
        mLoadingDialog = new ProgressDialog(Media.this);
        mLoadingDialog.setMessage("Please wait");
        mLoadingDialog.setCanceledOnTouchOutside(false);
        mLoadingDialog.setCancelable(false);

        //Init Download Dialog
        mDownloadDialog = new ProgressDialog(Media.this);
        mDownloadDialog.setTitle("Downloading file");
        mDownloadDialog.setIcon(android.R.drawable.ic_dialog_info);
        mDownloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDownloadDialog.setCanceledOnTouchOutside(false);
        mDownloadDialog.setCancelable(true);
        mDownloadDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (mMediaManager != null) {
                    mMediaManager.exitMediaDownloading();
                }
            }
        });
        syncButton = findViewById(R.id.syncButton);
        backButton = findViewById(R.id.backButton);
        refreshButton = findViewById(R.id.refreshButton);
    }
    private List<MediaFile> checkForNewestMedia(){

        List<MediaFile> newMediaFileList = new ArrayList<MediaFile>();
//        String path = Environment.getExternalStorageDirectory().toString()+"/MediaManagerFiles/";
//        File directory = new File(path);
//        File[] files = directory.listFiles();
//        boolean b = false;
        System.out.println("Testing checkFNM "+mediaFileList.size());
        for (int i = 0; i < mediaFileList.size(); i++){
            if (!loadDate().equals(mediaFileList.get(i).getDateCreated())){
                System.out.println("Testing "+loadDate()+"<"+mediaFileList.get(i).getDateCreated());
                newMediaFileList.add(i,mediaFileList.get(i));
            }
            else{
                break;
            }
        }
        System.out.println("Testing"+newMediaFileList.size());
        return newMediaFileList;
    }

    private static class Comparators {
        private static final Comparator<MediaFile> NAME = (MediaFile o1, MediaFile o2) -> o1.getFileName().compareTo(o2.getFileName());
        private static final Comparator<MediaFile> TIME = (MediaFile o1, MediaFile o2) -> Long.compare(o1.getTimeCreated(), o2.getTimeCreated());
//        public static final Comparator<MediaFile> NAMEANDAGE = (MediaFile o1, MediaFile o2) -> NAME.thenComparing(TIME).compare(o1, o2);
    }
    private void raiseCurrMediaFileDate(){
        saveDate(mediaFileList.get(currIndex).getDateCreated());
    }

    private void downloadFileByIndex(){
        System.out.println("Testing mediaFileList has: "+mediaFileList.size());
        for (int index = mediaFileList.size()-1; index > -1; index--) {
            currIndex = index;
            if ((mediaFileList.get(index).getMediaType() == MediaFile.MediaType.PANORAMA)
                    || (mediaFileList.get(index).getMediaType() == MediaFile.MediaType.SHALLOW_FOCUS)) {
                System.out.println("Testing idk");
                return;
            }
            destDir = new File(Environment.getExternalStorageDirectory().getPath() + "/MediaManagerFiles/");
//            final String date = mediaFileList.get(0).getDateCreated();
//            if (index == mediaFileList.size()-1){
//                saveDate("");
//            } else{
//                saveDate(mediaFileList.get(index + 1).getDateCreated());
//            }
            mediaFileList.get(index).fetchFileData(destDir, null, new DownloadListener<String>() {
                @Override
                public void onFailure(DJIError error) {
                    HideDownloadProgressDialog();
                    setResultToToast("Testing Download File Failed" + error.getDescription());

                    currentProgress = -1;


                }

                @Override
                public void onProgress(long total, long current) {
                }

                @Override
                public void onRateUpdate(long total, long current, long persize) {
                    int tmpProgress = (int) (1.0 * current / total * 100);
                    if (tmpProgress != currentProgress) {
                        mDownloadDialog.setProgress(tmpProgress);
                        currentProgress = tmpProgress;
                    }
                }

                @Override
                public void onStart() {
                    System.out.println("Testing starting download");
                    currentProgress = -1;
                    ShowDownloadProgressDialog();
                }

                @Override
                public void onSuccess(String filePath) {
                    raiseCurrMediaFileDate();
                    HideDownloadProgressDialog();
                    setResultToToast("Download File Success" + ":" + filePath);
                    currentProgress = -1;
                }
            });
        }
    }
    private void initMediaManager() {

        System.out.println("Testing inside initMM");
        if (getProductInstance() == null) {
            System.out.println("Testing no product!");
            mediaFileList.clear();
            DJILog.e(TAG, "Testing Product disconnected");
            return;
        } else {
            if (null != getCameraInstance() && getCameraInstance().isMediaDownloadModeSupported()) {
                System.out.println("Testing Camera found");
                mMediaManager = getCameraInstance().getMediaManager();
                if (null != mMediaManager) {
                    System.out.println("Testing Media Manager found");
                    mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);
                    getCameraInstance().setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError error) {
                            if (error == null) {
                                System.out.println("Testing Camera Mode success");
                                DJILog.e(TAG, "Set cameraMode success");
                                showProgressDialog();
                                getFileList();
                                System.out.println("Testing got file list");
                            } else {
                                setResultToToast("Set cameraMode failed");
                                System.out.println("Testing cameraMOde failed");
                            }
                        }
                    });
                    if (mMediaManager.isVideoPlaybackSupported()) {
                        DJILog.e(TAG, "Camera support video playback!");
                    } else {
                        setResultToToast("Camera does not support video playback!");
                    }
                    //remove scheduler
                    scheduler = mMediaManager.getScheduler();
                    //remove scheduler
                }

            } else if (null != getCameraInstance()
                    && !getCameraInstance().isMediaDownloadModeSupported()) {
                setResultToToast("Media Download Mode not Supported");
            }
        }
        return;
    }

    private void getFileList() {
        mMediaManager = getCameraInstance().getMediaManager();
        System.out.println("Testing"+mMediaManager.toString());
        if (mMediaManager != null) {

            if ((currentFileListState == MediaManager.FileListState.SYNCING) || (currentFileListState == MediaManager.FileListState.DELETING)) {
                System.out.println("Testing MM is busy");
                DJILog.e(TAG, "Media Manager is busy.");
            } else {
                System.out.println("Testing MM is not busy");
                mMediaManager.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.SDCARD, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (null == djiError) {
                            hideProgressDialog();
                            System.out.println("Testing refreshFLSL");
                            //Reset data
                            if (currentFileListState != MediaManager.FileListState.INCOMPLETE) {
                                mediaFileList.clear();
                                lastClickViewIndex = -1;
                                lastClickView = null;
                            }

                            mediaFileList = mMediaManager.getSDCardFileListSnapshot();
                            System.out.println("Testing"+mediaFileList.size());
                            setResultToToast("Tesing SIZE OF MEDIALIST IS: " + mediaFileList.size());
                            Collections.sort(mediaFileList, new Comparator<MediaFile>() {
                                @Override
                                public int compare(MediaFile lhs, MediaFile rhs) {
                                    if (lhs.getTimeCreated() < rhs.getTimeCreated()) {
                                        return 1;
                                    } else if (lhs.getTimeCreated() > rhs.getTimeCreated()) {
                                        return -1;
                                    }
                                    return 0;
                                }
                            });

//                            System.out.println("Testing "+mediaFileList.get(0).getFileName() + ' '+mediaFileList.get(mediaFileList.size()-1).getFileName());
                            mediaFileList = checkForNewestMedia();

                            downloadFileByIndex();

//                            scheduler.resume(new CommonCallbacks.CompletionCallback() {
//                                @Override
//                                public void onResult(DJIError error) {
//                                    if (error == null) {
//                                        getThumbnails();
//                                    }
//                                }
//                            });
                        } else {
                            hideProgressDialog();

                            System.out.println("Testing get media file list failed"+ djiError);
                            setResultToToast("Get Media File List Failed:" + djiError.getDescription());
                        }
                    }
                });

            }
        }else{
            System.out.println("Testing mediamanager is null");
        }
    }

    private void deleteFileByIndex(final int index) {
        ArrayList<MediaFile> fileToDelete = new ArrayList<MediaFile>();
        if (mediaFileList.size() > index) {
            fileToDelete.add(mediaFileList.get(index));
            mMediaManager.deleteFiles(fileToDelete, new CommonCallbacks.CompletionCallbackWithTwoParam<List<MediaFile>, DJICameraError>() {
                @Override
                public void onSuccess(List<MediaFile> x, DJICameraError y) {
                    DJILog.e(TAG, "Delete file success");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            MediaFile file = mediaFileList.remove(index);

                            //Reset select view
                            lastClickViewIndex = -1;
                            lastClickView = null;

                        }
                    });
                }

                @Override
                public void onFailure(DJIError error) {
                    setResultToToast("Delete file failed");
                }
            });
        }
    }


    public static synchronized BaseProduct getProductInstance() {
        if (null == mProduct) {
            mProduct = DJISDKManager.getInstance().getProduct();
            if(mProduct != null)System.out.println("mProduct is:"+mProduct.getModel().toString());
        }
        return mProduct;
    }

    public static synchronized Camera getCameraInstance() {

        if (getProductInstance() == null) return null;

        Camera camera = null;
        if (getProductInstance() instanceof Aircraft){
            camera = ((Aircraft) getProductInstance()).getCamera();
            System.out.println("Testing camera instance"+camera.isMediaDownloadModeSupported());
        }
        return camera;
    }
    private MediaManager.FileListStateListener updateFileListStateListener = new MediaManager.FileListStateListener() {
        @Override
        public void onFileListStateChange(MediaManager.FileListState state) {
            currentFileListState = state;
        }
    };

    private void setResultToToast(final String result) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast toast = Toast.makeText(Media.this, result, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL, 0, 0);
                toast.show();
            }
        });
    }
    private void showProgressDialog() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (mLoadingDialog != null) {
                    mLoadingDialog.show();
                }
            }
        });
    }

    private void hideProgressDialog() {

        runOnUiThread(new Runnable() {
            public void run() {
                if (null != mLoadingDialog && mLoadingDialog.isShowing()) {
                    mLoadingDialog.dismiss();
                }
            }
        });
    }

    private void ShowDownloadProgressDialog() {
        if (mDownloadDialog != null) {
            runOnUiThread(new Runnable() {
                public void run() {
                    mDownloadDialog.incrementProgressBy(-mDownloadDialog.getProgress());
                    mDownloadDialog.show();
                }
            });
        }
    }

    private void HideDownloadProgressDialog() {

        if (null != mDownloadDialog && mDownloadDialog.isShowing()) {
            runOnUiThread(new Runnable() {
                public void run() {
                    mDownloadDialog.dismiss();
                }
            });
        }
    }
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setResultToToast("Need to grant the permissions!");
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }

    }
    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    setResultToToast("registering, pls wait...");
                    DJISDKManager.getInstance().registerApp(Media.this.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                setResultToToast("Register Success");
                                DJISDKManager.getInstance().startConnectionToProduct();
                                showDBVersion();
                            } else {
                                setResultToToast("Register sdk fails, please check the bundle id and network connection!");
                            }
                            Log.v(TAG, djiError.getDescription());
                            hideProcess();
                        }

                        @Override
                        public void onProductDisconnect() {
                            Log.d(TAG, "onProductDisconnect");
                            setResultToToast("Product Disconnected");
                            notifyStatusChange();

                        }
                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {
                            Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                            setResultToToast("Product Connected");
                            notifyStatusChange();

                        }
                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                                      BaseComponent newComponent) {

                            if (newComponent != null) {
                                newComponent.setComponentListener(new BaseComponent.ComponentListener() {

                                    @Override
                                    public void onConnectivityChange(boolean isConnected) {
                                        Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
                                        notifyStatusChange();
                                    }
                                });
                            }
                            Log.d(TAG,
                                    String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                            componentKey,
                                            oldComponent,
                                            newComponent));
                            notifyStatusChange();

                        }
                        @Override
                        public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

                        }

                        @Override
                        public void onDatabaseDownloadProgress(long current, long total) {

                            int process = (int) (100 * current / total);
                            if (process == lastProcess) {
                                return;
                            }
                            lastProcess = process;
                            showProgress(process);
                            if (process % 25 == 0){
                                setResultToToast("DB load process : " + process);
                            }else if (process == 0){
                                setResultToToast("DB load begin");
                            }
                        }
                    });
                }
            });
        }
    }
    private void showDBVersion(){
        mHander.postDelayed(new Runnable() {
            @Override
            public void run() {
                DJISDKManager.getInstance().getFlyZoneManager().getPreciseDatabaseVersion(new CommonCallbacks.CompletionCallbackWith<String>() {
                    @Override
                    public void onSuccess(String s) {
                        setResultToToast("db load success ! version : " + s);
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        setResultToToast("db load success ! get version error : " + djiError.getDescription());

                    }
                });
            }
        },1000);
    }

    private void hideProcess(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
            }
        });
    }
    private void showProgress(final int process){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(process);
            }
        });
    }
    private void notifyStatusChange() {
        mHandler.removeCallbacks(updateRunnable);
        mHandler.postDelayed(updateRunnable, 500);
    }

    private Runnable updateRunnable = new Runnable() {

        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            sendBroadcast(intent);
        }
    };

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            setResultToToast("Missing permissions!!!");
        }
    }


}
