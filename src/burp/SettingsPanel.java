package burp;

import org.json.JSONArray;
import org.json.JSONObject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;

public class SettingsPanel extends JPanel {
    private final ExtensionState state;
    private OllamaClient testClient;
    
    // UI Components - Ollama (endpoint kept as hidden field)
    private JTextField endpointField;  // Keep but will be hidden
    private JTextField analysisModelField;
    private JTextField payloadModelField;
    private JSpinner temperatureSpinner;
    private JSpinner maxTokensSpinner;
    private JSpinner maxContextSpinner;
    private JCheckBox redactAuthCheckbox;
    private JCheckBox redactCookiesCheckbox;
    private JButton saveButton;
    private JButton newReleasesBtn;
    private JTextArea statusArea;
    private JList<String> modelList;
    private DefaultListModel<String> modelListModel;
    private UpdateChecker updateChecker;
    
    // UI Components - External Provider (Unified)
    private JPanel externalPanel; 
    private JComboBox<String> providerSelector;
    private JPasswordField unifiedApiKeyField;
    private JTextField unifiedBaseUrlField;
    private JButton testConnectionBtn;
    private JLabel connectionStatusLabel;

    public SettingsPanel(ExtensionState state) {
        this.state = state;
        this.testClient = new OllamaClient(state);
        this.updateChecker = new UpdateChecker(state);
        initUI();
        loadSettings();
        checkForUpdates();
        checkForUpdatesBackground();
    }
    
    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        
        // ===== ROW 1: External Provider Configuration (left) + Model Configuration (right) =====
        JPanel row1Panel = new JPanel(new GridBagLayout());
        GridBagConstraints row1Gbc = new GridBagConstraints();
        row1Gbc.insets = new Insets(0, 0, 0, 10);
        row1Gbc.fill = GridBagConstraints.BOTH;
        row1Gbc.weightx = 0.5;
        row1Gbc.gridx = 0;
        row1Gbc.gridy = 0;
        
        // Column 1: External Provider Configuration (moved from full-width row)
        externalPanel = new JPanel(new GridBagLayout());
        externalPanel.setBorder(BorderFactory.createTitledBorder("AI Configuration"));
        
        GridBagConstraints epGbc = new GridBagConstraints();
        epGbc.insets = new Insets(5, 5, 5, 5);
        epGbc.fill = GridBagConstraints.HORIZONTAL;
        epGbc.weightx = 1.0;
        epGbc.gridx = 0;
        
        epGbc.gridy = 0;
        epGbc.gridx = 0;
        externalPanel.add(new JLabel("AI Provider:"), epGbc);
        epGbc.gridx = 1;
        providerSelector = new JComboBox<>(new String[]{"ollama", "openai", "claude"});
        externalPanel.add(providerSelector, epGbc);
        
        epGbc.gridx = 0;
        epGbc.gridy = 1;
        externalPanel.add(new JLabel("API Key:"), epGbc);
        epGbc.gridx = 1;
        unifiedApiKeyField = new JPasswordField(30);
        externalPanel.add(unifiedApiKeyField, epGbc);
        
        epGbc.gridx = 0;
        epGbc.gridy = 2;
        externalPanel.add(new JLabel("Base URL:"), epGbc);
        epGbc.gridx = 1;
        unifiedBaseUrlField = new JTextField(30);
        externalPanel.add(unifiedBaseUrlField, epGbc);
        
        epGbc.gridx = 0;
        epGbc.gridy = 3;
        epGbc.gridwidth = 2;
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testConnectionBtn = new JButton("Test Connection");
        testConnectionBtn.addActionListener(e -> testExternalConnection());
        testPanel.add(testConnectionBtn);
        externalPanel.add(testPanel, epGbc);
        
        epGbc.gridwidth = 1;
        epGbc.gridy = 4;
        connectionStatusLabel = new JLabel(" ");
        connectionStatusLabel.setForeground(Color.GRAY);
        externalPanel.add(connectionStatusLabel, epGbc);
        
        externalPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, externalPanel.getPreferredSize().height + 20));
        row1Panel.add(externalPanel, row1Gbc);
        
        // Column 2: Model Configuration (moved from left to right)
        row1Gbc.gridx = 1;
        JPanel modelPanel = new JPanel(new GridBagLayout());
        modelPanel.setBorder(BorderFactory.createTitledBorder("Model Configuration"));
        GridBagConstraints mgbc = new GridBagConstraints();
        mgbc.insets = new Insets(5, 5, 5, 5);
        mgbc.anchor = GridBagConstraints.WEST;
        
        mgbc.gridx = 0; mgbc.gridy = 0;
        modelPanel.add(new JLabel("Analysis Model:"), mgbc);
        
        mgbc.gridx = 1; mgbc.fill = GridBagConstraints.HORIZONTAL; mgbc.weightx = 1.0;
        analysisModelField = new JTextField(25);
        modelPanel.add(analysisModelField, mgbc);
        
        mgbc.gridx = 0; mgbc.gridy = 1; mgbc.fill = GridBagConstraints.NONE; mgbc.weightx = 0;
        modelPanel.add(new JLabel("Payload Model:"), mgbc);
        
        mgbc.gridx = 1; mgbc.fill = GridBagConstraints.HORIZONTAL; mgbc.weightx = 1.0;
        payloadModelField = new JTextField(25);
        modelPanel.add(payloadModelField, mgbc);
        
        mgbc.gridx = 0; mgbc.gridy = 2; mgbc.fill = GridBagConstraints.NONE; mgbc.weightx = 0;
        modelPanel.add(new JLabel("Temperature:"), mgbc);

        mgbc.gridx = 1;
        mgbc.fill = GridBagConstraints.HORIZONTAL;  // ADD THIS - allows stretching
        mgbc.weightx = 1.0;                         // ADD THIS - takes available space
        temperatureSpinner = new JSpinner(new SpinnerNumberModel(0.7, 0.0, 2.0, 0.1));
        // Make the spinner editor wider
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(temperatureSpinner, "0.00");
        temperatureSpinner.setEditor(editor);
        temperatureSpinner.setPreferredSize(new Dimension(80, temperatureSpinner.getPreferredSize().height));
        modelPanel.add(temperatureSpinner, mgbc);
        
        mgbc.gridx = 0; mgbc.gridy = 3;
        modelPanel.add(new JLabel("Max Tokens:"), mgbc);
        
        mgbc.gridx = 1;
        maxTokensSpinner = new JSpinner(new SpinnerNumberModel(4096, 128, 16384, 256));
        modelPanel.add(maxTokensSpinner, mgbc);
        
        mgbc.gridx = 0; mgbc.gridy = 4;
        modelPanel.add(new JLabel("Max Context Size:"), mgbc);
        
        mgbc.gridx = 1;
        maxContextSpinner = new JSpinner(new SpinnerNumberModel(16384, 1024, 65536, 1024));
        JPanel contextPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        contextPanel.add(maxContextSpinner);
        contextPanel.add(new JLabel("characters"));
        modelPanel.add(contextPanel, mgbc);
        
        row1Panel.add(modelPanel, row1Gbc);
        row1Panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, row1Panel.getPreferredSize().height + 20));
        mainPanel.add(row1Panel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // ===== ROW 2: Available Models (left) + Security & Privacy (center) + Status (right) =====
        JPanel row2Panel = new JPanel(new GridBagLayout());
        GridBagConstraints row2Gbc = new GridBagConstraints();
        row2Gbc.insets = new Insets(0, 0, 0, 10);
        row2Gbc.fill = GridBagConstraints.BOTH;
        row2Gbc.weightx = 0.34;
        row2Gbc.gridx = 0;
        row2Gbc.gridy = 0;
        
        // Column 1: Available Models Panel
        JPanel modelsPanel = new JPanel(new BorderLayout(5, 5));
        modelsPanel.setBorder(BorderFactory.createTitledBorder("Available Models"));
        
        modelListModel = new DefaultListModel<>();
        modelList = new JList<>(modelListModel);
        JScrollPane modelScroll = new JScrollPane(modelList);
        modelScroll.setPreferredSize(new Dimension(400, 150));
        modelsPanel.add(modelScroll, BorderLayout.CENTER);
        
        row2Panel.add(modelsPanel, row2Gbc);
        
        // Column 2: Security & Privacy Panel (moved from top-right)
        row2Gbc.gridx = 1;
        JPanel securityPanel = new JPanel(new GridBagLayout());
        securityPanel.setBorder(BorderFactory.createTitledBorder("Privacy"));
        GridBagConstraints sgbc = new GridBagConstraints();
        sgbc.insets = new Insets(5, 5, 5, 5);
        sgbc.anchor = GridBagConstraints.WEST;
        sgbc.gridx = 0; sgbc.gridy = 0;
        
        redactAuthCheckbox = new JCheckBox("Redact Authorization headers");
        securityPanel.add(redactAuthCheckbox, sgbc);
        
        sgbc.gridy = 1;
        redactCookiesCheckbox = new JCheckBox("Redact Cookies");
        securityPanel.add(redactCookiesCheckbox, sgbc);
        
        sgbc.gridy = 2;
        JLabel note = new JLabel("Redacted data will be replaced with [REDACTED] before sending to AI");
        note.setFont(note.getFont().deriveFont(Font.PLAIN, 10f));
        securityPanel.add(note, sgbc);
        
        row2Panel.add(securityPanel, row2Gbc);
        
        // Column 3: Status Panel (moved from bottom-right)
        row2Gbc.gridx = 2;
        row2Gbc.insets = new Insets(0, 0, 0, 0);
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("System Status"));
        statusArea = new JTextArea(5, 40);
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statusPanel.add(new JScrollPane(statusArea), BorderLayout.CENTER);

        showWelcomeMessage();
        
        row2Panel.add(statusPanel, row2Gbc);
        row2Panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        mainPanel.add(row2Panel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // ===== Button Panel (stays at bottom) =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton refreshModelsBtn = new JButton("Refresh Models");
        refreshModelsBtn.addActionListener(e -> refreshModels());
        buttonPanel.add(refreshModelsBtn);
        
        saveButton = new JButton("Save Settings");
        saveButton.addActionListener(e -> saveSettings());
        buttonPanel.add(saveButton);
        
        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> resetToDefaults());
        buttonPanel.add(resetButton);
        
        // GitHub button
        JButton githubBtn = new JButton("GitHub");
        githubBtn.addActionListener(e -> openGitHubProfile());
        buttonPanel.add(githubBtn);
        
        // New Releases button
        newReleasesBtn = new JButton("New Releases");
        newReleasesBtn.addActionListener(e -> openGitHubReleases());
        buttonPanel.add(newReleasesBtn);
        
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        mainPanel.add(buttonPanel);
        
        // Add listener for provider selection to update UI
        providerSelector.addActionListener(e -> updateUnifiedFieldsVisibility());
        
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);
    }
    
    private void loadSettings() {
        analysisModelField.setText(state.getAnalysisModel());
        payloadModelField.setText(state.getPayloadModel());
        temperatureSpinner.setValue(state.getTemperature());
        maxTokensSpinner.setValue(state.getMaxTokens());
        maxContextSpinner.setValue(state.getMaxContextSize());
        redactAuthCheckbox.setSelected(state.isRedactAuthHeaders());
        redactCookiesCheckbox.setSelected(state.isRedactCookies());

        // External provider settings - unified approach
        String activeProvider = state.getActiveProvider();
        providerSelector.setSelectedItem(activeProvider);
        
        if ("openai".equals(activeProvider)) {
            unifiedApiKeyField.setText(state.getOpenAiApiKey());
            unifiedBaseUrlField.setText(state.getOpenAiBaseUrl());
        } else if ("claude".equals(activeProvider)) {
            unifiedApiKeyField.setText(state.getClaudeApiKey());
            unifiedBaseUrlField.setText(state.getClaudeBaseUrl());
        } else {
            unifiedApiKeyField.setText("");
            unifiedBaseUrlField.setText("");
        }
        
        // Populate model dropdowns based on provider
        updateUnifiedFieldsVisibility();
    }
    

    private void saveSettings() {
        // Save Ollama endpoint via unified Base URL field if Ollama is active
        String activeProvider = (String) providerSelector.getSelectedItem();
        
        // For OpenAI/Claude, validate API key before saving
        if ("openai".equals(activeProvider) || "claude".equals(activeProvider)) {
            String apiKey = new String(unifiedApiKeyField.getPassword());
            String baseUrl = unifiedBaseUrlField.getText().trim();
            
            // Check if API key is empty or looks invalid
            if (apiKey == null || apiKey.trim().isEmpty()) {
                String statusMsg = "Settings are NOT saved.\n" +
                                "Active Provider: " + activeProvider + "\n" +
                                "Base URL: " + (baseUrl.isEmpty() ? 
                                    (activeProvider.equals("openai") ? "https://api.openai.com/v1" : "https://api.anthropic.com/v1") : 
                                    baseUrl) + "\n" +
                                "Check your API key and try again.";
                statusArea.setText(statusMsg);
                return; // EXIT - don't save settings
            }
            
            // Optional: Basic API key format validation
            if (activeProvider.equals("openai") && !apiKey.trim().startsWith("sk-")) {
                String statusMsg = "Settings are NOT saved.\n" +
                                "Active Provider: " + activeProvider + "\n" +
                                "Base URL: " + baseUrl + "\n" +
                                "API key should start with 'sk-'. Check your API key and try again.";
                statusArea.setText(statusMsg);
                return; // EXIT - don't save settings
            }
            
            if (activeProvider.equals("claude") && apiKey.trim().length() < 20) {
                String statusMsg = "Settings are NOT saved.\n" +
                                "Active Provider: " + activeProvider + "\n" +
                                "Base URL: " + baseUrl + "\n" +
                                "API key seems invalid (too short). Check your API key and try again.";
                statusArea.setText(statusMsg);
                return; // EXIT - don't save settings
            }
        }
        
        // Proceed with saving if validation passes
        if ("ollama".equals(activeProvider)) {
            state.setOllamaEndpoint(unifiedBaseUrlField.getText().trim());
        }      
        state.setAnalysisModel(analysisModelField.getText().trim());
        state.setPayloadModel(payloadModelField.getText().trim());
        state.setTemperature((Double) temperatureSpinner.getValue());
        state.setMaxTokens((Integer) maxTokensSpinner.getValue());
        state.setMaxContextSize((Integer) maxContextSpinner.getValue());
        state.setRedactAuthHeaders(redactAuthCheckbox.isSelected());
        state.setRedactCookies(redactCookiesCheckbox.isSelected());
        
        // External provider settings - unified approach
        state.setActiveProvider(activeProvider);
        
        String apiKey = new String(unifiedApiKeyField.getPassword());
        String baseUrl = unifiedBaseUrlField.getText().trim();
        
        if ("openai".equals(activeProvider)) {
            state.setOpenAiApiKey(apiKey);
            state.setOpenAiBaseUrl(baseUrl);
        } else if ("claude".equals(activeProvider)) {
            state.setClaudeApiKey(apiKey);
            state.setClaudeBaseUrl(baseUrl);
        }
        
        state.saveSettings();
        
        // Success status message
        String statusMsg = "Settings saved successfully\n" + 
                        "Active Provider: " + activeProvider;
        if (!"ollama".equals(activeProvider)) {
            statusMsg += "\nBase URL: " + baseUrl;
            statusMsg += "\n\nNOTE: Use Model Configuration section above to set analysis/payload models";
        } else {
            statusMsg += "\nEndpoint: " + state.getOllamaEndpoint() +
                        "\nAnalysis Model: " + state.getAnalysisModel() +
                        "\nPayload Model: " + state.getPayloadModel();
        }
        statusArea.setText(statusMsg);
        
        // Recreate test client with new settings
        testClient = new OllamaClient(state);
    }
    
    // ========== NEW METHODS FOR UPDATE CHECKING ==========

    private void checkForUpdates() {
        new Thread(() -> {
            try {
                boolean hasUpdate = updateChecker.checkForUpdates();
                if (hasUpdate) {
                    SwingUtilities.invokeLater(() -> {
                    statusArea.append("\nNew release available! Click 'New releases' button.\n");
                    });
                }
            } catch (Exception e) {
            // Silent fail
            }
        }).start();
    }

    private void openGitHubReleases() {
        try {
            // Clear status area
            statusArea.setText("Checking for updates...\n");
            
            // Check if update is available
            boolean hasUpdate = updateChecker.checkForUpdates();
            
            if (hasUpdate) {
                statusArea.append("✓ New release available!\n");
                statusArea.append("Opening GitHub releases page...\n");
                
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URI("https://github.com/VermaOps/suite-o-llama/releases"));
                    statusArea.append("✓ GitHub releases page opened in browser.");
                } else {
                    statusArea.append("✗ Desktop operations not supported on this system.");
                }
            } else {
                statusArea.append("✓ You are already using the latest version.\n");
                statusArea.append("Current version: " + state.getVersion() + "\n");
                
                // Optional: Show latest version info
                JSONObject latestRelease = updateChecker.getLatestReleaseInfo();
                if (latestRelease != null) {
                    String latestVersion = latestRelease.optString("tag_name", "unknown");
                    String publishedAt = latestRelease.optString("published_at", "");
                    statusArea.append("Latest version: " + latestVersion + "\n");
                    if (!publishedAt.isEmpty()) {
                        statusArea.append("Published: " + publishedAt.substring(0, 10) + "\n");
                    }
                }
            }
        } catch (Exception e) {
            statusArea.append("✗ Error checking updates: " + e.getMessage());
        }
    }

    private void checkForUpdatesBackground() {
        new Thread(() -> {
            try {
                boolean hasUpdate = updateChecker.checkForUpdates();
                if (hasUpdate) {
                    SwingUtilities.invokeLater(() -> {
                        if (newReleasesBtn != null) {
                            newReleasesBtn.setBackground(Color.YELLOW);
                            newReleasesBtn.setOpaque(true);
                            newReleasesBtn.setBorderPainted(true);
                        }
                    });
                }
            } catch (Exception e) {
                // Silent fail - don't disrupt user experience
            }
        }).start();
    }

    private void openGitHubProfile() {
        try {
            // Clear and show fresh status
            statusArea.setText("Opening GitHub profile...\n");
        
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI("https://github.com/VermaOps"));
                statusArea.append("✓ GitHub profile opened in browser.");
            } else {
                statusArea.append("✗ Desktop operations not supported on this system.");
            }
        } catch (Exception e) {
            statusArea.append("\n✗ Error opening GitHub profile: " + e.getMessage());
        }
    }
    
    private void refreshModels() {
        String selectedProvider = (String) providerSelector.getSelectedItem();
        
        new Thread(() -> {
            try {
                java.util.List<String> models = new java.util.ArrayList<>();
                
                if ("ollama".equals(selectedProvider)) {
                    // Use OllamaClient for Ollama
                    state.setOllamaEndpoint(unifiedBaseUrlField.getText().trim());
                    OllamaClient ollamaClient = new OllamaClient(state);
                    models = ollamaClient.getAvailableModels();
                } else if ("openai".equals(selectedProvider)) {
                    // Fetch OpenAI models directly
                    String apiKey = new String(unifiedApiKeyField.getPassword());
                    String baseUrl = unifiedBaseUrlField.getText().trim();
                    if (baseUrl.isEmpty()) baseUrl = "https://api.openai.com/v1";
                    
                    if (apiKey.trim().isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            modelListModel.clear();
                            modelListModel.addElement("[API key required]");
                            statusArea.append("\n✗ Cannot fetch OpenAI models: API key required\n");
                        });
                        return;
                    }
                    
                    models = fetchOpenAIModelsList(apiKey, baseUrl);
                } else if ("claude".equals(selectedProvider)) {
                    // Fetch Claude models directly
                    String apiKey = new String(unifiedApiKeyField.getPassword());
                    String baseUrl = unifiedBaseUrlField.getText().trim();
                    if (baseUrl.isEmpty()) baseUrl = "https://api.anthropic.com/v1";
                    
                    if (apiKey.trim().isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            modelListModel.clear();
                            modelListModel.addElement("[API key required]");
                            statusArea.append("\n✗ Cannot fetch Claude models: API key required\n");
                        });
                        return;
                    }
                    
                    models = fetchClaudeModelsList(apiKey, baseUrl);
                }
                
                final java.util.List<String> finalModels = models;
                final String finalProvider = selectedProvider;
                
                SwingUtilities.invokeLater(() -> {
                    modelListModel.clear();
                    if (finalModels != null && !finalModels.isEmpty()) {
                        for (String model : finalModels) {
                            modelListModel.addElement(model);
                        }
                        statusArea.append("\n✓ Found " + finalModels.size() + " " + 
                                        finalProvider.toUpperCase() + " models\n");
                    } else {
                        if ("openai".equals(finalProvider)) {
                            modelListModel.addElement("[No GPT models found]");
                            statusArea.append("\n⚠ No chat models returned. Check API key permissions.\n");
                        } else if ("claude".equals(finalProvider)) {
                            modelListModel.addElement("[No Claude models found]");
                            statusArea.append("\n⚠ No models returned. Check API key permissions.\n");
                        } else {
                            modelListModel.addElement("No models found");
                            statusArea.append("\nNo models found. Pull models using:\n");
                            statusArea.append("  ollama pull qwen2.5:7b-instruct\n");
                            statusArea.append("  ollama pull qwen2.5-coder:7b\n");
                        }
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    modelListModel.clear();
                    modelListModel.addElement("[Error: " + e.getMessage() + "]");
                    statusArea.append("\n✗ Error fetching models: " + e.getMessage() + "\n");
                });
            }
        }).start();
    }

    private java.util.List<String> fetchOpenAIModelsList(String apiKey, String baseUrl) {
        java.util.List<String> models = new java.util.ArrayList<>();
        try {
            java.net.URL url = new java.net.URL(baseUrl + "/models");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(10000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return models; // Return empty list on error
            }
            
            StringBuilder response = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            org.json.JSONObject json = new org.json.JSONObject(response.toString());
            org.json.JSONArray data = json.getJSONArray("data");
            
            for (int i = 0; i < data.length(); i++) {
                String modelId = data.getJSONObject(i).getString("id");
                // Filter for chat-capable models
                if (modelId.startsWith("gpt-") || 
                    modelId.startsWith("o1") || 
                    modelId.startsWith("o3") ||
                    modelId.equals("gpt-4") ||
                    modelId.equals("gpt-4-turbo") ||
                    modelId.equals("gpt-3.5-turbo")) {
                    models.add(modelId);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            // Return empty list
        }
        return models;
    }

    private java.util.List<String> fetchClaudeModelsList(String apiKey, String baseUrl) {
        java.util.List<String> models = new java.util.ArrayList<>();
        try {
            java.net.URL url = new java.net.URL(baseUrl + "/models");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setConnectTimeout(10000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return models; // Return empty list on error
            }
            
            StringBuilder response = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            org.json.JSONObject json = new org.json.JSONObject(response.toString());
            org.json.JSONArray data = json.getJSONArray("data");
            
            for (int i = 0; i < data.length(); i++) {
                String modelId = data.getJSONObject(i).getString("id");
                if (modelId.startsWith("claude")) {
                    models.add(modelId);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            // Return empty list
        }
        return models;
    }

    private void updateUnifiedFieldsVisibility() {
        String selected = (String) providerSelector.getSelectedItem();
        boolean isOllama = "ollama".equals(selected);
        
        // ALWAYS clear API key when switching providers
        unifiedApiKeyField.setText("");

        if (isOllama) {
            // Ollama: disable API key, use Base URL field for endpoint
            unifiedApiKeyField.setText("");
            unifiedApiKeyField.setEnabled(false);
            unifiedBaseUrlField.setEnabled(true);
            unifiedBaseUrlField.setText("http://127.0.0.1:11434");  // FORCED default
            
            testConnectionBtn.setEnabled(true);
            connectionStatusLabel.setText("Enter Ollama endpoint and click Test Connection");
            
            // API Key label should be visible but dimmed/disabled indication
            Component[] components = externalPanel.getComponents();
            for (Component comp : components) {
                if (comp instanceof JLabel && "API Key:".equals(((JLabel) comp).getText())) {
                    comp.setVisible(true);
                }
                if (comp instanceof JLabel && "Base URL:".equals(((JLabel) comp).getText())) {
                    comp.setVisible(true);
                }
            }
        } else if ("openai".equals(selected)) {
            // OpenAI: enable both fields, set forced default
            unifiedApiKeyField.setEnabled(true);
            unifiedBaseUrlField.setEnabled(true);
            unifiedBaseUrlField.setText("https://api.openai.com/v1");  // FORCED default
            
            testConnectionBtn.setEnabled(true);
            connectionStatusLabel.setText("Enter API key and click Test Connection");
            
            // Show API key related labels
            Component[] components = externalPanel.getComponents();
            for (Component comp : components) {
                if (comp instanceof JLabel && "API Key:".equals(((JLabel) comp).getText())) {
                    comp.setVisible(true);
                }
                if (comp instanceof JLabel && "Base URL:".equals(((JLabel) comp).getText())) {
                    comp.setVisible(true);
                }
            }
        } else if ("claude".equals(selected)) {
            // Claude: enable both fields, set forced default
            unifiedApiKeyField.setEnabled(true);
            unifiedBaseUrlField.setEnabled(true);
            unifiedBaseUrlField.setText("https://api.anthropic.com/v1");  // FORCED default
            
            testConnectionBtn.setEnabled(true);
            connectionStatusLabel.setText("Enter API key and click Test Connection");
            
            // Show API key related labels
            Component[] components = externalPanel.getComponents();
            for (Component comp : components) {
                if (comp instanceof JLabel && "API Key:".equals(((JLabel) comp).getText())) {
                    comp.setVisible(true);
                }
                if (comp instanceof JLabel && "Base URL:".equals(((JLabel) comp).getText())) {
                    comp.setVisible(true);
                }
            }
        }
        
        // Clear stale models when provider changes
        modelListModel.clear();
        connectionStatusLabel.setForeground(Color.GRAY);
    }
    
    private void testExternalConnection() {
        String selected = (String) providerSelector.getSelectedItem();
        
        if ("ollama".equals(selected)) {
            testOllamaConnection();
        } else if ("openai".equals(selected)) {
            testOpenAIConnectionUnified();
        } else if ("claude".equals(selected)) {
            testClaudeConnectionUnified();
        }
    }
    
    private void testOllamaConnection() {
        String endpoint = unifiedBaseUrlField.getText().trim();
        if (endpoint.isEmpty()) {
            endpoint = "http://127.0.0.1:11434";
            unifiedBaseUrlField.setText(endpoint);
        }
        
        statusArea.append("\nTesting Ollama connection to " + endpoint + "...\n");
        connectionStatusLabel.setText("Testing Ollama...");
        connectionStatusLabel.setForeground(Color.GRAY);
        
        final String finalEndpoint = endpoint;
        new Thread(() -> {
            try {
                // Update state with endpoint for test
                state.setOllamaEndpoint(finalEndpoint);
                OllamaClient testClient = new OllamaClient(state);
                boolean connected = testClient.checkHealth();
                
                SwingUtilities.invokeLater(() -> {
                    if (connected) {
                        connectionStatusLabel.setText("✓ Ollama connected successfully");
                        connectionStatusLabel.setForeground(new Color(0, 150, 0));
                        statusArea.append("✓ Ollama connection successful!\n");
                        // Fetch models from /api/tags
                        refreshModelsForProvider("ollama", finalEndpoint);
                    } else {
                        connectionStatusLabel.setText("✗ Ollama connection failed");
                        connectionStatusLabel.setForeground(Color.RED);
                        statusArea.append("✗ Connection failed. Make sure Ollama is running.\n");
                        modelListModel.clear();
                        modelListModel.addElement("[Connection failed]");
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    connectionStatusLabel.setText("✗ Error: " + e.getMessage());
                    connectionStatusLabel.setForeground(Color.RED);
                    statusArea.append("✗ Error: " + e.getMessage() + "\n");
                    modelListModel.clear();
                    modelListModel.addElement("[Connection error]");
                });
            }
        }).start();
    }
    
    private void testOpenAIConnectionUnified() {
        String apiKey = new String(unifiedApiKeyField.getPassword());
        if (apiKey.trim().isEmpty()) {
            connectionStatusLabel.setText("✗ API key required");
            connectionStatusLabel.setForeground(Color.RED);
            statusArea.append("\n✗ OpenAI: API key required\n");
            return;
        }
        
        String baseUrl = unifiedBaseUrlField.getText().trim();
        if (baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com/v1";
            unifiedBaseUrlField.setText(baseUrl);
        }
        
        statusArea.append("\nTesting OpenAI connection to " + baseUrl + "...\n");
        connectionStatusLabel.setText("Testing OpenAI...");
        connectionStatusLabel.setForeground(Color.GRAY);
        
        final String finalApiKey = apiKey;
        final String finalBaseUrl = baseUrl;
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(finalBaseUrl + "/models");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + finalApiKey);
                conn.setConnectTimeout(10000);
                int responseCode = conn.getResponseCode();
                conn.disconnect();
                
                SwingUtilities.invokeLater(() -> {
                    if (responseCode == 200) {
                        connectionStatusLabel.setText("✓ OpenAI connection successful");
                        connectionStatusLabel.setForeground(new Color(0, 150, 0));
                        statusArea.append("✓ OpenAI authentication successful!\n");
                        // Fetch available models (requirement 5.2)
                        fetchOpenAIModels(finalApiKey, finalBaseUrl);
                    } else {
                        connectionStatusLabel.setText("✗ OpenAI connection failed (HTTP " + responseCode + ")");
                        connectionStatusLabel.setForeground(Color.RED);
                        statusArea.append("✗ Authentication failed. Check your API key.\n");
                        modelListModel.clear();
                        modelListModel.addElement("[Authentication failed]");
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    connectionStatusLabel.setText("✗ Error: " + e.getMessage());
                    connectionStatusLabel.setForeground(Color.RED);
                    statusArea.append("✗ Connection error: " + e.getMessage() + "\n");
                    modelListModel.clear();
                    modelListModel.addElement("[Connection error]");
                });
            }
        }).start();
    }
    
    private void testClaudeConnectionUnified() {
        String apiKey = new String(unifiedApiKeyField.getPassword());
        if (apiKey.trim().isEmpty()) {
            connectionStatusLabel.setText("✗ API key required");
            connectionStatusLabel.setForeground(Color.RED);
            statusArea.append("\n✗ Claude: API key required\n");
            return;
        }
        
        String baseUrl = unifiedBaseUrlField.getText().trim();
        if (baseUrl.isEmpty()) {
            baseUrl = "https://api.anthropic.com/v1";
            unifiedBaseUrlField.setText(baseUrl);
        }
        
        statusArea.append("\nTesting Claude connection to " + baseUrl + "...\n");
        connectionStatusLabel.setText("Testing Claude...");
        connectionStatusLabel.setForeground(Color.GRAY);
        
        final String finalApiKey = apiKey;
        final String finalBaseUrl = baseUrl;
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(finalBaseUrl + "/models");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-key", finalApiKey);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                conn.setConnectTimeout(10000);
                int responseCode = conn.getResponseCode();
                conn.disconnect();
                
                SwingUtilities.invokeLater(() -> {
                    if (responseCode == 200) {
                        connectionStatusLabel.setText("✓ Claude connection successful");
                        connectionStatusLabel.setForeground(new Color(0, 150, 0));
                        statusArea.append("✓ Claude authentication successful!\n");
                        // Fetch available models (requirement 5.3)
                        fetchClaudeModels(finalApiKey, finalBaseUrl);
                    } else {
                        connectionStatusLabel.setText("✗ Claude connection failed (HTTP " + responseCode + ")");
                        connectionStatusLabel.setForeground(Color.RED);
                        statusArea.append("✗ Authentication failed. Check your API key.\n");
                        modelListModel.clear();
                        modelListModel.addElement("[Authentication failed]");
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    connectionStatusLabel.setText("✗ Error: " + e.getMessage());
                    connectionStatusLabel.setForeground(Color.RED);
                    statusArea.append("✗ Connection error: " + e.getMessage() + "\n");
                    modelListModel.clear();
                    modelListModel.addElement("[Connection error]");
                });
            }
        }).start();
    }
    
    private void refreshModelsForProvider(String provider, String endpoint) {
        new Thread(() -> {
            try {
                state.setOllamaEndpoint(endpoint);
                OllamaClient client = new OllamaClient(state);
                java.util.List<String> models = client.getAvailableModels();
                
                SwingUtilities.invokeLater(() -> {
                    modelListModel.clear();
                    if (models != null && !models.isEmpty()) {
                        for (String model : models) {
                            modelListModel.addElement(model);
                        }
                        statusArea.append("✓ Found " + models.size() + " Ollama models\n");
                    } else {
                        modelListModel.addElement("[No models found. Run: ollama pull <model>]");
                        statusArea.append("No models found. Pull models using ollama pull\n");
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    modelListModel.clear();
                    modelListModel.addElement("[Error fetching models: " + e.getMessage() + "]");
                });
            }
        }).start();
    }
    
    private void fetchOpenAIModels(String apiKey, String baseUrl) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(baseUrl + "/models");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setConnectTimeout(10000);
                
                StringBuilder response = new StringBuilder();
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                org.json.JSONObject json = new org.json.JSONObject(response.toString());
                org.json.JSONArray data = json.getJSONArray("data");
                java.util.List<String> gptModels = new java.util.ArrayList<>();
                for (int i = 0; i < data.length(); i++) {
                    String modelId = data.getJSONObject(i).getString("id");
                    if (modelId.contains("gpt") || modelId.contains("o1") || modelId.contains("o3")) {
                        gptModels.add(modelId);
                    }
                }
                
                // Empty result handling
                if (gptModels.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        modelListModel.clear();
                        modelListModel.addElement("[No models found - check API key permissions]");
                        statusArea.append("⚠ No models returned from API. Verify API key has access to /models endpoint.\n");
                    });
                    conn.disconnect();
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    modelListModel.clear();
                    if (!gptModels.isEmpty()) {
                        for (String model : gptModels) {
                            modelListModel.addElement(model);
                        }
                        statusArea.append("✓ Loaded " + gptModels.size() + " OpenAI models\n");
                    } else {
                        modelListModel.addElement("[No GPT models found]");
                    }
                });
                conn.disconnect();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    modelListModel.clear();
                    modelListModel.addElement("[Error fetching models: " + e.getMessage() + "]");
                });
            }
        }).start();
    }
    
    private void fetchClaudeModels(String apiKey, String baseUrl) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(baseUrl + "/models");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("x-api-key", apiKey);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                conn.setConnectTimeout(10000);
                
                StringBuilder response = new StringBuilder();
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                org.json.JSONObject json = new org.json.JSONObject(response.toString());
                org.json.JSONArray data = json.getJSONArray("data");
                java.util.List<String> claudeModels = new java.util.ArrayList<>();
                for (int i = 0; i < data.length(); i++) {
                    String modelId = data.getJSONObject(i).getString("id");
                    if (modelId.startsWith("claude")) {
                        claudeModels.add(modelId);
                    }
                }
                
                // Empty result handling
                if (claudeModels.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        modelListModel.clear();
                        modelListModel.addElement("[No models found - check API key permissions]");
                        statusArea.append("⚠ No models returned from API. Verify API key has access to /models endpoint.\n");
                    });
                    conn.disconnect();
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    modelListModel.clear();
                    if (!claudeModels.isEmpty()) {
                        for (String model : claudeModels) {
                            modelListModel.addElement(model);
                        }
                        statusArea.append("✓ Loaded " + claudeModels.size() + " Claude models\n");
                    } else {
                        modelListModel.addElement("[No Claude models found]");
                    }
                });
                conn.disconnect();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    modelListModel.clear();
                    modelListModel.addElement("[Error fetching models: " + e.getMessage() + "]");
                });
            }
        }).start();
    }

    private void fetchAndDisplayOpenAIModels(String apiKey, String baseUrl) {
        try {
            java.net.URL url = new java.net.URL(baseUrl + "/models");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(10000);
            
            StringBuilder response = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            org.json.JSONObject json = new org.json.JSONObject(response.toString());
            org.json.JSONArray data = json.getJSONArray("data");
            java.util.List<String> gptModels = new java.util.ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                String modelId = data.getJSONObject(i).getString("id");
                if (modelId.contains("gpt") || modelId.contains("o1") || modelId.contains("o3")) {
                    gptModels.add(modelId);
                }
            }
            
            SwingUtilities.invokeLater(() -> {
                modelListModel.clear();
                if (!gptModels.isEmpty()) {
                    for (String model : gptModels) {
                        modelListModel.addElement(model);
                    }
                    statusArea.append("\n✓ Loaded " + gptModels.size() + " OpenAI models\n");
                } else {
                    modelListModel.addElement("[No GPT models found]");
                }
            });
            conn.disconnect();
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                modelListModel.clear();
                modelListModel.addElement("[Error fetching models: " + e.getMessage() + "]");
            });
        }
    }
    
    private void fetchAndDisplayClaudeModels(String apiKey, String baseUrl) {
        try {
            java.net.URL url = new java.net.URL(baseUrl + "/models");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setConnectTimeout(10000);
            
            StringBuilder response = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }
            
            org.json.JSONObject json = new org.json.JSONObject(response.toString());
            org.json.JSONArray data = json.getJSONArray("data");
            java.util.List<String> claudeModels = new java.util.ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                String modelId = data.getJSONObject(i).getString("id");
                if (modelId.startsWith("claude")) {
                    claudeModels.add(modelId);
                }
            }
            
            SwingUtilities.invokeLater(() -> {
                modelListModel.clear();
                if (!claudeModels.isEmpty()) {
                    for (String model : claudeModels) {
                        modelListModel.addElement(model);
                    }
                    statusArea.append("\n✓ Loaded " + claudeModels.size() + " Claude models\n");
                } else {
                    modelListModel.addElement("[No Claude models found]");
                }
            });
            conn.disconnect();
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                modelListModel.clear();
                modelListModel.addElement("[Error fetching models: " + e.getMessage() + "]");
            });
        }
    }

    private void resetToDefaults() {
        // Reset Ollama endpoint in unified Base URL field if Ollama is selected
        String selectedProvider = (String) providerSelector.getSelectedItem();
        if ("ollama".equals(selectedProvider)) {
            unifiedBaseUrlField.setText("http://127.0.0.1:11434");
        } else if ("openai".equals(selectedProvider)) {
            unifiedBaseUrlField.setText("https://api.openai.com/v1");
        } else if ("claude".equals(selectedProvider)) {
            unifiedBaseUrlField.setText("https://api.anthropic.com/v1");
        }
        
        analysisModelField.setText("qwen2.5:7b-instruct");
        payloadModelField.setText("qwen2.5-coder:7b");
        temperatureSpinner.setValue(0.7);
        maxTokensSpinner.setValue(state.getMaxTokens());
        maxContextSpinner.setValue(state.getMaxContextSize());
        redactAuthCheckbox.setSelected(true);
        redactCookiesCheckbox.setSelected(true);
        statusArea.setText("Settings reset to defaults (not saved yet)\n");
    }

    private void showWelcomeMessage() {
        String welcomeMsg = 
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "     Welcome to Suite-o-llama     \n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "\n" +
            "📋 Getting Started:\n" +
            "   • Configure your AI Provider in the panel above\n" +
            "   • Refresh models to see available LLMs\n" +
            "\n" +
            "🎯 Quick Tips:\n" +
            "   • Right-click any request → Send to Suite-o-llama\n" +
            "   • Check Repeater tabs for AI analysis\n" +
            "\n" +
            "⚙️ Active Configuration:\n" +
            "   • Current Provider: " + state.getActiveProvider() + "\n" +
            "   • Analysis Model: " + state.getAnalysisModel() + "\n" +
            "   • Payload Model: " + state.getPayloadModel() + "\n" +
            "\n" +
            "👨‍💻 Creator: VermaOps | GitHub\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n";
        statusArea.setText(welcomeMsg);
    }
}
