package burp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RepeaterAITab implements IMessageEditorTab {
    private final ExtensionState state;
    private final IMessageEditorController controller;
    private final ProviderFactory providerFactory;
    private LLMProvider activeProvider;
    private final PromptEngine promptEngine;
    
    // DECLARED ALL COMPONENTS AS FIELDS
    private JPanel panel;
    private JTextArea promptArea;
    private JTextArea responseArea;
    private JButton analyzeButton;
    private JButton cancelButton;
    private JButton clearButton;
    private JComboBox<String> modelSelector;
    private byte[] currentMessage;
    
    // PER-MESSAGE STATE
    private volatile boolean isAnalyzing = false;
    private volatile String requestIdentifier = "";
    private volatile long requestStartTime = 0;
    private boolean firstMessageLoaded = false;
    private volatile String savedLLMResponse = "";
    private volatile String savedPromptText = "";
    private volatile boolean hasSavedResponse = false;
    private ConversationSession conversationSession;
    private static final ScheduledExecutorService timeoutScheduler = 
        Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Conversation-Timeout-Checker");
            return t;
        });

    public RepeaterAITab(ExtensionState state, IMessageEditorController controller) {
        this.state = state;
        this.controller = controller;
        this.providerFactory = new ProviderFactory(state);
        this.activeProvider = providerFactory.getActiveProvider(); // Each tab gets its own client
        this.promptEngine = new PromptEngine(state);
        
        initUI();
    }

    private void refreshActiveProvider() {
        this.activeProvider = providerFactory.getActiveProvider();
    }
    
    private void initUI() {
        panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Top: Prompt editor
        JPanel promptPanel = new JPanel(new BorderLayout(5, 5));
        promptPanel.setBorder(BorderFactory.createTitledBorder("Prompt"));
        
        promptArea = new JTextArea(6, 40);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        promptArea.setText("I am an authorized ethical pentester with written permission to test the target system. This is for a sanctioned security assessment to help developers fix vulnerabilities before production.\n\nHTTP Request:\n------------\n{{full_request}}\n------------\n\nAnalyze the above request and help identify security vulnerabilities safely. Provide:\n1. **Entry points & loopholes** — with safe, non-destructive payloads for different injection types (varied techniques, not repetitive patterns).\n2. **WAF bypass techniques** (defensive testing only).\n3. **Additional insights** — anything relevant you spot (logic flaws, misconfigurations, edge cases).\n4. **No remediation** — skip fixes entirely.\n\nRules:\n- No data modification\n- No exfiltration to third parties unless explicitly asked\n- No malware\n\nAfter your response, ask me: \"Would you like alternative payloads (e.g., encoding variants, context-specific bypasses)?\"");
        
        // Save prompt text when user modifies it
        promptArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                saveCurrentPrompt();
            }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                saveCurrentPrompt();
            }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                saveCurrentPrompt();
            }
            private void saveCurrentPrompt() {
                savedPromptText = promptArea.getText();
            }
        });

        promptPanel.add(new JScrollPane(promptArea), BorderLayout.CENTER);
        
        // Control panel - FIXED: Initialize all buttons as fields
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearResponse());

        analyzeButton = new JButton("Analyze with AI");
        analyzeButton.addActionListener(e -> analyzeRequest());
        
        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> {
            // Immediate UI feedback
            responseArea.append("\n" + "=".repeat(60) + "\nCancelling request...\n" + "=".repeat(60) + "\n");
            responseArea.setCaretPosition(responseArea.getDocument().getLength());

            // Call the enhanced cancellation
            if (activeProvider != null) {
                activeProvider.cancel();
            }
    
            // Show cancellation in progress
            cancelButton.setEnabled(false);
            cancelButton.setText("Cancelling...");

            // Re-enable after 1 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {}
                SwingUtilities.invokeLater(() -> {
                    cancelButton.setEnabled(true);
                    cancelButton.setText("Cancel");
                });
            }).start();
        });
        
        modelSelector = new JComboBox<>(new String[]{"Analysis Model", "Payload Model"});
        
        controlPanel.add(clearButton);
        controlPanel.add(analyzeButton);
        controlPanel.add(cancelButton);
        controlPanel.add(new JLabel("  "));
        controlPanel.add(modelSelector);
        
        promptPanel.add(controlPanel, BorderLayout.SOUTH);
        
        panel.add(promptPanel, BorderLayout.NORTH);
        
        // Bottom: Response area
        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBorder(BorderFactory.createTitledBorder("AI Analysis"));
        
        responseArea = new JTextArea(15, 40);
        responseArea.setEditable(false);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);
        responseArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        responseArea.setText("Load a response and click 'Analyze with AI'");
        
        responsePanel.add(new JScrollPane(responseArea), BorderLayout.CENTER);
        
        panel.add(responsePanel, BorderLayout.CENTER);
    }
    
    private void clearResponse() {
        if (responseArea.getText() != null && !responseArea.getText().trim().isEmpty()) {
            int result = JOptionPane.showConfirmDialog(panel,
                "Clear LLM response history?\n\nRequest will be preserved.",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        
            if (result == JOptionPane.YES_OPTION) {
                responseArea.setText("Click 'Analyze with AI' to start analysis");
                // CLEAR SAVED STATE
                savedLLMResponse = "";
                savedPromptText = "";
                hasSavedResponse = false;
                // Clear conversation history but keep session alive
                if (conversationSession != null) {
                    conversationSession.clearHistory();
                }
                state.getStdout().println("Cleared LLM response in Repeater AI tab");
            }
        }
    }

    private void saveCurrentPrompt() {
        savedPromptText = promptArea.getText();
    }

    private void analyzeRequest() {
        // Generate unique ID for this analysis session
        final String analysisId = requestIdentifier + "-" + System.currentTimeMillis();
        final byte[] messageToAnalyze = currentMessage; // Capture at time of click
        
        if (messageToAnalyze == null || messageToAnalyze.length == 0) {
            responseArea.append("\n" + "=".repeat(60) + "\nNo request to analyze\n" + "=".repeat(60) + "\n");
            return;
        }
        
        // Check if this is still the current message
        if (!analysisId.startsWith(requestIdentifier)) {
            state.getStdout().println("[RepeaterAITab] Analysis cancelled - message changed");
            return;
        }
    
        // Refresh provider before use
        refreshActiveProvider();

        // Check active provider health
        if (activeProvider == null || !activeProvider.checkHealth()) {
            String errorMsg = activeProvider == null ? "No provider configured" : 
                "Provider " + activeProvider.getProviderName() + " not available";
            responseArea.append("\n" + "=".repeat(60) + "\nERROR: " + errorMsg + "\n" + "=".repeat(60) + "\n");
            return;
        }
    
        IHttpService service = controller.getHttpService();
        byte[] response = controller.getResponse();
        RequestContext context;
        if (response != null && response.length > 0) {
            context = new RequestContext(messageToAnalyze, response, service);
        } else {
            context = new RequestContext(messageToAnalyze, service);
        }
    
        String prompt = promptEngine.processTemplate(promptArea.getText(), context);
        String model = modelSelector.getSelectedIndex() == 0 ? 
                    state.getAnalysisModel() : state.getPayloadModel();
            
        // Initialize conversation session if null
        if (conversationSession == null || conversationSession.isTerminated()) {
            conversationSession = new ConversationSession();
            conversationSession.scheduleTimeout(timeoutScheduler, () -> {
                // Non-destructive timeout - just terminate session, keep UI
                state.getStdout().println("[RepeaterAITab] Conversation session timed out due to inactivity");
            });
        }
            
        // Get conversation context for this prompt
        String conversationContext = conversationSession.buildConversationPrompt("");
    
        // Check model availability
        if (activeProvider != null && !activeProvider.isModelAvailable(model)) {
            responseArea.append("\n" + "=".repeat(60) + "\nERROR: Model not available: " + model + 
                               "\nRun: ollama pull " + model + "\n" + "=".repeat(60) + "\n");
            return;
        }
        
        // Set analyzing state
        isAnalyzing = true;
        requestStartTime = System.currentTimeMillis();
        
        // === UI UPDATE ===
        SwingUtilities.invokeLater(() -> {
            try {
                // Only update if this is still the current analysis
                if (isAnalyzing && analysisId.startsWith(requestIdentifier)) {
                    analyzeButton.setEnabled(false);
                    analyzeButton.setText("Analyzing..."); 
                    cancelButton.setEnabled(true);
                    clearButton.setEnabled(false);
                    modelSelector.setEnabled(false);
                    
                    String separator = "=".repeat(60);
                    String currentText = responseArea.getText();
                    String newText = currentText + 
                        "\n" + separator + 
                        "\nAnalyzing with " + model + "..." + 
                        "\n" + separator + "\n";
                    
                    responseArea.setText(newText);
                    responseArea.setCaretPosition(responseArea.getDocument().getLength());
                    responseArea.repaint();
                }
            } catch (Exception e) {
                state.getStderr().println("[RepeaterAITab] UI update error: " + e.getMessage());
            }
        });
    
        // Create callback with analysis ID check
        if (activeProvider != null) {
            activeProvider.generateAsync(prompt, model, conversationContext, new LLMProvider.ResponseCallback() {
            @Override
            public void onSuccess(String response, long timeMs, int estimatedTokens) {
                // Check if this callback is still valid for the current message
                if (!analysisId.startsWith(requestIdentifier)) return;
                
                SwingUtilities.invokeLater(() -> {
                    // Only update if we're still analyzing the same message
                    if (isAnalyzing && analysisId.startsWith(requestIdentifier)) {
                        double executionTimeSeconds = timeMs / 1000.0;
                    
                        String result = String.format(
                            "\n%s\n✓ ANALYSIS COMPLETE\nModel: %s | Time: %.2fs | Tokens: ~%d\n%s\n\n%s\n%s\n",
                            "=".repeat(60), model, executionTimeSeconds, estimatedTokens,
                            "=".repeat(60), response, "=".repeat(60)
                        );
                    
                        responseArea.append(result);
                        responseArea.setCaretPosition(responseArea.getDocument().getLength());
                        
                        // Add to conversation history
                        if (conversationSession != null && !conversationSession.isTerminated()) {
                            conversationSession.addExchange(prompt, response);
                        }

                        // SAVE THE RESPONSE FOR PERSISTENCE
                        savedLLMResponse = responseArea.getText();
                        savedPromptText = promptArea.getText();
                        hasSavedResponse = true;

                        // RESTORE UI
                        resetUIState();
                        state.getStdout().println("[RepeaterAITab] Analysis complete for ID: " + analysisId);
                    }
                });
            }
        
            @Override
            public void onError(String error) {
                if (!analysisId.startsWith(requestIdentifier)) return;
                
                final long errorTime = System.currentTimeMillis() - requestStartTime;
                double errorTimeSeconds = errorTime / 1000.0;
            
                SwingUtilities.invokeLater(() -> {
                    if (analysisId.startsWith(requestIdentifier)) {
                        String provider = activeProvider != null ? activeProvider.getProviderName() : "Unknown";
                        String errorMsg;
                        
                        if (("OpenAI".equals(provider) || "Claude".equals(provider)) &&
                            (error.toLowerCase().contains("connection") || 
                             error.toLowerCase().contains("timeout") ||
                             error.toLowerCase().contains("401") ||
                             error.toLowerCase().contains("403"))) {
                            errorMsg = String.format(
                                "\n%s\n✗ UNABLE TO CONNECT TO %s\nError: %s\n\nPlease check your configuration settings\n%s\n",
                                "=".repeat(60), provider.toUpperCase(), error, "=".repeat(60)
                            );
                        } else {
                            errorMsg = String.format(
                                "\n%s\n✗ ERROR (after %.2fs)\n%s\n\nCheck provider at: %s\n%s\n",
                                "=".repeat(60), errorTimeSeconds, error, 
                                "openai".equals(state.getActiveProvider()) ? state.getOpenAiBaseUrl() : state.getClaudeBaseUrl(),
                                "=".repeat(60)
                            );
                        }
                    
                        responseArea.append(errorMsg);
                        responseArea.setCaretPosition(responseArea.getDocument().getLength());
                        resetUIState();
                    }
                });
            }

            @Override
            public void onCancelled(long cancelTimeMs) {
                // Check if this callback is still valid for the current message
                if (!analysisId.startsWith(requestIdentifier)) return;
                
                SwingUtilities.invokeLater(() -> {
                    if (analysisId.startsWith(requestIdentifier)) {
                        String separator = "=".repeat(60);
                        String cancelMsg = String.format(
                            "\n%s\n✗ REQUEST CANCELLED\nCancellation time: %.2fs\n%s\n",
                            separator, cancelTimeMs / 1000.0, separator
                        );
        
                        responseArea.append(cancelMsg);
                        responseArea.setCaretPosition(responseArea.getDocument().getLength());
                        
                        // RESTORE UI
                        resetUIState();
                    }
                });
            }

            public void cleanup() {
                if (providerFactory != null) {
                    providerFactory.shutdownAll();
                }
                if (conversationSession != null) {
                    conversationSession.terminate();
                }
            }
        });
    }}

    private void resetUIState() {
        isAnalyzing = false;
        
        analyzeButton.setEnabled(true);
        analyzeButton.setText("Analyze with AI");
        cancelButton.setEnabled(false);
        clearButton.setEnabled(true);
        modelSelector.setEnabled(true);
        saveCurrentPrompt(); // Save current prompt text
    }
    
    @Override
    public String getTabCaption() {
        return "Suite-o-llama AI";
    }
    
    @Override
    public Component getUiComponent() {
        return panel;
    }
    
    @Override
    public boolean isEnabled(byte[] content, boolean isRequest) {
        // Only show for requests
        state.getStdout().println("[RepeaterAITab] isEnabled() called - isRequest: " + isRequest + ", content length: " + (content != null ? content.length : 0));
        return isRequest;
    }
    
    @Override
    public void setMessage(byte[] content, boolean isRequest) {
        // Only process if this is a request (we're in RepeaterAITab for requests)
        if (!isRequest) return;
        
        // Check if this is the first time loading a message
        if (!firstMessageLoaded) {
            // First load - reset state as before
            resetStateForNewMessage();
            firstMessageLoaded = true;
            
            this.currentMessage = content;
            
            SwingUtilities.invokeLater(() -> {
                if (content != null && content.length > 0) {
                    analyzeButton.setEnabled(true);
                    analyzeButton.setText("Analyze with AI");
                    responseArea.setText("Click 'Analyze with AI' to start analysis");
                } else {
                    analyzeButton.setEnabled(false);
                    responseArea.setText("No request to analyze");
                }
            });
        } else {
            // Subsequent request update (SEND/Ctrl+R) - preserve UI state
            // Update the current message but preserve prompt and response
            this.currentMessage = content;
            
            SwingUtilities.invokeLater(() -> {
                if (content != null && content.length > 0) {
                    if (!isAnalyzing) {
                        analyzeButton.setEnabled(true);
                    }
                    // RESTORE SAVED RESPONSE IF IT EXISTS
                    if (hasSavedResponse) {
                        promptArea.setText(savedPromptText);
                        responseArea.setText(savedLLMResponse);
                        responseArea.setCaretPosition(responseArea.getDocument().getLength());
                    } else {
                        // Only show default message if no saved response exists
                        String currentResponse = responseArea.getText();
                        if (currentResponse == null || currentResponse.trim().isEmpty() ||
                            currentResponse.equals("Load a response and click 'Analyze with AI'") ||
                            currentResponse.equals("Click 'Analyze with AI' to analyze")) {
                            responseArea.setText("Click 'Analyze with AI' to analyze");
                        }
                    }
                } else {
                    analyzeButton.setEnabled(false);
                }
            });
        }
    }
    
    private void resetStateForNewMessage() {
        // Only reset state variables for first-time initialization
        // Do NOT cancel ongoing analysis - let it complete
        isAnalyzing = false;
        requestIdentifier = System.currentTimeMillis() + "-" + hashCode();
        requestStartTime = 0;
        // Preserve prompt text but clear LLM response for new messages
        // This allows prompt to persist but response resets for new requests
        savedLLMResponse = "";
        hasSavedResponse = false;
        // Terminate old conversation session when loading new message
        if (conversationSession != null) {
            conversationSession.terminate();
            conversationSession = null;
        }
        // CRITICAL FIX: Reset button to enabled state for new messages
        SwingUtilities.invokeLater(() -> {
            analyzeButton.setEnabled(true);
            analyzeButton.setText("Analyze with AI");
        });
    }

    @Override
    public byte[] getMessage() {
        return currentMessage;
    }
    
    @Override
    public boolean isModified() {
        return false;
    }
    
    @Override
    public byte[] getSelectedData() {
        return null;
    }

    // Package-private accessors for state transfer
    JTextArea getPromptArea() {
        return promptArea;
    }
    
    JTextArea getResponseArea() {
        return responseArea;
    }
    
    String getCurrentPromptText() {
        return promptArea.getText();
    }
    
    String getCurrentResponseText() {
        return responseArea.getText();
    }
}