package pt.itm.cutter;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImageLoader {
    private final Activity activity;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    ImageLoader(Activity activity) { this.activity = activity; }

    void load(String url, ImageView target) {
        target.setImageDrawable(null);
        if (url == null || url.isEmpty()) return;
        Bitmap hit = cache.get(url);
        if (hit != null) { target.setImageBitmap(hit); return; }
        target.setTag(url);
        pool.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                Bitmap b = BitmapFactory.decodeStream(c.getInputStream());
                if (b == null) return;
                cache.put(url, b);
                activity.runOnUiThread(() -> {
                    if (url.equals(target.getTag())) target.setImageBitmap(b);
                });
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    void shutdown() { pool.shutdownNow(); }
}
