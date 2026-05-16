package burp;

import java.util.List;

public interface LLMProvider {
    // Core methods
    void generateAsync(String prompt, String model, String conversationContext, ResponseCallback callback);
    boolean checkHealth();
    List<String> getAvailableModels();
    boolean isModelAvailable(String modelName);
    void cancel();
    void shutdown();
    
    // Configuration
    String getProviderName();
    void updateSettings(ExtensionState state);
    
    interface ResponseCallback {
        void onSuccess(String response, long timeMs, int estimatedTokens);
        void onError(String error);
        void onCancelled(long cancelTimeMs);
    }
}