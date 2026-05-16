package burp;

import java.util.concurrent.*;
import burp.LLMProvider;

public class AutocompleteEngine {
    private final ExtensionState state;
    private final ProviderFactory providerFactory;
    private LLMProvider activeProvider;
    private final PromptEngine promptEngine;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, String[]> cache;
    private final Semaphore rateLimiter;
    
    public AutocompleteEngine(ExtensionState state, ProviderFactory providerFactory, 
                            PromptEngine promptEngine) {
        this.state = state;
        this.providerFactory = providerFactory;
        this.activeProvider = providerFactory.getActiveProvider();
        this.promptEngine = promptEngine;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Suite-o-llama-Autocomplete");
            return t;
        });
        this.cache = new ConcurrentHashMap<>();
        this.rateLimiter = new Semaphore(3); // 3 for smoothness
    }
    
    public interface PayloadCallback {
        void onPayloadsGenerated(String[] payloads);
    }
    
    public void generatePayloads(RequestContext context, String paramContext, 
                               PayloadCallback callback) {
        // Check cache first
        String cacheKey = createCacheKey(context, paramContext);
        if (cache.containsKey(cacheKey)) {
            callback.onPayloadsGenerated(cache.get(cacheKey));
            return;
        }
        
        // Rate limiting - only process if not already generating
        if (!rateLimiter.tryAcquire()) {
            callback.onPayloadsGenerated(new String[0]);
            return;
        }
        
        executor.submit(() -> {
            try {
                String[] payloads = generatePayloadsInternal(context, paramContext);
                cache.put(cacheKey, payloads);
                callback.onPayloadsGenerated(payloads);
            } catch (Exception e) {
                state.getStderr().println("Autocomplete error: " + e.getMessage());
                callback.onPayloadsGenerated(new String[0]);
            } finally {
                rateLimiter.release();
            }
        });
    }
    
    private String[] generatePayloadsInternal(RequestContext context, String paramContext) {
        try {
            // Create lightweight prompt for payload generation
            String prompt = "Generate 5 security testing payloads for this parameter context:\n" +
                          paramContext + "\n\n" +
                          "Output format: one payload per line, no explanations.\n" +
                          "Focus on: SQLi, XSS, command injection.";
            
            // Trim prompt to avoid bloat
            prompt = ContextTrimmer.trim(prompt, 500);
            activeProvider = providerFactory.getActiveProvider();
            String response = null;
            // Use synchronous generation - create a temporary callback
            final String[] result = new String[1];
            final Exception[] error = new Exception[1];
            final Object lock = new Object();
            
            activeProvider.generateAsync(prompt, state.getPayloadModel(), null, new LLMProvider.ResponseCallback() {
                @Override
                public void onSuccess(String resp, long timeMs, int tokens) {
                    synchronized (lock) {
                        result[0] = resp;
                        lock.notify();
                    }
                }
                @Override
                public void onError(String err) {
                    synchronized (lock) {
                        error[0] = new Exception(err);
                        lock.notify();
                    }
                }
                @Override
                public void onCancelled(long cancelTimeMs) {
                    synchronized (lock) {
                        error[0] = new Exception("Cancelled");
                        lock.notify();
                    }
                }
            });
            
            synchronized (lock) {
                lock.wait(30000); // 30 second timeout
            }
            
            if (error[0] != null) {
                throw error[0];
            }
            response = result[0];
            
            // Parse response into individual payloads
            return parsePayloads(response);
            
        } catch (Exception e) {
            state.getStderr().println("Payload generation failed: " + e.getMessage());
            return new String[0];
        }
    }
    
    private String[] parsePayloads(String response) {
        if (response == null || response.trim().isEmpty()) {
            return new String[0];
        }
        
        String[] lines = response.split("\n");
        java.util.List<String> payloads = new java.util.ArrayList<>();
        
        for (String line : lines) {
            line = line.trim();
            // Skip empty lines, headers, explanations
            if (line.isEmpty() || 
                line.startsWith("#") || 
                line.startsWith("//") ||
                line.length() > 200 ||
                line.toLowerCase().contains("payload") && line.endsWith(":")) {
                continue;
            }
            
            // Remove common prefixes
            line = line.replaceFirst("^\\d+\\.\\s*", "");
            line = line.replaceFirst("^-\\s*", "");
            line = line.replaceFirst("^\\*\\s*", "");
            
            if (!line.isEmpty()) {
                payloads.add(line);
            }
            
            // Limit to 10 payloads
            if (payloads.size() >= 10) {
                break;
            }
        }
        
        return payloads.toArray(new String[0]);
    }
    
    private String createCacheKey(RequestContext context, String paramContext) {
        // Simple cache key based on URL and param context
        String url = context.hasService() ? context.getServiceInfo() : "unknown";
        return url + ":" + paramContext.hashCode();
    }
    
    public void clearCache() {
        cache.clear();
    }
    
    public void shutdown() {
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
