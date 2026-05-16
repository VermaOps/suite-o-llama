package burp;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.json.*;

public class OpenAIProvider implements LLMProvider {
    private final ExtensionState state;
    private final ExecutorService executor;
    private HttpURLConnection currentConnection;
    private volatile boolean cancelled = false;
    private Future<?> currentRequest;
    
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final int DEFAULT_TIMEOUT = 30000;
    
    public OpenAIProvider(ExtensionState state) {
        this.state = state;
        this.executor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Suite-o-llama-OpenAI");
            return t;
        });
    }
    
    @Override
    public String getProviderName() {
        return "OpenAI";
    }
    
    @Override
    public void updateSettings(ExtensionState state) {
        // Settings are read from state directly
    }
    
    private String getApiKey() {
        return state.getOpenAiApiKey();
    }
    
    private String getBaseUrl() {
        String url = state.getOpenAiBaseUrl();
        return (url != null && !url.isEmpty()) ? url : DEFAULT_BASE_URL;
    }
    
    private String getModel() {
        return state.getOpenAiModel();
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
            throw new Exception("OpenAI API key not configured");
        }
        
        URL url = new URL(getBaseUrl() + "/chat/completions");
        currentConnection = (HttpURLConnection) url.openConnection();
        currentConnection.setRequestMethod("POST");
        currentConnection.setRequestProperty("Content-Type", "application/json");
        currentConnection.setRequestProperty("Authorization", "Bearer " + apiKey);
        currentConnection.setDoOutput(true);
        currentConnection.setConnectTimeout(DEFAULT_TIMEOUT);
        currentConnection.setReadTimeout(DEFAULT_TIMEOUT);
        
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        
        JSONArray messages = new JSONArray();
        messages.put(message);
        
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", state.getTemperature());
        requestBody.put("max_tokens", state.getMaxTokens());
        requestBody.put("stream", false);
        
        try (OutputStream os = currentConnection.getOutputStream()) {
            os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
        }
        
        int responseCode = currentConnection.getResponseCode();
        if (responseCode != 200) {
            String error = readErrorStream(currentConnection);
            throw new Exception("OpenAI API error (" + responseCode + "): " + error);
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
        JSONArray choices = jsonResponse.getJSONArray("choices");
        if (choices.length() > 0) {
            JSONObject choice = choices.getJSONObject(0);
            JSONObject messageObj = choice.getJSONObject("message");
            return messageObj.getString("content");
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
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
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
            return models;
        }
        
        try {
            URL url = new URL(getBaseUrl() + "/models");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(500);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return models;
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
                // ONLY include chat-completion capable models
                if (isChatModel(modelId)) {
                    models.add(modelId);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            // Return empty list
        }
        
        return models;
    }
    
    private boolean isChatModel(String modelId) {
        // Chat completion models
        if (modelId.startsWith("gpt-")) return true;
        if (modelId.startsWith("o1")) return true;
        if (modelId.startsWith("o3")) return true;
        if (modelId.equals("gpt-4")) return true;
        if (modelId.equals("gpt-4-turbo")) return true;
        if (modelId.equals("gpt-3.5-turbo")) return true;
        
        // EXCLUDE non-chat models
        if (modelId.contains("embedding")) return false;
        if (modelId.contains("whisper")) return false;
        if (modelId.contains("dall-e")) return false;
        if (modelId.contains("tts")) return false;
        if (modelId.contains("moderation")) return false;
        if (modelId.contains("instruct") && !modelId.contains("gpt")) return false;
        
        // Legacy completion-only models (not chat)
        if (modelId.equals("text-davinci-003")) return false;
        if (modelId.equals("text-davinci-002")) return false;
        if (modelId.equals("text-curie-001")) return false;
        if (modelId.equals("text-babbage-001")) return false;
        if (modelId.equals("text-ada-001")) return false;
        
        return modelId.startsWith("gpt") || modelId.startsWith("o1") || modelId.startsWith("o3");
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
        return false;
    }
}