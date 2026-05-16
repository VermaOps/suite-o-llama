package burp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import burp.OllamaClient;

public class RepeaterAIResponseTab implements IMessageEditorTab {
    private final ExtensionState state;
    private final IMessageEditorController controller;
    private final ProviderFactory providerFactory;
    private LLMProvider activeProvider;
    private final PromptEngine promptEngine;

    // ALL COMPONENTS DECLARED AS FIELDS
    private JPanel panel;
    private JTextArea promptArea;
    private JTextArea responseArea;
    private JButton analyzeButton;
    private JButton cancelButton;
    private JButton clearButton;
    private JCheckBox includeRequestCheckbox;
    private byte[] currentMessage;
    
    private volatile boolean isAnalyzing = false;
    private volatile OllamaClient.ResponseCallback currentCallback;
    private volatile String currentModel;
    private volatile boolean isCancelling = false;
    private volatile long requestStartTime = 0; // Track per-request timing
    private volatile String requestIdentifier = ""; // Unique ID for this request
    private boolean firstMessageLoaded = false;
    private volatile String savedLLMResponse = "";
    private volatile String savedPromptText = "";
    private volatile boolean hasSavedResponse = false;
    private ConversationSession conversationSession;
    private static final ScheduledExecutorService timeoutScheduler = 
        Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Conversation-Timeout-Checker-Response");
            return t;
        });

    public RepeaterAIResponseTab(ExtensionState state, IMessageEditorController controller) {
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
        promptArea.setText("Analyze this HTTP response:\n\n" +
                     "Response Headers:\n{{res_headers}}\n\n" +
                     "Response Body:\n{{res_body}}\n\n" +
                     "Look for sensitive data exposure, security headers, and potential issues.");

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
        
        // Control panel - ALL BUTTONS INITIALIZED AS FIELDS
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearResponse());

        analyzeButton = new JButton("Analyze with AI");
        analyzeButton.addActionListener(e -> analyzeResponse());
        
        cancelButton = new JButton("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> {
            // Immediate UI feedback
            responseArea.append("\n" + "=".repeat(60) + "\nCancelling request...\n" + "=".repeat(60) + "\n");
            responseArea.setCaretPosition(responseArea.getDocument().getLength());
            
            isCancelling = true;
            // Call the enhanced cancellation
            if (activeProvider != null) {
                activeProvider.cancel();
            }
    
            // Show cancellation in progress
            cancelButton.setEnabled(false);
            cancelButton.setText("Cancelling...");

            // Re-enable after 0.5 seconds
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
        
        includeRequestCheckbox = new JCheckBox("Include Request", true);
        includeRequestCheckbox.setToolTipText("Include the request in context for better analysis");
        
        controlPanel.add(clearButton);
        controlPanel.add(analyzeButton);
        controlPanel.add(cancelButton);
        controlPanel.add(new JLabel("  "));
        controlPanel.add(includeRequestCheckbox);
        
        promptPanel.add(controlPanel, BorderLayout.SOUTH);
        
        panel.add(promptPanel, BorderLayout.NORTH);
        
        // Bottom: Response area
        JPanel aiResponsePanel = new JPanel(new BorderLayout());
        aiResponsePanel.setBorder(BorderFactory.createTitledBorder("AI Analysis"));
        
        responseArea = new JTextArea(15, 40);
        responseArea.setEditable(false);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);
        responseArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        responseArea.setText("Load a response and click 'Analyze with AI'");
        
        aiResponsePanel.add(new JScrollPane(responseArea), BorderLayout.CENTER);
        
        panel.add(aiResponsePanel, BorderLayout.CENTER);
    }
    
    private void clearResponse() {
        if (responseArea.getText() != null && !responseArea.getText().trim().isEmpty()) {
            int result = JOptionPane.showConfirmDialog(panel,
                "Clear LLM response history?\n\nResponse will be preserved.",
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
                state.getStdout().println("Cleared LLM response in Response AI tab");
            }
        }
    }

    private void saveCurrentPrompt() {
        savedPromptText = promptArea.getText();
    }

    private void resetStateForNewMessage() {
        // Terminate old conversation session when loading new message
        if (conversationSession != null) {
            conversationSession.terminate();
            conversationSession = null;
        }
        // Only reset state variables for first-time initialization
        // Do NOT cancel ongoing analysis - let it complete
        isAnalyzing = false;
        currentCallback = null;
        isCancelling = false;
        requestStartTime = 0;
        currentMessage = null;
        
        // Generate a unique identifier for this request
        requestIdentifier = System.currentTimeMillis() + "-" + hashCode();

        // CRITICAL FIX: Reset button to enabled state for new messages
        SwingUtilities.invokeLater(() -> {
            analyzeButton.setEnabled(true);
            analyzeButton.setText("Analyze with AI");
        });
    }

    private void analyzeResponse() {
        // Generate a unique ID for this specific analysis session
        final String analysisId = requestIdentifier + "-" + System.currentTimeMillis();
        final byte[] messageToAnalyze = currentMessage; // Capture at time of click

        if (messageToAnalyze == null || messageToAnalyze.length == 0) {
            state.getStdout().println("[RepeaterAIResponseTab] No currentMessage for ID: " + analysisId);
            responseArea.append("\n" + "=".repeat(60) + "\nNo response to analyze\n" + "=".repeat(60) + "\n");
            return;
        }
        
        // Check if this is still the current message (user hasn't switched)
        if (!analysisId.startsWith(requestIdentifier)) {
            state.getStdout().println("[RepeaterAIResponseTab] Analysis cancelled - message changed");
            return;
        }

        // Refresh provider before use
        refreshActiveProvider();

        // Check Ollama health
        state.getStdout().println("[RepeaterAIResponseTab] Checking Ollama health...");
        if (activeProvider == null || !activeProvider.checkHealth()) {
            state.getStdout().println("[RepeaterAIResponseTab] Ollama health check FAILED");
            responseArea.append("\n" + "=".repeat(60) + "\nERROR: Ollama disconnected\nEnsure Ollama is running at: " + 
                            state.getOllamaEndpoint() + "\n" + "=".repeat(60) + "\n");
            return;
        }

        IHttpService service = controller.getHttpService();
        byte[] request = controller.getRequest();
        
        RequestContext context;
        if (includeRequestCheckbox.isSelected() && request != null) {
            context = new RequestContext(request, messageToAnalyze, service);
        } else {
            context = new RequestContext(new byte[0], messageToAnalyze, service);
        }

        String prompt = promptEngine.processTemplate(promptArea.getText(), context);
        String model = state.getAnalysisModel();

        // Initialize conversation session if null
        if (conversationSession == null || conversationSession.isTerminated()) {
            conversationSession = new ConversationSession();
            conversationSession.scheduleTimeout(timeoutScheduler, () -> {
                state.getStdout().println("[RepeaterAIResponseTab] Conversation session timed out due to inactivity");
            });
        }
            
        // Get conversation context for this prompt
        String conversationContext = conversationSession.buildConversationPrompt("");
        
        state.getStdout().println("[RepeaterAIResponseTab] Model: " + model);

        // Check model availability
        state.getStdout().println("[RepeaterAIResponseTab] Checking model availability...");
        if (!activeProvider.isModelAvailable(model)) {
            state.getStdout().println("[RepeaterAIResponseTab] Model NOT available");
            responseArea.append("\n" + "=".repeat(60) + "\nERROR: Model not available: " + model + 
                            "\nRun: ollama pull " + model + "\n" + "=".repeat(60) + "\n");
            return;
        }
        
        // Check model availability
        if (!activeProvider.isModelAvailable(model)) {
            responseArea.append("\n" + "=".repeat(60) + "\nERROR: Model not available: " + model + 
                           "\nRun: ollama pull " + model + "\n" + "=".repeat(60) + "\n");
            return;
        }
        // Set analyzing state
        isAnalyzing = true;
        currentModel = model;
        requestStartTime = System.currentTimeMillis();
        
        // === CRITICAL FIX: SINGLE ATOMIC UI UPDATE ===
        SwingUtilities.invokeLater(() -> {
            try {
                // Only update if this is still the current analysis
                if (isAnalyzing && analysisId.startsWith(requestIdentifier)) {
                    analyzeButton.setEnabled(false);
                    analyzeButton.setText("Analyzing..."); 
                    cancelButton.setEnabled(true);
                    clearButton.setEnabled(false);
                    includeRequestCheckbox.setEnabled(false);
                    
                    String separator = "=".repeat(60);
                    String currentText = responseArea.getText();
                    String newText = currentText + 
                        "\n" + separator + 
                        "\nAnalyzing response with " + model + "..." + 
                        "\n" + separator + "\n";
                    
                    responseArea.setText(newText);
                    responseArea.setCaretPosition(responseArea.getDocument().getLength());
                    responseArea.repaint();
                }
            } catch (Exception e) {
                state.getStderr().println("[RepeaterAIResponseTab] UI update error: " + e.getMessage());
            }
        });

        // START TIME measurement
        final long startTime = System.currentTimeMillis();
        state.getStdout().println("[RepeaterAIResponseTab] Calling ollamaClient.generateAsync()");

        // Create the callback
        currentCallback = new LLMProvider.ResponseCallback() {
            @Override
            public void onSuccess(String response, long timeMs, int estimatedTokens) {
                // Check if this callback is still valid for the current message
                if (!analysisId.startsWith(requestIdentifier) || isCancelling) return;
                
                state.getStdout().println("[RepeaterAIResponseTab] onSuccess() received");
                SwingUtilities.invokeLater(() -> {
                    // Only update if we're still analyzing the same message
                    if (isAnalyzing && analysisId.startsWith(requestIdentifier) && !isCancelling) {
                        double executionTimeSeconds = timeMs / 1000.0;
                        String separator = "=".repeat(60);
                    
                        String result = String.format(
                            "\n%s\n" +
                            "✓ RESPONSE ANALYSIS COMPLETED\n" +
                            "Model: %s | Time: %.2fs | Tokens: ~%d\n" +
                            "%s\n\n" +
                            "%s\n" +
                            "%s\n",
                            separator,
                            model, executionTimeSeconds, estimatedTokens,
                            separator,
                            response,
                            separator
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

                        // RESTORE UI STATE
                        resetUIState();
                        state.getStdout().println("[RepeaterAIResponseTab] Analysis complete for ID: " + analysisId);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                if (!analysisId.startsWith(requestIdentifier)) return;
                
                final long errorTime = System.currentTimeMillis() - requestStartTime;
                double errorTimeSeconds = errorTime / 1000.0;
                
                SwingUtilities.invokeLater(() -> {
                    if (analysisId.startsWith(requestIdentifier) && !isCancelling) {
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
                                "\n%s\n✗ ERROR (after %.2fs)\n%s\n\nCheck provider configuration\n%s\n",
                                "=".repeat(60), errorTimeSeconds, error, "=".repeat(60)
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
                            "\n%s\n" +
                            "✗ REQUEST CANCELLED\n" +
                            "Cancellation time: %.2fs\n" +
                            "%s\n",
                            separator,
                            cancelTimeMs / 1000.0,
                            separator
                        );
        
                        responseArea.append(cancelMsg);
                        responseArea.setCaretPosition(responseArea.getDocument().getLength());
                        
                        // RESTORE UI STATE
                        resetUIState();
                        isCancelling = false;
                    }
                });
            }

            public void cleanup() {
                if (conversationSession != null) {
                    conversationSession.terminate();
                }
            }
        };
        
        // Store the model and start the analysis
        currentModel = model;
        activeProvider.generateAsync(prompt, model, conversationContext, currentCallback);
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
        // Only enable for non-empty responses
        boolean shouldEnable = !isRequest && content != null && content.length > 0;
        return shouldEnable;
    }
    
    @Override
    public void setMessage(byte[] content, boolean isRequest) {
        // Only process if this is a response (we're in RepeaterAIResponseTab for responses)
        if (isRequest) return;
        
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
                    responseArea.setText("No response to analyze");
                }
                
                responseArea.setCaretPosition(0);
            });
        } else {
            // Subsequent response update - preserve UI state
            // Update the current message but preserve prompt and response
            this.currentMessage = content;
            
            SwingUtilities.invokeLater(() -> {
                if (content != null && content.length > 0) {
                    // CRITICAL FIX: Only enable button if NOT currently analyzing
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
                            currentResponse.equals("Response loaded") ||
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

    // new method to reset UI state:
    private void resetUIState() {
        isAnalyzing = false;
        currentCallback = null;
        
        analyzeButton.setEnabled(true);
        analyzeButton.setText("Analyze with AI");
        cancelButton.setEnabled(false);
        cancelButton.setText("Cancel");
        clearButton.setEnabled(true);
        includeRequestCheckbox.setEnabled(true);
        saveCurrentPrompt(); // Save current prompt text
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