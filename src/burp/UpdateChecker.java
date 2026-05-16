package burp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

public class UpdateChecker {
    private final ExtensionState state;
    
    public UpdateChecker(ExtensionState state) {
        this.state = state;
    }
    
        /**
         * Gets version comparison info
         * return Object with {hasUpdate: boolean, current: string, latest: string, latestInfo: JSONObject}
         */
        public JSONObject getUpdateInfo() {
            JSONObject result = new JSONObject();
            
            try {
                URL url = new URL("https://api.github.com/repos/VermaOps/suite-o-llama/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "Suite-o-llama-Burp-Extension");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject latestRelease = new JSONObject(response.toString());
                    String latestVersion = latestRelease.getString("tag_name");
                    latestVersion = latestVersion.replaceFirst("^v", "").trim();
                    String currentVersion = state.getVersion();
                    
                    result.put("hasUpdate", isNewerVersion(latestVersion, currentVersion));
                    result.put("current", currentVersion);
                    result.put("latest", latestVersion);
                    result.put("latestInfo", latestRelease);
                    
                } else {
                    result.put("hasUpdate", false);
                    result.put("error", "GitHub API returned: " + conn.getResponseCode());
                }
                conn.disconnect();
            } catch (Exception e) {
                result.put("hasUpdate", false);
                result.put("error", e.getMessage());
            }
            
            return result;
        }

    // checkForUpdates() for simple boolean check
    public boolean checkForUpdates() {
        JSONObject info = getUpdateInfo();
        return info.optBoolean("hasUpdate", false);
    }
    
    /**
     * Compares two version strings (e.g., "2.2.0" vs "2.1.0")
     * param latest The latest version from GitHub
     * param current The current installed version
     * return true if latest > current
     */
    private boolean isNewerVersion(String latest, String current) {
        // Handle null or empty
        if (latest == null || latest.isEmpty()) return false;
        if (current == null || current.isEmpty()) return true;
        
        // Split version strings into parts
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");
        
        // Compare each part numerically
        int maxLength = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < maxLength; i++) {
            int latestNum = 0;
            int currentNum = 0;
            
            try {
                if (i < latestParts.length) {
                    latestNum = Integer.parseInt(latestParts[i]);
                }
                if (i < currentParts.length) {
                    currentNum = Integer.parseInt(currentParts[i]);
                }
            } catch (NumberFormatException e) {
                // If non-numeric, compare as strings
                String latestPart = (i < latestParts.length) ? latestParts[i] : "";
                String currentPart = (i < currentParts.length) ? currentParts[i] : "";
                if (!latestPart.equals(currentPart)) {
                    return latestPart.compareTo(currentPart) > 0;
                }
                continue;
            }
            
            if (latestNum > currentNum) return true;
            if (latestNum < currentNum) return false;
        }
        
        // All parts are equal
        return false;
    }
    
    /**
     * Gets the latest release info from GitHub
     * return JSONObject with release info or null if error
     */
    public JSONObject getLatestReleaseInfo() {
        try {
            URL url = new URL("https://api.github.com/repos/VermaOps/suite-o-llama/releases/latest");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "Suite-o-llama-Burp-Extension");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();
                
                return new JSONObject(response.toString());
            }
            conn.disconnect();
        } catch (Exception e) {
            // Silent fail
        }
        return null;
    }
}