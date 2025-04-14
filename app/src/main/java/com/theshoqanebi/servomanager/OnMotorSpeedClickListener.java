package com.theshoqanebi.servomanager;

import android.view.View;

public interface OnMotorSpeedClickListener {
    void onIncrease(int position);

    void onDecrease(int position);

    void onSeekBarProgressChanged(int position, int progress);
    void onSeekBarStartTrackingTouch(int position);
    void onSeekBarStopTrackingTouch(int position);
}
