package com.tjo7.stamp;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Receives images / videos shared to the app from the gallery (Share -> this app).
 * Each shared item is copied into the app cache (so the WebView can read it) and handed to JS through getShared().
 */
@CapacitorPlugin(name = "ShareReceiver")
public class ShareReceiverPlugin extends Plugin {

    private static final List<JSObject> PENDING = new ArrayList<>();
    private static int counter = 0;

    /** Called from MainActivity for the first launch and for every new share while the app is open. */
    static void handleIntent(final Activity activity, Intent intent) {
        if (activity == null || intent == null) return;
        String action = intent.getAction();
        final ArrayList<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        }
        if (uris.isEmpty()) return;
        intent.setAction(null); // do not process the same share twice (rotation, resume)

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean any = false;
                for (Uri uri : uris) {
                    try {
                        JSObject o = copyToCache(activity, uri);
                        if (o != null) {
                            synchronized (PENDING) { PENDING.add(o); }
                            any = true;
                        }
                    } catch (Exception ignored) { /* skip this item */ }
                }
                if (any && activity instanceof BridgeActivity) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Bridge b = ((BridgeActivity) activity).getBridge();
                            if (b != null) b.triggerWindowJSEvent("tjo7shared");
                        }
                    });
                }
            }
        }, "tjo7-share-copy").start();
    }

    private static JSObject copyToCache(Activity ctx, Uri uri) throws Exception {
        String name = null;
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        String mime = ctx.getContentResolver().getType(uri);
        if (name == null || name.isEmpty()) {
            String last = uri.getLastPathSegment();
            name = last == null ? "shared" : last;
        }
        if (name.indexOf('.') < 0 && mime != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ext != null) name = name + "." + ext;
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");

        File dir = new File(ctx.getCacheDir(), "shared");
        if (!dir.exists()) dir.mkdirs();
        int n;
        synchronized (PENDING) { n = ++counter; }
        File out = new File(dir, System.currentTimeMillis() + "-" + n + "-" + name);

        InputStream in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) return null;
        try {
            OutputStream os = new FileOutputStream(out);
            try {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) != -1) os.write(buf, 0, r);
            } finally {
                os.close();
            }
        } finally {
            in.close();
        }
        JSObject o = new JSObject();
        o.put("path", "file://" + out.getAbsolutePath());
        o.put("name", name);
        o.put("mime", mime == null ? "" : mime);
        o.put("size", out.length());
        return o;
    }

    @PluginMethod
    public void getShared(PluginCall call) {
        JSArray arr = new JSArray();
        synchronized (PENDING) {
            for (JSObject o : PENDING) arr.put(o);
            PENDING.clear();
        }
        JSObject r = new JSObject();
        r.put("files", arr);
        call.resolve(r);
    }

    /** Deletes the cached copies once JS has loaded them into memory. */
    @PluginMethod
    public void cleanup(PluginCall call) {
        File dir = new File(getContext().getCacheDir(), "shared");
        File[] list = dir.listFiles();
        if (list != null) for (File f : list) f.delete();
        call.resolve();
    }
}
