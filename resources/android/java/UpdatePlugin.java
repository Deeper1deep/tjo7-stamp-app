package com.tjo7.stamp;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * In-app updater. The app reads latest.json from the public releases repo, downloads the APK into the app cache
 * and hands it to the system installer. Signed with the same key, so it installs over the old version.
 */
@CapacitorPlugin(name = "AppUpdate")
public class UpdatePlugin extends Plugin {

    private static final String UA = "tjo7-stamp-updater";
    private volatile long done = 0;
    private volatile long total = 0;
    private volatile boolean cancelled = false;
    private volatile boolean running = false;

    private HttpURLConnection open(String url) throws Exception {
        if (url == null || !url.startsWith("https://")) throw new Exception("رابط التحديث لازم يبدأ بـ https");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Cache-Control", "no-cache");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new Exception("السيرفر رد بـ " + code);
        }
        return c;
    }

    @PluginMethod
    public void info(PluginCall call) {
        try {
            Context ctx = getContext();
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            long vc = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            JSObject r = new JSObject();
            r.put("versionCode", vc);
            r.put("versionName", pi.versionName == null ? "" : pi.versionName);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void check(final PluginCall call) {
        final String url = call.getString("url");
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    c = open(url);
                    InputStream in = c.getInputStream();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        bos.write(buf, 0, n);
                        if (bos.size() > 256 * 1024) throw new Exception("ملف التحديث كبير بشكل غريب");
                    }
                    in.close();
                    JSObject r = new JSObject();
                    r.put("json", bos.toString("UTF-8"));
                    call.resolve(r);
                } catch (Exception e) {
                    call.reject(String.valueOf(e.getMessage()));
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }, "tjo7-upd-check").start();
    }

    @PluginMethod
    public void progress(PluginCall call) {
        JSObject r = new JSObject();
        r.put("done", done);
        r.put("total", total);
        r.put("running", running);
        call.resolve(r);
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        cancelled = true;
        call.resolve();
    }

    @PluginMethod
    public void download(final PluginCall call) {
        final String url = call.getString("url");
        if (running) { call.reject("التحميل شغال بالفعل"); return; }
        running = true; cancelled = false; done = 0; total = 0;
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                File part = null;
                try {
                    File dir = new File(getContext().getCacheDir(), "updates");
                    if (!dir.exists()) dir.mkdirs();
                    File[] old = dir.listFiles();
                    if (old != null) for (File f : old) f.delete();
                    part = new File(dir, "tjo7-update.part");
                    File out = new File(dir, "tjo7-update.apk");
                    c = open(url);
                    total = c.getContentLengthLong();
                    InputStream in = c.getInputStream();
                    OutputStream os = new FileOutputStream(part);
                    try {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            if (cancelled) throw new Exception("cancelled");
                            os.write(buf, 0, n);
                            done += n;
                        }
                    } finally {
                        os.close();
                        in.close();
                    }
                    if (total > 0 && done != total) throw new Exception("التحميل ناقص، جرّب تاني");
                    if (done < 100 * 1024) throw new Exception("الملف اللي اتحمّل مش APK");
                    if (!part.renameTo(out)) throw new Exception("مقدرتش أحفظ الملف");
                    JSObject r = new JSObject();
                    r.put("size", out.length());
                    call.resolve(r);
                } catch (Exception e) {
                    if (part != null) part.delete();
                    call.reject(cancelled ? "cancelled" : String.valueOf(e.getMessage()));
                } finally {
                    running = false;
                    if (c != null) c.disconnect();
                }
            }
        }, "tjo7-upd-download").start();
    }

    @PluginMethod
    public void install(PluginCall call) {
        try {
            Context ctx = getContext();
            File apk = new File(new File(ctx.getCacheDir(), "updates"), "tjo7-update.apk");
            if (!apk.isFile()) { call.reject("ملف التحديث مش موجود، حمّله تاني"); return; }
            if (Build.VERSION.SDK_INT >= 26 && !ctx.getPackageManager().canRequestPackageInstalls()) {
                Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.getPackageName()));
                s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(s);
                JSObject r = new JSObject();
                r.put("status", "needs_permission");
                call.resolve(r);
                return;
            }
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            JSObject r = new JSObject();
            r.put("status", "started");
            call.resolve(r);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }
}
