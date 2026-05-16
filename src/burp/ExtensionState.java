package burp;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

public class ExtensionState {
    private final IBurpExtenderCallbacks callbacks;
    private final IExtensionHelpers helpers;
    private final PrintWriter stdout;
    private final PrintWriter stderr;
    
    // Ollama settings
    private String ollamaEndpoint = "http://127.0.0.1:11434";
    private String analysisModel = "qwen2.5:7b-instruct";
    private String payloadModel = "qwen2.5-coder:7b";
    private double temperature = 0.7;
    private int maxTokens = 4096;          // 4k tokens - safe & fast
    private int maxContextSize = 16384;    // 16k characters - fits most requests
    
    // Security settings
    private boolean redactAuthHeaders = true;
    private boolean redactCookies = true;
    private String extensionVersion;

    // External provider settings
    private String activeProvider = "ollama";  // ollama, openai, claude
    
    // OpenAI settings
    private String openAiApiKey = "";
    private String openAiModel = "gpt-4";
    private String openAiBaseUrl = "https://api.openai.com/v1";
    
    // Claude settings
    private String claudeApiKey = "";
    private String claudeModel = "claude-3-5-sonnet-20241022";
    private String claudeBaseUrl = "https://api.anthropic.com/v1";

    // Saved prompts
    private Map<String, String> savedPrompts = new HashMap<>();
    
    // Preferences
    private Preferences prefs;
    
    public ExtensionState(IBurpExtenderCallbacks callbacks, IExtensionHelpers helpers, 
                         PrintWriter stdout, PrintWriter stderr) {
        this.callbacks = callbacks;
        this.helpers = helpers;
        this.stdout = stdout;
        this.stderr = stderr;
        this.prefs = Preferences.userNodeForPackage(BurpExtender.class);
        this.extensionVersion = BurpExtender.VERSION;

        loadSettings();
        loadSavedPrompts();
    }
    
    private void loadSettings() {
        ollamaEndpoint = prefs.get("ollama_endpoint", ollamaEndpoint);
        analysisModel = prefs.get("analysis_model", analysisModel);
        payloadModel = prefs.get("payload_model", payloadModel);
        temperature = prefs.getDouble("temperature", temperature);
        maxTokens = prefs.getInt("max_tokens", maxTokens);
        maxContextSize = prefs.getInt("max_context_size", maxContextSize);
        redactAuthHeaders = prefs.getBoolean("redact_auth", redactAuthHeaders);
        redactCookies = prefs.getBoolean("redact_cookies", redactCookies);

        // External provider settings
        activeProvider = prefs.get("active_provider", "ollama");
        openAiApiKey = prefs.get("openai_api_key", "");
        openAiModel = prefs.get("openai_model", "gpt-4");
        openAiBaseUrl = prefs.get("openai_base_url", "https://api.openai.com/v1");
        claudeApiKey = prefs.get("claude_api_key", "");
        claudeModel = prefs.get("claude_model", "claude-3-5-sonnet-20241022");
        claudeBaseUrl = prefs.get("claude_base_url", "https://api.anthropic.com/v1");
    }
    
    public void saveSettings() {
        prefs.put("ollama_endpoint", ollamaEndpoint);
        prefs.put("analysis_model", analysisModel);
        prefs.put("payload_model", payloadModel);
        prefs.putDouble("temperature", temperature);
        prefs.putInt("max_tokens", maxTokens);
        prefs.putInt("max_context_size", maxContextSize);
        prefs.putBoolean("redact_auth", redactAuthHeaders);
        prefs.putBoolean("redact_cookies", redactCookies);
        prefs.put("extension_version", extensionVersion);
        
        // External provider settings
        prefs.put("active_provider", activeProvider);
        prefs.put("openai_api_key", openAiApiKey);
        prefs.put("openai_model", openAiModel);
        prefs.put("openai_base_url", openAiBaseUrl);
        prefs.put("claude_api_key", claudeApiKey);
        prefs.put("claude_model", claudeModel);
        prefs.put("claude_base_url", claudeBaseUrl);
    }
    
