package com.theshoqanebi.servomanager;

import androidx.annotation.Nullable;

public class Motor {
    private int id;
    private int speed;

    public Motor(int id, int speed) {
        this.id = id;
        this.speed = speed;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        assert obj != null;
        return ((Motor) obj).getId() == this.getId();
    }
}
