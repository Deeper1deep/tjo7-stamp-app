package com.tjo7.stamp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Copies a file from the app cache into shared storage (Pictures / Movies / Download) via MediaStore. */
@CapacitorPlugin(name = "SaveToDevice")
public class SaveToDevicePlugin extends Plugin {

    @PluginMethod
    public void save(PluginCall call) {
        String src = call.getString("path");
        String name = call.getString("name");
        String mime = call.getString("mime", "application/octet-stream");
        if (src == null || name == null) {
            call.reject("bad_args");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            call.reject("old_android");
            return;
        }
        try {
            String p = Uri.parse(src).getPath();
            File f = new File(p == null ? src : p);
            ContentResolver cr = getContext().getContentResolver();
            Uri collection;
            String folder;
            if (mime.startsWith("image/")) {
                collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                folder = Environment.DIRECTORY_PICTURES + "/t.jo7";
            } else if (mime.startsWith("video/")) {
                collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                folder = Environment.DIRECTORY_MOVIES + "/t.jo7";
            } else {
                collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                folder = Environment.DIRECTORY_DOWNLOADS + "/t.jo7";
            }
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = cr.insert(collection, v);
            if (uri == null) throw new IOException("insert_failed");
            try (InputStream in = new FileInputStream(f); OutputStream out = cr.openOutputStream(uri)) {
                if (out == null) throw new IOException("open_failed");
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            JSObject r = new JSObject();
            r.put("uri", uri.toString());
            r.put("folder", folder);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage() == null ? "save_failed" : e.getMessage());
        }
    }
}