    private void loadSavedPrompts() {
        String promptsData = prefs.get("saved_prompts", "");
        if (!promptsData.isEmpty()) {
            String[] entries = promptsData.split("\\|\\|\\|");
            for (String entry : entries) {
                int idx = entry.indexOf(":::");
                if (idx > 0) {
                    String name = entry.substring(0, idx);
                    String prompt = entry.substring(idx + 3);
                    savedPrompts.put(name, prompt);
                }
            }
        }
        
        // Update default prompts with new variable names
    if (savedPrompts.isEmpty()) {
        savedPrompts.put("Vulnerability Analysis", 
            "Analyze this HTTP request for security vulnerabilities:\n\n{{full_request}}\n\n" +
            "Focus on: SQL injection, XSS, authentication issues, and input validation flaws.");
        
        savedPrompts.put("Response Analysis", // NEW prompt
            "Analyze this HTTP response:\n\n" +
            "Response Headers:\n{{res_headers}}\n\n" +
            "Response Body:\n{{res_body}}\n\n" +
            "Look for: sensitive data exposure, security headers, error messages, and potential issues.");
        
        savedPrompts.put("SQLi Detection",
            "Check if this request is vulnerable to SQL injection:\n\n{{full_request}}\n\n" +
            "Provide specific payloads for testing.");
        
        savedPrompts.put("XSS Analysis",
            "Analyze this request for XSS vulnerabilities:\n\n{{full_request}}\n\n" +
            "Suggest payloads for reflected, stored, and DOM-based XSS.");
        
        savedPrompts.put("Auth Bypass",
            "Analyze authentication and authorization in this request:\n\n{{full_request}}\n\n" +
            "Suggest bypass techniques.");
        
        savedPrompts.put("API Security",
            "Review this API request for security issues:\n\n{{full_request}}\n\n" +
            "Check: authentication, rate limiting, input validation, data exposure.");
    }
    }
    
    public void saveSavedPrompts() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : savedPrompts.entrySet()) {
            if (sb.length() > 0) sb.append("|||");
            sb.append(entry.getKey()).append(":::").append(entry.getValue());
        }
        prefs.put("saved_prompts", sb.toString());
    }
    
    // Getters and setters
    public IBurpExtenderCallbacks getCallbacks() { return callbacks; }
    public IExtensionHelpers getHelpers() { return helpers; }
    public PrintWriter getStdout() { return stdout; }
    public PrintWriter getStderr() { return stderr; }
    
    public String getOllamaEndpoint() { return ollamaEndpoint; }
    public void setOllamaEndpoint(String endpoint) { this.ollamaEndpoint = endpoint; }
    
    public String getAnalysisModel() { return analysisModel; }
    public void setAnalysisModel(String model) { this.analysisModel = model; }
    
    public String getPayloadModel() { return payloadModel; }
    public void setPayloadModel(String model) { this.payloadModel = model; }
    
    public double getTemperature() { return temperature; }
    public void setTemperature(double temp) { this.temperature = temp; }
    
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int tokens) { this.maxTokens = tokens; }
    
    public int getMaxContextSize() { return maxContextSize; }
    public void setMaxContextSize(int size) { this.maxContextSize = size; }
    
    public boolean isRedactAuthHeaders() { return redactAuthHeaders; }
    public void setRedactAuthHeaders(boolean redact) { this.redactAuthHeaders = redact; }
    
    public boolean isRedactCookies() { return redactCookies; }
    public void setRedactCookies(boolean redact) { this.redactCookies = redact; }
    
    public Map<String, String> getSavedPrompts() { return savedPrompts; }
    public void addSavedPrompt(String name, String prompt) {
        savedPrompts.put(name, prompt);
        saveSavedPrompts();
    }
    public void removeSavedPrompt(String name) {
        savedPrompts.remove(name);
        saveSavedPrompts();
    }
    // Version management
    public String getVersion() {
        return extensionVersion;
    }
    
    public void setVersion(String version) {
        this.extensionVersion = version;
        saveSettings(); // Persist version if needed
    }

    // Provider selection
    public String getActiveProvider() { return activeProvider; }
    public void setActiveProvider(String provider) { this.activeProvider = provider; }
    
    // OpenAI getters/setters
    public String getOpenAiApiKey() { return openAiApiKey; }
    public void setOpenAiApiKey(String key) { this.openAiApiKey = key; }
    public String getOpenAiModel() { return openAiModel; }
    public void setOpenAiModel(String model) { this.openAiModel = model; }
    public String getOpenAiBaseUrl() { return openAiBaseUrl; }
    public void setOpenAiBaseUrl(String url) { this.openAiBaseUrl = url; }
    
    // Claude getters/setters
    public String getClaudeApiKey() { return claudeApiKey; }
    public void setClaudeApiKey(String key) { this.claudeApiKey = key; }
    public String getClaudeModel() { return claudeModel; }
    public void setClaudeModel(String model) { this.claudeModel = model; }
    public String getClaudeBaseUrl() { return claudeBaseUrl; }
    public void setClaudeBaseUrl(String url) { this.claudeBaseUrl = url; }
}
