package burp;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.json.*;

public class OllamaClient implements LLMProvider {
    private final ExtensionState state;
    private final ExecutorService executor;
    private Future<?> currentRequest;
    private volatile HttpURLConnection currentConnection;
    private volatile boolean cancelled = false;
    private volatile boolean streaming = false;
    private long requestStartTime = 0;
    
    public OllamaClient(ExtensionState state) {
        this.state = state;
        this.executor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Suite-o-llama-Worker");
            return t;
        });
    }
    
    @Override
    public String getProviderName() {
        return "Ollama";
    }
    
    @Override
    public void updateSettings(ExtensionState state) {
        // Settings already available via this.state
    }
    
    @Override
    public void generateAsync(String prompt, String model, String conversationContext, ResponseCallback callback) {
        if (currentRequest != null && !currentRequest.isDone()) {
            currentRequest.cancel(true);
        }
        
        cancelled = false;
        streaming = false;
        
        final String augmentedPrompt;
        if (conversationContext != null && !conversationContext.trim().isEmpty()) {
            augmentedPrompt = conversationContext + "\n\n" + prompt;
        } else {
            augmentedPrompt = prompt;
        }
        
        currentRequest = executor.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();
                String response = generate(augmentedPrompt, model);
                long elapsed = System.currentTimeMillis() - startTime;
                int tokens = estimateTokens(response);
                
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
                    callback.onCancelled(System.currentTimeMillis() - requestStartTime);
                }
            } finally {
                currentConnection = null;
            }
        });
    }
    
    private String generate(String prompt, String model) throws Exception {
        cancelled = false;
        streaming = true;
        requestStartTime = System.currentTimeMillis();
    
        if (currentConnection != null) {
            try {
                currentConnection.disconnect();
            } catch (Exception e) {}
            currentConnection = null;
        }

        URL url = new URL(state.getOllamaEndpoint() + "/api/generate");
        currentConnection = (HttpURLConnection) url.openConnection();
        
        try {
            currentConnection.setRequestMethod("POST");
            currentConnection.setRequestProperty("Content-Type", "application/json");
            currentConnection.setDoOutput(true);
            currentConnection.setConnectTimeout(5000);
            currentConnection.setReadTimeout(150000);
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", true);
            requestBody.put("options", new JSONObject()
                .put("temperature", state.getTemperature())
                .put("num_predict", state.getMaxTokens())
            );
            
            try (OutputStream os = currentConnection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = currentConnection.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Ollama returned status " + responseCode);
            }
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(currentConnection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (cancelled || Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Generation cancelled by user");
                    }
                    
                    if (!line.trim().isEmpty()) {
                        JSONObject jsonResponse = new JSONObject(line);
                        if (jsonResponse.has("response")) {
                            String token = jsonResponse.getString("response");
                            response.append(token);
                        }
                    }
                }
            }
            
            return response.toString();
            
        } finally {
            currentConnection = null;
            streaming = false;
        }
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
        try {
            URL url = new URL(state.getOllamaEndpoint() + "/api/tags");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
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
        String[] modelArray = getAvailableModelsArray();
        for (String model : modelArray) {
            models.add(model);
        }
        return models;
    }
    
    private String[] getAvailableModelsArray() {
        try {
            URL url = new URL(state.getOllamaEndpoint() + "/api/tags");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            JSONObject json = new JSONObject(response.toString());
            JSONArray models = json.getJSONArray("models");
            String[] modelNames = new String[models.length()];
            for (int i = 0; i < models.length(); i++) {
                modelNames[i] = models.getJSONObject(i).getString("name");
            }
            return modelNames;
            
        } catch (Exception e) {
            state.getStderr().println("Error fetching models: " + e.getMessage());
            return new String[0];
        }
    }
    
    @Override
    public boolean isModelAvailable(String modelName) {
        String[] models = getAvailableModelsArray();
        for (String model : models) {
            if (model.equals(modelName) || model.startsWith(modelName + ":")) {
                return true;
            }
        }
        return false;
    }
    
    private int estimateTokens(String text) {
        return text.length() / 4;
    }
}