package burp;

import java.util.HashMap;
import java.util.Map;

public class ProviderFactory {
    private final ExtensionState state;
    private final Map<ProviderType, LLMProvider> providers;
    private LLMProvider activeProvider;
    
    public ProviderFactory(ExtensionState state) {
        this.state = state;
        this.providers = new HashMap<>();
        initializeProviders();
    }
    
    private void initializeProviders() {
        providers.put(ProviderType.OLLAMA, new OllamaClient(state));
        providers.put(ProviderType.OPENAI, new OpenAIProvider(state));
        providers.put(ProviderType.CLAUDE, new ClaudeProvider(state));
    }
    
    public LLMProvider getProvider(ProviderType type) {
        LLMProvider provider = providers.get(type);
        if (provider != null) {
            provider.updateSettings(state);
        }
        return provider;
    }
    
    public LLMProvider getActiveProvider() {
        ProviderType activeType = ProviderType.fromString(state.getActiveProvider());
        activeProvider = getProvider(activeType);
        return activeProvider;
    }
    
    public void setActiveProvider(ProviderType type) {
        state.setActiveProvider(type.getName());
        activeProvider = getProvider(type);
    }
    
    public ListModelsResult listAllModels() {
        ListModelsResult result = new ListModelsResult();
        
        for (ProviderType type : ProviderType.values()) {
            LLMProvider provider = getProvider(type);
            if (provider != null) {
                for (String model : provider.getAvailableModels()) {
                    result.addModel(type, model);
                }
            }
        }
        
        return result;
    }
    
    public static class ListModelsResult {
        private final Map<ProviderType, java.util.List<String>> models = new HashMap<>();
        
        public void addModel(ProviderType type, String model) {
            models.computeIfAbsent(type, k -> new java.util.ArrayList<>()).add(model);
        }
        
        public java.util.List<String> getAllModels() {
            java.util.List<String> all = new java.util.ArrayList<>();
            for (Map.Entry<ProviderType, java.util.List<String>> entry : models.entrySet()) {
                for (String model : entry.getValue()) {
                    all.add(entry.getKey().getName() + ":" + model);
                }
            }
            return all;
        }
        
        public java.util.List<String> getModelsForProvider(ProviderType type) {
            return models.getOrDefault(type, new java.util.ArrayList<>());
        }
    }
    
    public void shutdownAll() {
        for (LLMProvider provider : providers.values()) {
            provider.shutdown();
        }
    }
}