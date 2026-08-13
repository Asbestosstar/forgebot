package nl.finnt730.paste;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import nl.finnt730.Global;

/**
 * Paste implementation for CrashDetector's PHP paste service.
 * Uploads gzipped logs and returns a decorated view link.Source code https://pagure.io/CrashDetectorMC/blob/main/f/paste/endpoint.php
 */
public final class CrashDetectorPaste implements PasteSite {

    private static final String API_BASE_URL = "https://asbestosstar.egoism.jp/crash_detector/paste/endpoint.php";
    private static final String USER_AGENT = "ForgeBot (https://github.com/FinnT730/forgebot)";
    private static final int MAX_GZIPPED_SIZE_BYTES = 20 * 1024 * 1024; // 20MB, matches server limit

    @Override
    public boolean largeEnough(String content) {
        try {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            byte[] compressed = Global.compressGZIP(contentBytes);
            return compressed.length <= MAX_GZIPPED_SIZE_BYTES;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean supportsGZip() {
        return true; // the PHP endpoint expects gzipped logs
    }


 @Override
public String getResultURL(String content) throws java.io.IOException {
    try {
        if (!largeEnough(content)) {
            return null;
        }

        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] compressedBytes = Global.compressGZIP(contentBytes);

        // Connect to PHP endpoint with ?action=save_log
        URL url = new URL(API_BASE_URL + "?action=save_log");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setRequestProperty("Content-Encoding", "gzip"); // keep gzip
        connection.setRequestProperty("Content-Length", String.valueOf(compressedBytes.length));
        connection.setDoOutput(true);

        // Send gzipped log as raw binary
        try (OutputStream os = connection.getOutputStream()) {
            os.write(compressedBytes);
            os.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new java.io.IOException("HTTP " + responseCode);
        }

        // Read JSON response
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            String responseStr = response.toString();

            // Look for "link":"..."
            int linkIndex = responseStr.indexOf("\"link\":\"");
            if (linkIndex != -1) {
                linkIndex += 8; // skip "link":" 
                int endIndex = responseStr.indexOf("\"", linkIndex);
                if (endIndex != -1) {
                    String link = responseStr.substring(linkIndex, endIndex);
                    // Fix escaped forward slashes from JSON
                    link = link.replace("\\/", "/");
                    return link;
                }
            }
        }

        throw new java.io.IOException("Failed to extract link from response");
    } catch (java.io.IOException e) { throw e; } catch (Exception e) { throw new java.io.IOException(e); }
}

   



    @Override
    public String getId() {
        return "cdpaste";
    }
}
