package com.theshoqanebi.servomanager;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.theshoqanebi.servomanager.databinding.MotorControlBinding;

import java.util.ArrayList;
import java.util.Locale;

public class Adapter extends RecyclerView.Adapter<ViewHolder> {
    private ArrayList<Motor> motors;
    private final OnMotorSpeedClickListener listener;

    public Adapter(@NonNull ArrayList<Motor> motors, OnMotorSpeedClickListener listener) {
        this.motors = motors;
        this.listener = listener;
    }

    public void update(@NonNull ArrayList<Motor> motors, int position) {
        if (position < motors.size()) {
            this.motors = motors;
            notifyItemChanged(position);
        }
    }

    public void resetAll() {
        for(int i = 0; i < motors.size(); i++) {
            motors.get(i).setSpeed(0);
            notifyItemChanged(i);
        }
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(MotorControlBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Motor current = motors.get(position);
        holder.binding.number.setText(String.valueOf(position + 1));
        holder.binding.speed.setText(String.valueOf(1000 + current.getSpeed()));
        holder.binding.speedSeekBar.setProgress(current.getSpeed());
    }

    @Override
    public int getItemCount() {
        return motors.size();
    }
}
