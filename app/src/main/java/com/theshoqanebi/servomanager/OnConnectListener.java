package com.theshoqanebi.servomanager;

public interface OnConnectListener {
    void onConnect();
    void onConnectFailure(String err);
}
