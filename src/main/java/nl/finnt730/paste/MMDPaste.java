package nl.finnt730.paste;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * A Stikked based paste site. Supported by CrashDetector and has a good GUI for manual pasting. Supports many languages
 */
public final class MMDPaste implements PasteSite {

    private static final String API_ENDPOINT = "https://paste.mikumikudance.jp/en/api/create";
    private static final int MAX_SIZE_BYTES = 16 * 1024 * 1024; // 16MB
    
    @Override
    public boolean largeEnough(String content) {
        // Only check size (no line limit)
        return content.getBytes(StandardCharsets.UTF_8).length <= MAX_SIZE_BYTES;
    }

    @Override
    public boolean supportsGZip() {
        return false; // MMDPaste doesn't require gzip compression
    }

    @Override
    public String getResultURL(String content) throws java.io.IOException {
        try {
            
            // Prepare parameters
            String params = String.format(
                "text=%s&title=CrashLog&name=ForgeBot&private=1&lang=java",
                URLEncoder.encode(content, StandardCharsets.UTF_8.name())
            );
            
            // Create connection
            URL url = new URL(API_ENDPOINT);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", String.valueOf(params.length()));
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);  // 5 seconds
            connection.setReadTimeout(30000);    // 30 seconds
            
            // Send the data
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = params.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Check response code
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new java.io.IOException("HTTP " + responseCode);
            }
            
            // Read the response
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                
                String responseStr = response.toString();
                if (responseStr.startsWith("Error:") || responseStr.isEmpty()) {
                    throw new java.io.IOException("API Error: " + responseStr);
                }
                
                // Ensure the URL is properly formatted (add https:// if missing)
                if (!responseStr.startsWith("http")) {
                    return "https://paste.mikumikudance.jp/en/" + responseStr;
                }
                
                return responseStr;
            }
        } catch (java.io.IOException e) { throw e; } catch (Exception e) { throw new java.io.IOException(e); }
    }

	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return "mmd";
	}
}