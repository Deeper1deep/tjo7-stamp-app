package com.tjo7.stamp;

import android.content.Intent;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SaveToDevicePlugin.class);
        registerPlugin(ShareReceiverPlugin.class);
        registerPlugin(UpdatePlugin.class);
        super.onCreate(savedInstanceState);
        ShareReceiverPlugin.handleIntent(this, getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        ShareReceiverPlugin.handleIntent(this, intent);
    }
}
