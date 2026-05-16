package burp;

public enum ProviderType {
    OLLAMA("ollama"),
    OPENAI("openai"),
    CLAUDE("claude");
    
    private final String name;
    
    ProviderType(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public static ProviderType fromString(String text) {
        for (ProviderType type : ProviderType.values()) {
            if (type.name.equalsIgnoreCase(text)) {
                return type;
            }
        }
        return OLLAMA;
    }
}