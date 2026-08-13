package nl.finnt730.paste;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

public class PasteReader {
    private static final Pattern MCLOGS_PATTERN = Pattern.compile("(?:https?://)?(?:api\\.)?mclo\\.gs/(?:1/raw/)?([a-zA-Z0-9]+)");
    private static final Pattern GNOMEBOT_PATTERN = Pattern.compile("(?:https?://)?gnomebot\\.dev/paste/mclogs/([a-zA-Z0-9]+)");
    private static final Pattern CAPASTE_PATTERN = Pattern.compile("(?:https?://)?kostromdan\\.dev/paste/mclogs/([a-zA-Z0-9]+)");
    private static final Pattern PASTESDEV_PATTERN = Pattern.compile("(?:https?://)?pastes\\.dev/([a-zA-Z0-9]+)");
    private static final Pattern BYTEBIN_PATTERN = Pattern.compile("(?:https?://)?bytebin\\.lucko\\.me/([a-zA-Z0-9]+)");
    private static final Pattern CD_PATTERN = Pattern.compile("(?:https?://)?asbestosstar\\.egoism\\.jp/crash_detector/paste/endpoint\\.php(?:\\?id=|/logs/)([a-zA-Z0-9]+)(?:\\.gz)?");
    private static final Pattern MMD_PATTERN = Pattern.compile("(?:https?://)?paste\\.(?:mikumikudance\\.jp|centos\\.org)/(?:en/)?(?:view/)?(?:raw/)?([a-zA-Z0-9]+)");
    private static final Pattern KDAN_PATTERN = Pattern.compile("(?:https?://)?p\\.kdan\\.dev/([a-zA-Z0-9]+)");
    private static final Pattern SECURELOGGER_PATTERN = Pattern.compile("(?:https?://)?securelogger\\.net/files/([a-zA-Z0-9\\-]+)(?:\\.tar\\.gz)?");

    public static String read(String url) throws java.io.IOException {
        Matcher m;
        if ((m = MCLOGS_PATTERN.matcher(url)).find()) return readRaw("https://api.mclo.gs/1/raw/" + m.group(1), false);
        if ((m = GNOMEBOT_PATTERN.matcher(url)).find()) return readRaw("https://api.mclo.gs/1/raw/" + m.group(1), false);
        if ((m = CAPASTE_PATTERN.matcher(url)).find()) return readRaw("https://api.mclo.gs/1/raw/" + m.group(1), false);
        if ((m = PASTESDEV_PATTERN.matcher(url)).find()) return readRaw("https://api.pastes.dev/" + m.group(1), false);
        if ((m = BYTEBIN_PATTERN.matcher(url)).find()) return readRaw("https://bytebin.lucko.me/" + m.group(1), false);
        if ((m = CD_PATTERN.matcher(url)).find()) return readRaw("https://asbestosstar.egoism.jp/crash_detector/paste/endpoint.php/logs/" + m.group(1) + ".gz", true);
        if ((m = MMD_PATTERN.matcher(url)).find()) {
            String id = m.group(1);
            if (url.contains("centos.org")) {
                return readRaw("https://paste.centos.org/view/raw/" + id, false);
            } else {
                return readRaw("https://paste.mikumikudance.jp/view/raw/" + id, false);
            }
        }
        if ((m = KDAN_PATTERN.matcher(url)).find()) return readRaw("https://api.mclo.gs/1/raw/" + m.group(1), false);
        if ((m = SECURELOGGER_PATTERN.matcher(url)).find()) {
            String id = m.group(1);
            return readRaw("https://securelogger.net/files/" + id + ".tar.gz", true);
        }
        return null;
    }

    public static boolean isMcLogsInstance(String url) {
        return MCLOGS_PATTERN.matcher(url).find() || 
               GNOMEBOT_PATTERN.matcher(url).find() || 
               CAPASTE_PATTERN.matcher(url).find() ||
               KDAN_PATTERN.matcher(url).find();
    }
    
    public static String getMcLogsId(String url) {
        Matcher m;
        if ((m = MCLOGS_PATTERN.matcher(url)).find()) return m.group(1);
        if ((m = GNOMEBOT_PATTERN.matcher(url)).find()) return m.group(1);
        if ((m = CAPASTE_PATTERN.matcher(url)).find()) return m.group(1);
        if ((m = KDAN_PATTERN.matcher(url)).find()) return m.group(1);
        return null;
    }

    private static String readRaw(String urlString, boolean isGzipped) throws java.io.IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "ForgeBot (https://github.com/FinnT730/forgebot)");
        
        int code = conn.getResponseCode();
        if (code != 200) throw new java.io.IOException("HTTP " + code);

        InputStream is = conn.getInputStream();
        if (isGzipped) is = new GZIPInputStream(is);
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        }
    }
}
