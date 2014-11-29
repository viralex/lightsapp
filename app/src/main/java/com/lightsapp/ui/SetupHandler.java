package com.lightsapp.ui;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.lightsapp.core.CameraController;
import com.lightsapp.core.OutputController;
import com.lightsapp.core.SoundController;
import com.lightsapp.core.analyzer.sound.SoundAnalyzer;

import static com.lightsapp.utils.HandlerUtils.signalStr;

public class SetupHandler extends HandlerThread {
    private final String TAG = SetupHandler.class.getSimpleName();
    Handler mHandlerSetup = null;
    private MainActivity mContext;

    SetupHandler() {
        super("SetupHandler");
        start();
        mHandlerSetup = new Handler(getLooper());
    }

    void setupHandler(final Context context) {
        mContext = (MainActivity) context;
        mHandlerSetup.post(new Runnable() {
            @Override
            public void run() {
                boolean done = false;

                while (!done) {
                    try {
                        if (mContext.mCamera == null) {
                            mContext.mCameraController = new CameraController(mContext);
                            mContext.mCamera = mContext.mCameraController.setup();
                        }

                        if (mContext.mHandlerInfo != null && mContext.mHandlerRecv != null &&
                            mContext.mCamera != null && mContext.mCameraController != null) {
                            mContext.mOutputController = new OutputController(mContext);
                            if (mContext.mOutputController != null)
                                mContext.mOutputController.start();
                            else
                                continue;

                            mContext.mSoundController = new SoundController(mContext);
                            mContext.mSoundController.setup();
                            mContext.mSoundA = new SoundAnalyzer(context);
                            if (mContext.mSoundA != null) {
                                mContext.mSoundA.start();
                                mContext.mSoundA.activate();
                            }
                            else
                                continue;

                            signalStr(mContext.mHandlerInfo, "setup_done", "");
                            signalStr(mContext.mHandlerRecv, "setup_done", "");
                            done = true;
                        }
                    }
                    catch (RuntimeException e) {
                        Log.e(TAG, "setup error: " + e.getMessage());
                    }

                    try {
                        if (!done) {
                            Log.e(TAG, "setup failed, retrying... ");
                            Thread.sleep(50);
                        }
                    }
                    catch (InterruptedException e) {
                    }
                }
                Log.v(TAG, "setup done");
            }
        });
    }
}