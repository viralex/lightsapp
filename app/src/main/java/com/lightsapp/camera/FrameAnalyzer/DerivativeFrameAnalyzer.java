package com.lightsapp.camera.FrameAnalyzer;

import android.content.Context;
import android.util.Log;

import com.lightsapp.utils.LinearFilter;

import java.util.ArrayList;
import java.util.List;

import static com.lightsapp.camera.FrameAnalyzer.DerivativeFrameAnalyzer.StatusCode.*;
import static com.lightsapp.utils.HandlerUtils.*;

public class DerivativeFrameAnalyzer extends FrameAnalyzer {
    enum StatusCode{ERROR, INIT, SEARCH_HIGH, SET_DATA, SEARCH_LOW, SET_GAP}

    public DerivativeFrameAnalyzer(Context context){
        super(context);
    }

    @Override
    public void analyze() {
        if ((lframes.size()) < 2) {
            signalStr(mCtx.mHandlerRecv, "data_message_text", "<derivative algorithm>");
            return;
        }

        List<Long> ldata = new ArrayList<Long>();
        List<Long> lframes_d = new ArrayList<Long>();

        long tstart = 0, tstop = 0, diff;

        float fdata_lum[] = new float[lframes.size()];
        LinearFilter dataFilter = LinearFilter.get(LinearFilter.Filter.KERNEL_GAUSSIAN_11);
        for (int i = 0; i < lframes.size(); i++) {
            fdata_lum[i] = (float) lframes.get(i).luminance;
        }
        dataFilter.apply(fdata_lum);

        // TODO optimize and compute incrementally, use, last_diff and add new frames.
        for (int i = 1; i < lframes.size(); i++) {
              long dd = (long) (fdata_lum[i] - fdata_lum[i-1]);
              lframes_d.add(dd);
        }

        Log.w(TAG, "START!");

        long fmax = Long.MIN_VALUE;
        long fmin = Long.MAX_VALUE;
        int fmax_id = 0, fmin_id = 0;

        StatusCode statcode = INIT;

        for (int i = 0; i < (lframes_d.size() - 1); i++) {
            switch (statcode) {
                case INIT:
                    if (lframes_d.get(i) > sensitivity) {
                        statcode = SEARCH_HIGH;
                        Log.w(TAG, "Searching high front");
                    }
                    else
                        break;

                case SEARCH_HIGH:
                    if ((lframes_d.get(i) > sensitivity)) {
                        if (lframes_d.get(i) > fmax)
                        {
                            fmax = lframes_d.get(i);
                            fmax_id = i;
                            Log.w(TAG, "new max");
                        }
                    }
                    else {
                        Log.w(TAG, "maxid: " + fmax_id);
                        tstart = lframes.get(fmax_id).timestamp;
                        if (tstop == 0) {
                            statcode = SEARCH_LOW;
                            Log.w(TAG, "Searching low front");
                        }
                        else {
                            statcode = SET_GAP;
                            Log.w(TAG, "Setting gap");
                        }
                    }
                    break;

                case SEARCH_LOW:
                    if ((lframes_d.get(i) < -sensitivity)) {
                        if (lframes_d.get(i) < fmin)
                        {
                            fmin = lframes_d.get(i);
                            fmin_id = i;
                            Log.w(TAG, "new min");
                        }
                    }
                    else {
                        Log.w(TAG, "minid: " + fmin_id);
                        tstop = lframes.get(fmin_id).timestamp;
                        statcode = SET_DATA;
                        Log.w(TAG, "Setting data");
                    }
                    break;

                case SET_DATA:
                    diff = tstop - tstart;

                    /*
                    if (diff > (8 * speed_base)) {
                        Log.w(TAG, "too long high signal");
                        tstart = 0;
                        ldata.add(new Long(-7 * speed_base));
                        continue;
                    }
                    */
                    Log.w(TAG, "diff: " + diff);
                    if (diff > (long) (0.6 * (float) speed_base)) {
                        ldata.add(new Long(diff));
                        statcode = SEARCH_HIGH;
                        Log.w(TAG, "Searching high front for the gap end");
                    }
                    else {
                        statcode = SEARCH_LOW;
                        Log.w(TAG, "Skip short data frame, go back to search the real low front");
                    }
                    break;

                case SET_GAP:
                    diff = tstart - tstop;

                    /*
                    if (diff > (8 * speed_base)) {
                        Log.w(TAG, "too long low signal");
                        tstop = 0;
                        ldata.add(new Long(-7 * speed_base));
                        continue;
                    }
                    */

                    if (diff > (long) (0.6 * (float) speed_base)) {
                        ldata.add(new Long(-diff));
                        tstart = lframes.get(i).timestamp;
                        statcode = SEARCH_LOW;
                        Log.w(TAG, "Searching low front for the data end");
                    }
                    else {
                        statcode = SEARCH_HIGH;
                        Log.w(TAG, "Skip short gap frame, go back to search the real high front");
                    }
                    break;

                case ERROR:
                    statcode = INIT;
                    Log.w(TAG, "GO TO INIT");
                    break;
            }
        }

        if (ldata.size() > 0)
            endAnalyze(ldata);
    }
}
