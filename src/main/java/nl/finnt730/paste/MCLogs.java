package nl.finnt730.paste;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Common Minecraft Logging site owned by Aternos. Used by Aternos, Prism Launcher, Crash Assistant, CrashDetector, NotEnoughCrashes, Luna Pixel Studios and many others.
 */
public sealed class MCLogs implements PasteSite permits GnomeBotPaste,CAPaste {

    private static final String API_URL = "https://api.mclo.gs/1/log";
    private static final int MAX_LINES = 25000;
    private static final int MAX_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    @Override
    public boolean largeEnough(String content) {
        // Check line count (more efficient than splitting)
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
                if (lines > MAX_LINES) {
                    return false;
                }
            }
        }
        
        // Check size in bytes (UTF-8)
        int sizeInBytes = content.getBytes(StandardCharsets.UTF_8).length;
        return sizeInBytes <= MAX_SIZE_BYTES;
    }

    @Override
    public boolean supportsGZip() {
        return false; // MCLogs API doesn't require gzip compression
    }

    @Override
    public String getResultURL(String content) {
        try {
            
            // Prepare the POST data
            String postData = "content=" + URLEncoder.encode(content, StandardCharsets.UTF_8.name());
            byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);
            
            // Create connection
            URL url = new URL(API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);  // 5 seconds
            connection.setReadTimeout(30000);    // 30 seconds
            
            // Send the data
            try (OutputStream os = connection.getOutputStream()) {
                os.write(postDataBytes);
            }
            
            // Check response code
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return null;
            }
            
            // Read the response
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                
                // Parse the response to extract the main URL
                String responseStr = response.toString();
                int urlIndex = responseStr.indexOf("\"url\":\"");
                if (urlIndex != -1) {
                    urlIndex += 7;  // Skip "url\":"
                    int endIndex = responseStr.indexOf("\"", urlIndex);
                    if (endIndex != -1) {
                        return responseStr.substring(urlIndex, endIndex).replace("\\", "");
                    }
                }
            }
            
            return null; // Failed to extract URL
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return "mclogs";
	}
}