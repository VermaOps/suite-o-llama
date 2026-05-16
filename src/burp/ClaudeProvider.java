package burp;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.json.*;

public class ClaudeProvider implements LLMProvider {
    private final ExtensionState state;
    private final ExecutorService executor;
    private HttpURLConnection currentConnection;
    private volatile boolean cancelled = false;
    private Future<?> currentRequest;
    
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final int DEFAULT_TIMEOUT = 30000;
    
    public ClaudeProvider(ExtensionState state) {
        this.state = state;
        this.executor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Suite-o-llama-Claude");
            return t;
        });
    }
    
    @Override
    public String getProviderName() {
        return "Claude";
    }
    
    @Override
    public void updateSettings(ExtensionState state) {
        // Settings are read from state directly
    }
    
    private String getApiKey() {
        return state.getClaudeApiKey();
    }
    
    private String getBaseUrl() {
        String url = state.getClaudeBaseUrl();
        return (url != null && !url.isEmpty()) ? url : DEFAULT_BASE_URL;
    }
    
    private String getModel() {
        return state.getClaudeModel();
    }
    
    @Override
    public void generateAsync(String prompt, String model, String conversationContext, ResponseCallback callback) {
        if (currentRequest != null && !currentRequest.isDone()) {
            currentRequest.cancel(true);
        }
        
        cancelled = false;
        final String effectiveModel = (model != null && !model.isEmpty()) ? model : getModel();
        final String augmentedPrompt = (conversationContext != null && !conversationContext.trim().isEmpty()) 
            ? conversationContext + "\n\n" + prompt 
            : prompt;
        
        currentRequest = executor.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();
                String response = generate(effectiveModel, augmentedPrompt);
                long elapsed = System.currentTimeMillis() - startTime;
                int tokens = response.length() / 4;
                
                if (!Thread.currentThread().isInterrupted() && !cancelled) {
                    callback.onSuccess(response, elapsed, tokens);
                } else {
                    callback.onCancelled(elapsed);
                }
            } catch (InterruptedException e) {
                callback.onCancelled(0);
            } catch (Exception e) {
                if (!cancelled) {
                    callback.onError(e.getMessage());
                } else {
                    callback.onCancelled(0);
                }
            } finally {
                currentConnection = null;
            }
        });
    }
    
    private String generate(String model, String prompt) throws Exception {
        cancelled = false;
        
        if (currentConnection != null) {
            try {
                currentConnection.disconnect();
            } catch (Exception e) {}
            currentConnection = null;
        }
        
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Claude API key not configured");
        }
        
        URL url = new URL(getBaseUrl() + "/messages");
        currentConnection = (HttpURLConnection) url.openConnection();
        currentConnection.setRequestMethod("POST");
        currentConnection.setRequestProperty("Content-Type", "application/json");
        currentConnection.setRequestProperty("x-api-key", apiKey);
        currentConnection.setRequestProperty("anthropic-version", "2023-06-01");
        currentConnection.setDoOutput(true);
        currentConnection.setConnectTimeout(DEFAULT_TIMEOUT);
        currentConnection.setReadTimeout(DEFAULT_TIMEOUT);
        
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messages.put(message);
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", state.getMaxTokens());
        requestBody.put("temperature", state.getTemperature());
        
        try (OutputStream os = currentConnection.getOutputStream()) {
            os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
        }
        
        int responseCode = currentConnection.getResponseCode();
        if (responseCode != 200) {
            String error = readErrorStream(currentConnection);
            throw new Exception("Claude API error (" + responseCode + "): " + error);
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(currentConnection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (cancelled || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Generation cancelled");
                }
                response.append(line);
            }
        }
        
        JSONObject jsonResponse = new JSONObject(response.toString());
        JSONArray content = jsonResponse.getJSONArray("content");
        if (content.length() > 0) {
            JSONObject contentObj = content.getJSONObject(0);
            return contentObj.getString("text");
        }
        
        return "";
    }
    
    private String readErrorStream(HttpURLConnection conn) {
        try {
            InputStream errorStream = conn.getErrorStream();
            if (errorStream != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(errorStream));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {}
        return "";
    }
    
    @Override
    public void cancel() {
        cancelled = true;
        if (currentConnection != null) {
            currentConnection.disconnect();
        }
        if (currentRequest != null && !currentRequest.isDone()) {
            currentRequest.cancel(true);
        }
    }
    
    @Override
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }
    
    @Override
    public boolean checkHealth() {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        
        try {
            URL url = new URL(getBaseUrl() + "/models");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return models; // Return empty list, no hardcoded fallback
        }
        
        try {
            URL url = new URL(getBaseUrl() + "/models");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                return models; // Return empty list on error
            }
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            JSONObject json = new JSONObject(response.toString());
            JSONArray data = json.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                String modelId = data.getJSONObject(i).getString("id");
                if (modelId.startsWith("claude")) {
                    models.add(modelId);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            // Return empty list, no hardcoded fallback
        }
        
        return models;
    }
    
    @Override
    public boolean isModelAvailable(String modelName) {
        if (modelName == null) {
            return false;
        }
        List<String> models = getAvailableModels();
        for (String model : models) {
            if (model.equals(modelName)) {
                return true;
            }
        }
        return modelName != null && modelName.startsWith("claude-");
    }
}