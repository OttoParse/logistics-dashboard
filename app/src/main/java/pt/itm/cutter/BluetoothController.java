package pt.itm.cutter;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BluetoothController {
    interface Listener {
        void status(String text, boolean connected);
        void message(String text);
        void cutDone();
        void error(String text);
    }

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final Pattern CHALLENGE = Pattern.compile("RCMD=11,(\\d+);");
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final StringBuilder rx = new StringBuilder();
    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;
    private Thread reader;
    private volatile boolean running;
    private volatile boolean waiting;
    private byte[] pending;
    private Listener listener;

    boolean isConnected() { return socket != null && socket.isConnected() && out != null; }

    void connect(BluetoothDevice device, Listener l) {
        disconnect();
        listener = l;
        io.execute(() -> {
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP);
                socket.connect();
                in = socket.getInputStream();
                out = socket.getOutputStream();
                startReader();
                sendText(";RHVER;");
                sendText(";RCBM;");
                sendText(";RPID;");
                sendText(";RSVER;");
                l.status(device.getName() == null ? "CUTTER ON" : device.getName(), true);
            } catch (Exception e) {
                closeQuietly();
                l.status("CUTTER OFF", false);
                l.error("Ligação Bluetooth: " + safe(e));
            }
        });
    }

    void cut(byte[] sjc, Listener l) {
        listener = l;
        if (!isConnected()) { l.error("A plotter não está ligada."); return; }
        io.execute(() -> {
            try {
                pending = sjc;
                waiting = true;
                synchronized (rx) { rx.setLength(0); }
                sendText(";BD:10;");
                l.message("A autorizar corte na plotter…");
            } catch (Exception e) {
                waiting = false;
                l.error("Falha ao iniciar corte: " + safe(e));
            }
        });
    }

    void disconnect() {
        running = false;
        waiting = false;
        pending = null;
        if (reader != null) reader.interrupt();
        closeQuietly();
        if (listener != null) listener.status("CUTTER OFF", false);
    }

    void shutdown() { disconnect(); io.shutdownNow(); }

    private void startReader() {
        running = true;
        reader = new Thread(() -> {
            byte[] buf = new byte[1024];
            while (running) {
                try {
                    int n = in.read(buf);
                    if (n < 0) break;
                    String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                    handle(chunk);
                } catch (Exception e) {
                    if (running && listener != null) listener.error("Ligação à plotter perdida.");
                    break;
                }
            }
            running = false;
        }, "itm-bt-reader");
        reader.start();
    }

    private void handle(String chunk) {
        if (!waiting) return;
        String all;
        synchronized (rx) {
            rx.append(chunk);
            if (rx.length() > 4096) rx.delete(0, rx.length() - 2048);
            all = rx.toString();
        }
        try {
            Matcher m = CHALLENGE.matcher(all);
            if (m.find()) {
                long challenge = Long.parseLong(m.group(1));
                long response = handshake(challenge);
                sendText("BD:12," + Long.toUnsignedString(response) + ";");
                synchronized (rx) { rx.setLength(0); }
                return;
            }
            if (all.contains("RCMD=12,0;")) {
                byte[] data = pending;
                pending = null;
                waiting = false;
                if (data == null) throw new IllegalStateException("Corte vazio");
                out.write(data);
                out.flush();
                if (listener != null) {
                    listener.message("Corte enviado para a plotter.");
                    listener.cutDone();
                }
            } else if (all.contains("RCMD=12,") && all.contains(";")) {
                waiting = false;
                if (listener != null) listener.error("A plotter recusou o handshake de corte.");
            }
        } catch (Exception e) {
            waiting = false;
            if (listener != null) listener.error("Handshake: " + safe(e));
        }
    }

    private void sendText(String s) throws Exception {
        if (out == null) throw new IllegalStateException("Bluetooth desligado");
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static long handshake(long challenge) {
        long x = challenge & 0xffffffffL;
        x = (x ^ 410665463L) & 0xffffffffL;
        x = (x + 859313513L) & 0xffffffffL;
        x = (x ^ 1368461889L) & 0xffffffffL;
        x = (x + 1789835113L) & 0xffffffffL;
        return x;
    }

    private void closeQuietly() {
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        in = null; out = null; socket = null;
    }

    private static String safe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
