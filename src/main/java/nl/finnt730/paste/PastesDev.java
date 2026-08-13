package nl.finnt730.paste;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import nl.finnt730.Global;

/**
 * Sucessor to ByteBin, however unlike ByteBin it is not Raw only (SecureLogger is though). Supported by NotEnoughCrashes, CrashDetector, and Spark. Supports GZipping and large amounts of content, best for large amounts of content.
 */
public final class PastesDev implements PasteSite {

    private static final String API_BASE_URL = "https://api.pastes.dev/";
    private static final String USER_AGENT = "ForgeBot (https://github.com/FinnT730/forgebot)";
    private static final int MAX_GZIPPED_SIZE_BYTES = 15 * 1024 * 1024; // 15MB for gzipped content
    
    @Override
    public boolean largeEnough(String content) {
        // No line limit for bytebin instances
        // Check if content can be compressed under 15MB
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
        return true; // bytebin supports gzip compression
    }

    @Override
    public String getResultURL(String content) throws java.io.IOException {
        try {
            // Verify content size
            if (!largeEnough(content)) {
                return null;
            }
            
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            byte[] compressedBytes = Global.compressGZIP(contentBytes);
            
            // Create connection
            URL url = new URL(API_BASE_URL + "post");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);  // 5 seconds
            connection.setReadTimeout(30000);    // 30 seconds
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            connection.setRequestProperty("Content-Encoding", "gzip");
            connection.setDoOutput(true);

            // Send the gzipped data
            try (OutputStream os = connection.getOutputStream()) {
                os.write(compressedBytes);
            }

            // Check response
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_CREATED && 
                responseCode != HttpURLConnection.HTTP_OK) {
                throw new java.io.IOException("HTTP " + responseCode);
            }
            
            // Try to get the key from Location header first
            String location = connection.getHeaderField("Location");
            if (location != null && !location.isEmpty()) {
                // Extract key from location (should be like "/key" or "https://api.pastes.dev/key")
                String key = location;
                if (key.startsWith("/")) {
                    key = key.substring(1);
                } else if (key.startsWith(API_BASE_URL)) {
                    key = key.substring(API_BASE_URL.length());
                }
                return "https://pastes.dev/" + key;
            }
            
            // If no Location header, try to parse JSON response
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                
                // Parse JSON response {"key": "aabbcc"}
                String responseStr = response.toString();
                int keyIndex = responseStr.indexOf("\"key\":\"");
                if (keyIndex != -1) {
                    keyIndex += 7; // Skip "key\":"
                    int endIndex = responseStr.indexOf("\"", keyIndex);
                    if (endIndex != -1) {
                        String key = responseStr.substring(keyIndex, endIndex);
                        return "https://pastes.dev/" + key;
                    }
                }
            }
            
            throw new java.io.IOException("Failed to extract key from response");
        } catch (java.io.IOException e) { throw e; } catch (Exception e) { throw new java.io.IOException(e); }
    }

	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return "pastesdev";
	}
}