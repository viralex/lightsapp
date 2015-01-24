package com.lightsapp.core.analyzer.sound;

import android.content.Context;
import android.util.Log;

import com.lightsapp.core.analyzer.BaseAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.lightsapp.utils.HandlerUtils.signalStr;

public class SoundAnalyzer extends BaseAnalyzer {
    protected final String TAG = SoundAnalyzer.class.getSimpleName();

    protected int THRESHOLD = 100;

    private int sampleRate = 8000;
    private int blockSize = 256;
    private short[] buffer;
    private byte[] buffer_byte;

    private int beepFreq;
    private int beepFreqval;
    private int min_beepFreqval;
    private int max_beepFreqval;
    private int bandwidth;

    private long time;
    private boolean signal_up;
    private boolean threshold_changed;
    private boolean to_reset;

    private BlockingQueue<Spectrum> bQueueSpectrum;
    private BlockingQueue<Frame> bQueueFrameIn;
    private BlockingQueue<Frame> bQueueFrameElaborated;
    private List<Long> ldata;
    private List<Double> maxVector;

    public SoundAnalyzer(Context context) {
        super(context);

        bQueueSpectrum = new LinkedBlockingQueue<Spectrum>();
        bQueueFrameIn = new LinkedBlockingQueue<Frame>();
        bQueueFrameElaborated = new LinkedBlockingQueue<Frame>();

        ldata = new ArrayList<Long>();
        maxVector = new ArrayList<Double>();

        signal_up = false;
        to_reset = false;
        sleep_time = 10;
        time = 0;

        threshold_changed = false;

        blockSize = Integer.valueOf(mContext.mPrefs.getString("fft_size", "512"));
        sampleRate = Integer.valueOf(mContext.mPrefs.getString("sample_freq", "8000"));
        beepFreq = Integer.valueOf(mContext.mPrefs.getString("beep_freq", "850"));
        bandwidth = Integer.valueOf(mContext.mPrefs.getString("bandwidth", "5"));

        buffer = new short[blockSize];
        buffer_byte = new byte[blockSize];

        beepFreqval = beepFreq * blockSize / sampleRate;
        min_beepFreqval = 0;
        max_beepFreqval = blockSize - 1;
        if (beepFreqval > bandwidth)
            min_beepFreqval = beepFreqval - bandwidth;
        if (beepFreqval < (blockSize - 1 - bandwidth))
            max_beepFreqval = beepFreqval + bandwidth;
        Log.v(TAG, "beepFreqval: "+beepFreqval+", min_beepFreqval: "+min_beepFreqval+", max_beepFreqval: "+max_beepFreqval);

    }

    public void reset_simple()
    {
        bQueueFrameElaborated.clear();
        ldata.clear();
        signal_up = false;
        time = 0;
    }

    protected void force_reset()
    {
        bQueueFrameIn.clear();
        bQueueFrameElaborated.clear();
        ldata.clear();
        maxVector.clear();
        signal_up = false;
        time = 0;
    }

    public void reset()
    {
        to_reset = true;
        mContext.mMorseA.reset();
        signalStr(mContext.mHandlerInfo, "reset_graphs", "");
    }

    protected void reanalyze()
    {
        ldata.clear();
        signal_up = false;
        time = 0;

        for (Frame f: bQueueFrameElaborated) {
            if (signal_up){ // valuta condizione di discesa
                if (f.maxY < (THRESHOLD * sensitivity)){
                    signal_up = false;
                    ldata.add(time);
                    time = 0;
                }
                else{
                    time += f.delta;
                }
            }
            else { // valuta condizione di salita
                if (f.maxY > (THRESHOLD * sensitivity))
                {
                    signal_up = true;
                    ldata.add(time);
                    time = 0;
                }
                else{
                    time -= f.delta;
                }
            }
        }
    }

