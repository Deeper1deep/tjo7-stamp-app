package com.tjo7.stamp;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SaveToDevicePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
