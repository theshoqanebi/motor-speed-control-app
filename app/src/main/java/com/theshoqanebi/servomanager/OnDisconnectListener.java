package com.theshoqanebi.servomanager;

public interface OnDisconnectListener {
    void onDisconnect();
    void onDisconnectFailure(String err);
}
