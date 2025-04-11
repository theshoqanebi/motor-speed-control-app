package com.theshoqanebi.servomanager;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;

import java.util.Objects;

public class LoadingDialog extends Dialog {
    public LoadingDialog(@NonNull Context context) {
        super(context);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.setContentView(R.layout.dialog_loading);
        this.setCancelable(false);
        Objects.requireNonNull(this.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        this.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}