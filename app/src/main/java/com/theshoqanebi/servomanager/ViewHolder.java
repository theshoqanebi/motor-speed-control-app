package com.theshoqanebi.servomanager;

import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.theshoqanebi.servomanager.databinding.MotorControlBinding;

public class ViewHolder extends RecyclerView.ViewHolder {
    public MotorControlBinding binding;

    public ViewHolder(@NonNull MotorControlBinding binding, OnMotorSpeedClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;

        if (listener != null) {
            binding.increase.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onIncrease(position);
                }
            });

            binding.decrease.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onDecrease(position);
                }
            });

            binding.speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onSeekBarProgressChanged(position, progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onSeekBarStartTrackingTouch(position);
                    }
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onSeekBarStopTrackingTouch(position);
                    }
                }
            });
        }
    }
}