    protected void analyze() {

        if (to_reset){
            force_reset();
            to_reset = false;
        }

        // Se la coda è vuota (non dovrebbe capitare)
        if (bQueueFrameIn.isEmpty())
            return;

        // controlla che non c'è un cambio di frequenza di ricezione
        if (beepFreq != Integer.valueOf(mContext.mPrefs.getString("beep_freq", "850"))){
            reset_simple();
            beepFreq = Integer.valueOf(mContext.mPrefs.getString("beep_freq", "850"));
            beepFreqval = beepFreq * blockSize / sampleRate;
            Log.v(TAG, "beepFreqval: "+beepFreqval);
            min_beepFreqval = 0;
            max_beepFreqval = blockSize - 1;
            if (beepFreqval > bandwidth)
                min_beepFreqval = beepFreqval- bandwidth;
            if (beepFreqval < (blockSize - 1 - bandwidth))
                max_beepFreqval = beepFreqval+ bandwidth;
        }

        try {

            if (threshold_changed){
                reanalyze();
                threshold_changed = false;
            }

            Frame new_frame = bQueueFrameIn.take();

            // Per il grafico
            Spectrum spectrum = new Spectrum(new_frame.getSpectrum().getCopy());
            bQueueSpectrum.put(spectrum);

            new_frame.cutSpectrum(min_beepFreqval, max_beepFreqval);

            if (signal_up){ // valuta condizione di discesa
                if (new_frame.getMax() < (THRESHOLD * sensitivity)){
                    signal_up = false;
                    ldata.add(time);
                    time = 0;
                }
                else{
                    time += new_frame.delta;
                }
                //Log.v(TAG, "Is Up, average: "+new_frame.avg);
            }
            else { // valuta condizione di salita
                if (new_frame.getMax() > (THRESHOLD * sensitivity))
                {
                    signal_up = true;
                    ldata.add(time);
                    time = 0;
                }
                else{
                    time -= new_frame.delta;
                }
                //Log.v(TAG, "Is Down, average: "+new_frame.avg);
            }

            Log.v(TAG, "Delta: "+new_frame.delta);
            new_frame.clean();
            bQueueFrameElaborated.put(new_frame);

            maxVector.add(new_frame.maxY);
            if (enable_analyze.get() && ldata.size() > 0)
                mContext.mMorseA.analyze(ldata);
        }
        catch (InterruptedException e) {
        }
        catch (Exception e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
    }

    public double[] getFrames()
    {
        try {
            if (!bQueueSpectrum.isEmpty()) {
                Spectrum spectrum = bQueueSpectrum.peek();
                bQueueSpectrum.clear();
                return spectrum.getData();
            }
        }
        catch (Exception e){
            Log.e(TAG, "getFrames(): " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public double[] getSignal() {
        try {
            while (maxVector.size() > 512)
                maxVector.remove(0);

            double data[] = new double[maxVector.size()];
            int index = 0;
            for(Double val : maxVector) {
                data[index] =  val;
                index++;
            }
            return data;
        }
        catch (Exception e){ }

        return null;
    }

    @Override
    public final void loop() {
        try {
            Thread.sleep(sleep_time);
            SoundDataBlock data = null;
            if (mContext.mSoundController != null) {
                int ret = mContext.mSoundController.mAudioRec.read(buffer_byte, 0, blockSize);
                if (ret > 0)
                {
                    // Conversione in double da byte
                    double[] micBufferData = new double[blockSize];
                    final int bytesPerSample = 2; // As it is 16bit PCM
                    final double amplification = 100.0; // choose a number as you like
                    for (int index = 0, floatIndex = 0; index < ret - bytesPerSample + 1; index += bytesPerSample, floatIndex++)
                    {
                        double sample = 0;
                        for (int b = 0; b < bytesPerSample; b++) {
                            int v = buffer_byte[index + b];
                            if (b < bytesPerSample - 1 || bytesPerSample == 1) {
                                v &= 0xFF;
                            }
                            sample += v << (b * 8);
                        }
                        double sample32 = amplification * (sample / 32768.0);
                        micBufferData[floatIndex] = sample32;
                    }

                    data = new SoundDataBlock(micBufferData);

                    long timestamp_now = System.currentTimeMillis();
                    Frame fdata = new Frame(data, timestamp_now, timestamp_now - timestamp_last);
                    bQueueFrameIn.add(fdata);
                    timestamp_last = timestamp_now;

                    analyze();
                }
                else
                    Log.v(TAG, "audio recording ret is 0");
            }
            signalStr(mContext.mHandlerInfo, "update_graphs_sound", "");
        }
        //catch (InterruptedException e) {
        //}
        catch (Exception e) {
            Log.e(TAG, "error analyzing audio frames: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void setSensitivity(int sensitivity) {
        super.setSensitivity(sensitivity);
        mContext.graphView_snd.setManualYAxisBounds(sensitivity * THRESHOLD, 0);
        threshold_changed = true;
    }

}