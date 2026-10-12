package pt.itm.cutter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class CatalogClient {
    static final String BASE = "https://proscreen.celly.com/";

    private static volatile String sessionId = "";
    private static volatile String authEmail = "";
    private static volatile String authPassword = "";

    static synchronized void configureAuth(String email, String password, String session) {
        authEmail = email == null ? "" : email.trim();
        authPassword = password == null ? "" : password;
        sessionId = session == null ? "" : session.trim();
    }

    static synchronized String login(String email, String password) throws Exception {
        if (email == null || email.trim().isEmpty()) throw new IOException("Introduz o email da conta OEM.");
        if (password == null || password.isEmpty()) throw new IOException("Introduz a palavra-passe da conta OEM.");

        String u = BASE + "api/user/login?email=" + enc(email.trim()) + "&pwd=" + enc(password);
        JSONObject root = postRaw(u, null, null);
        JSONObject data = root.optJSONObject("data");
        JSONObject buss = data == null ? null : data.optJSONObject("bussData");
        String s = buss == null ? "" : first(buss, "sessionId");
        if (s.isEmpty()) throw new IOException(message(root, "Não foi possível iniciar sessão no catálogo OEM."));

        configureAuth(email, password, s);
        return s;
    }

    static String currentSession() { return sessionId; }

    List<Models.Category> categories() throws Exception {
        JSONObject root = postAuth("api/sysClassOne/find_list", null);
        JSONArray arr = arrayFrom(root);
        if (arr.length() == 0) throw new IOException(message(root, "Catálogo vazio"));
        ArrayList<Models.Category> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Models.Category c = new Models.Category();
            c.id = intOf(o, "id", "classOneId");
            c.name = bestName(o);
            c.icon = absolute(first(o, "icon", "englishIcon", "coverIcon"));
            if (c.id != 0 && !c.name.isEmpty()) out.add(c);
        }
        out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    List<Models.Family> families(Models.Category cat) throws Exception {
        JSONObject body = new JSONObject();
        body.put("classOneId", cat.id);
        body.put("pageIndex", 1);
        body.put("pageSize", "5000");
        JSONObject root = postAuth("api/sysClassTwo/find_page", body);
        JSONArray arr = arrayFrom(root);
        if (arr.length() == 0) throw new IOException(message(root, "Sem marcas nesta categoria"));
        ArrayList<Models.Family> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Models.Family f = new Models.Family();
            f.id = intOf(o, "id", "classTwoId");
            f.classOneId = intOf(o, "classOneId");
            f.name = bestName(o);
            f.icon = absolute(first(o, "icon", "englishIcon", "coverIcon"));
            if (f.id != 0 && !f.name.isEmpty()) out.add(f);
        }
        out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    List<Models.Product> products(Models.Category cat, Models.Family fam) throws Exception {
        JSONObject body = new JSONObject();
        body.put("classTwoId", fam.id);
        body.put("pageIndex", 1);
        body.put("pageSize", "5000");
        JSONObject root = postAuth("api/product/find_page", body);
        JSONArray arr = arrayFrom(root);
        if (arr.length() == 0) throw new IOException(message(root, "Sem modelos nesta família"));
        ArrayList<Models.Product> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Models.Product p = parseProduct(o);
            if (p.id != 0 && !p.name.isEmpty()) out.add(p);
        }
        out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    Models.Product detail(Models.Product p) throws Exception {
        if (p.file != null && !p.file.isEmpty()) return p;
        JSONObject root = postAuth("api/product/find_detail?id=" + p.id, null);
        JSONObject o = objectFrom(root);
        if (o == null) return p;
        Models.Product d = parseProduct(o);
        if (d.id == 0) d.id = p.id;
        if (d.classOneId == 0) d.classOneId = p.classOneId;
        if (d.classTwoId == 0) d.classTwoId = p.classTwoId;
        if (d.name.isEmpty()) d.name = p.name;
        if (d.cover.isEmpty()) d.cover = p.cover;
        if (d.detail.isEmpty()) d.detail = p.detail;
        return d;
    }

    byte[] download(String url) throws Exception {
        HttpURLConnection c = open(url);
        c.setRequestMethod("GET");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IOException("Falha no ficheiro de corte (HTTP " + code + ")");
        }
        byte[] bytes = readAll(c.getInputStream());
        c.disconnect();
        return bytes;
    }

    private Models.Product parseProduct(JSONObject o) {
        Models.Product p = new Models.Product();
        p.id = intOf(o, "id", "productId");
        p.classOneId = intOf(o, "classOneId");
        p.classTwoId = intOf(o, "classTwoId");
        p.name = bestName(o);
        p.cover = absolute(first(o, "coverIcon", "icon", "englishIcon"));
        p.detail = absolute(first(o, "detailIcon", "detailImage", "image"));
        p.file = absolute(first(o, "file", "filePath", "sjcFile"));
        p.size = first(o, "membraneSize", "size");
        p.isBack = intOf(o, "isBack");
        p.raw = o;
        return p;
    }

    private static synchronized JSONObject postAuth(String path, JSONObject body) throws Exception {
        ensureSession();
        JSONObject root = postRaw(BASE + path, body, sessionId);
        if (isExpired(root) && !authEmail.isEmpty() && !authPassword.isEmpty()) {
            login(authEmail, authPassword);
            root = postRaw(BASE + path, body, sessionId);
        }
        return root;
    }

    private static void ensureSession() throws Exception {
        if (!sessionId.isEmpty()) return;
        if (!authEmail.isEmpty() && !authPassword.isEmpty()) {
            login(authEmail, authPassword);
            return;
        }
        throw new IOException("É necessário iniciar sessão no catálogo OEM.");
    }

    private static JSONObject postRaw(String urlText, JSONObject body, String session) throws Exception {
        HttpURLConnection c = openStatic(urlText);
        c.setRequestMethod("POST");
        c.setRequestProperty("Accept", "application/json");
        if (session != null && !session.isEmpty()) c.setRequestProperty("sessionId", session);

        if (body != null) {
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            c.setDoOutput(true);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = c.getOutputStream()) { out.write(payload); }
        } else {
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(0);
            try (OutputStream out = c.getOutputStream()) { out.flush(); }
        }

        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
        c.disconnect();
        if (text.trim().isEmpty()) throw new IOException("Resposta vazia do catálogo (HTTP " + code + ")");
        if (text.trim().startsWith("<")) throw new IOException("O catálogo devolveu HTML (HTTP " + code + ")");
        return new JSONObject(text);
    }

    private HttpURLConnection open(String urlText) throws Exception { return openStatic(urlText); }

    private static HttpURLConnection openStatic(String urlText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "ITM-Cutter/0.5.1 Android");
        return c;
    }

    private static boolean isExpired(JSONObject root) {
        String m = message(root, "").toLowerCase(Locale.ROOT);
        return m.contains("not logged in") || m.contains("session expired") || m.contains("session has expired");
    }

    static boolean looksLikeSjc(byte[] data) {
        if (data == null || data.length < 10) return false;
        String s = new String(data, StandardCharsets.UTF_8).replace("\uFEFF", "").trim();
        return s.startsWith("IN IN") && s.contains("@");
    }

    static String absolute(String u) {
        if (u == null) return "";
        u = u.trim();
        if (u.isEmpty()) return "";
        if (u.startsWith("http://") || u.startsWith("https://")) return u;
        if (u.startsWith("//")) return "https:" + u;
        if (u.startsWith("/")) return BASE.substring(0, BASE.length() - 1) + u;
        return BASE + u;
    }

    private static String bestName(JSONObject o) {
        return first(o, "englishName", "name", "chinaName", "productName", "title").trim();
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k);
            if (v != null && v != JSONObject.NULL) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
            }
        }
        return "";
    }

    private static int intOf(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k);
            if (v instanceof Number) return ((Number) v).intValue();
            if (v != null) try { return Integer.parseInt(String.valueOf(v)); } catch (Exception ignored) {}
        }
        return 0;
    }

    private static JSONArray arrayFrom(JSONObject root) {
        JSONObject data = root.optJSONObject("data");
        if (data != null) {
            JSONArray a = data.optJSONArray("bussData");
            if (a != null) return a;
        }
        JSONArray a = root.optJSONArray("bussData");
        return a == null ? new JSONArray() : a;
    }

    private static JSONObject objectFrom(JSONObject root) {
        JSONObject data = root.optJSONObject("data");
        if (data == null) return root;
        JSONObject o = data.optJSONObject("bussData");
        return o != null ? o : data;
    }

    private static String message(JSONObject root, String fallback) {
        JSONObject data = root.optJSONObject("data");
        String s = data == null ? "" : first(data, "errMsg", "message", "msg");
        if (s.isEmpty()) s = first(root, "errMsg", "message", "msg");
        return s.isEmpty() ? fallback : s;
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
