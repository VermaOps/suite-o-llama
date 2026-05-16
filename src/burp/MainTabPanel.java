package burp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService; 
import java.util.concurrent.TimeUnit;

public class MainTabPanel extends JPanel implements IMessageEditorController {

    private final ExtensionState state;
    private final ProviderFactory providerFactory;
    private LLMProvider activeProvider;
    private final PromptEngine promptEngine;
    private final AutocompleteEngine autocompleteEngine;

    // UI Components
    private IMessageEditor requestEditor;
    private IMessageEditor responseEditor;
    private JTextArea promptArea;
    private JTextArea llmResponseArea;

    private JButton sendToServerBtn;
    private JButton sendToLlmBtn;
    private JButton clearBtn;
    private JButton cancelBtn;
    private JLabel responseTimeLabel; // ADD: For displaying server response time

    private IHttpService currentService;
    private byte[] currentRequest;
    private byte[] currentResponse;
    private AutocompleteContext currentContext;

    private boolean initialEmptyTab = false; // NEW: Track if this is initial empty tab

    private ConversationSession conversationSession;
    private static final ScheduledExecutorService timeoutScheduler = 
        Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Conversation-Timeout-Checker-Main");
            return t;
        });

    public MainTabPanel(ExtensionState state, PromptEngine promptEngine) {
        this.state = state;
        this.providerFactory = new ProviderFactory(state);
        this.activeProvider = providerFactory.getActiveProvider();
        this.promptEngine = promptEngine;
        this.autocompleteEngine = new AutocompleteEngine(state, providerFactory, promptEngine);

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(5, 5, 5, 5));
    
        initUi();
    }

    private void refreshActiveProvider() {
            this.activeProvider = providerFactory.getActiveProvider();
    }
    
    // NEW: Getter/Setter for initial empty tab status
    public void setInitialEmptyTab(boolean isInitial) {
        this.initialEmptyTab = isInitial;
    }
    
    public boolean isInitialEmptyTab() {
        return initialEmptyTab && !hasContent();
    }

    private void initUi() {
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        verticalSplit.setResizeWeight(0.5);  // FIX: Equal split
        verticalSplit.setDividerLocation(0.5);  // FIX: Start at middle

        // ===== Request / Response =====
        JSplitPane reqRespSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        reqRespSplit.setResizeWeight(0.5); // FIX: Equal horizontal split  
        reqRespSplit.setDividerLocation(0.5); // FIX: Start at middle

        requestEditor = state.getCallbacks()
                .createMessageEditor(this, true);

        responseEditor = state.getCallbacks()
                .createMessageEditor(this, false);

        reqRespSplit.setLeftComponent(wrapPanel("Request", requestEditor.getComponent()));
        reqRespSplit.setRightComponent(wrapPanel("Response", responseEditor.getComponent()));

        // ===== Bottom Panel =====
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        promptArea = new JTextArea(4, 80);
        promptArea.setBorder(new TitledBorder("Prompt"));
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setText("I am an authorized ethical pentester with written permission to test the target system. This is for a sanctioned security assessment to help developers fix vulnerabilities before production.\n\nHTTP Request:\n------------\n{{full_request}}\n------------\n\nAnalyze the above request and help identify security vulnerabilities safely. Provide:\n1. **Entry points & loopholes** — with safe, non-destructive payloads for different injection types (varied techniques, not repetitive patterns).\n2. **WAF bypass techniques** (defensive testing only).\n3. **Additional insights** — anything relevant you spot (logic flaws, misconfigurations, edge cases).\n4. **No remediation** — skip fixes entirely.\n\nRules:\n- No data modification\n- No exfiltration to third parties unless explicitly asked\n- No malware\n\nAfter your response, ask me: \"Would you like alternative payloads (e.g., encoding variants, context-specific bypasses)?\"");

        llmResponseArea = new JTextArea(6, 80);
        llmResponseArea.setEditable(false);
        llmResponseArea.setBorder(new TitledBorder("AI Response"));

        bottomPanel.add(new JScrollPane(promptArea), BorderLayout.NORTH);
        bottomPanel.add(new JScrollPane(llmResponseArea), BorderLayout.CENTER);
        bottomPanel.add(buildButtonPanel(), BorderLayout.SOUTH);

        verticalSplit.setTopComponent(reqRespSplit);
        verticalSplit.setBottomComponent(bottomPanel);

        add(verticalSplit, BorderLayout.CENTER);

        hookAutocomplete(promptArea);
        // FIX: Force initial layout
        SwingUtilities.invokeLater(() -> {
            verticalSplit.setDividerLocation(0.5);
            reqRespSplit.setDividerLocation(0.5);
        });
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        sendToServerBtn = new JButton("Send to Server");
        sendToLlmBtn = new JButton("Send to AI");
        cancelBtn = new JButton("Cancel"); // NEW
        clearBtn = new JButton("Clear");

        sendToServerBtn.addActionListener(e -> sendToServer());
        sendToLlmBtn.addActionListener(e -> sendToLlm());
        cancelBtn.addActionListener(e -> cancelLlmRequest()); // NEW
        clearBtn.addActionListener(e -> clearLLMResponse());

        // Initially disable cancel button
        cancelBtn.setEnabled(false);

        panel.add(clearBtn);
        panel.add(sendToServerBtn);
        panel.add(sendToLlmBtn);
        panel.add(cancelBtn); // NEW

        // ADD: Response time label
        responseTimeLabel = new JLabel("");
        responseTimeLabel.setForeground(Color.GRAY);
        responseTimeLabel.setFont(responseTimeLabel.getFont().deriveFont(Font.PLAIN, 11f));
        responseTimeLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        responseTimeLabel.setToolTipText("Server response time");
        panel.add(responseTimeLabel);

        return panel;
    }

    private JPanel wrapPanel(String title, Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new TitledBorder(title));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    // ================= NEW: hasContent() method for tab closing =================
    public boolean hasContent() {
        // Check if tab has any meaningful content
        if (currentRequest != null && currentRequest.length > 0) return true;
        if (currentResponse != null && currentResponse.length > 0) return true;
        
        // Check prompt area for non-default content
        String prompt = promptArea.getText();
        if (prompt != null && !prompt.trim().isEmpty()) {
            // Check if it's NOT the default prompt
            if (!prompt.trim().equals("I am an authorized ethical pentester with written permission to test the target system. This is for a sanctioned security assessment to help developers fix vulnerabilities before production.\n\nHTTP Request:\n------------\n{{full_request}}\n------------\n\nAnalyze the above request and help identify security vulnerabilities safely. Provide:\n1. **Entry points & loopholes** — with safe, non-destructive payloads for different injection types (varied techniques, not repetitive patterns).\n2. **WAF bypass techniques** (defensive testing only).\n3. **Additional insights** — anything relevant you spot (logic flaws, misconfigurations, edge cases).\n4. **No remediation** — skip fixes entirely.\n\nRules:\n- No data modification\n- No exfiltration to third parties unless explicitly asked\n- No malware\n\nAfter your response, ask me: \"Would you like alternative payloads (e.g., encoding variants, context-specific bypasses)?\"")) {
                return true;
            }
        }
        
        if (llmResponseArea.getText() != null && !llmResponseArea.getText().trim().isEmpty()) return true;
        return false;
    }

    private void hookAutocomplete(JTextArea area) {
        area.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_SPACE) {
                    showAutocomplete(area);
                }
            }
        });
    }

    private void showAutocomplete(JTextArea textArea) {
        if (currentContext == null || !currentContext.hasService()) {
            return;
        }

        int caretPos = textArea.getCaretPosition();
        String text = textArea.getText();

        String paramContext = extractParameterContext(text, caretPos);
        if (paramContext == null) {
            return;
        }

        if (currentContext != null && currentContext.getMessage() != null) {
            IHttpRequestResponse message = currentContext.getMessage();
            RequestContext requestContext = new RequestContext(
                message.getRequest(),
                message.getResponse(),
                message.getHttpService()
            );
    
            autocompleteEngine.generatePayloads(
                requestContext,
                paramContext,
                new AutocompleteEngine.PayloadCallback() {
                    @Override
                    public void onPayloadsGenerated(String[] payloads) {
                        if (payloads.length > 0) {
                            SwingUtilities.invokeLater(() ->
                                insertSuggestion(textArea, caretPos, payloads[0])
                            );
                        }
                    }
                }
            );
        }
    }

    private String extractParameterContext(String text, int caretPos) {
        if (caretPos <= 0 || caretPos > text.length()) {
            return null;
        }

        int start = caretPos - 1;
        while (start > 0 && !Character.isWhitespace(text.charAt(start))) {
            start--;
        }

        return text.substring(start, caretPos).trim();
    }

    private void cancelLlmRequest() {
        // Immediate UI feedback
        llmResponseArea.append("\n" + "=".repeat(60) + "\nCancelling LLM request...\n" + "=".repeat(60) + "\n");
        llmResponseArea.setCaretPosition(llmResponseArea.getDocument().getLength());
    
        // Call cancellation
        if (activeProvider != null) {
            activeProvider.cancel();
        }
    
        // Update UI
        cancelBtn.setEnabled(false);
        cancelBtn.setText("Cancelling...");
        sendToLlmBtn.setEnabled(true);
        sendToLlmBtn.setText("Send to AI");
    
        // Re-enable cancel button after delay
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ie) {}
            SwingUtilities.invokeLater(() -> {
                cancelBtn.setEnabled(true);
                cancelBtn.setText("Cancel");
            });
        }).start();
    }

    private void insertSuggestion(JTextArea area, int caretPos, String suggestion) {
        try {
            area.getDocument().insertString(caretPos, suggestion, null);
        } catch (Exception ignored) {
        }
    }

    private void openGitHub() {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://github.com/berserkikun")
                );
                state.getStdout().println("Opened GitHub profile in browser");
            }
        } catch (Exception e) {
            state.getStderr().println("Error opening GitHub: " + e.getMessage());
        }
    }
    
    private void sendToServer() {
        byte[] editedRequest = requestEditor.getMessage(); // FIX: Use editor content
        if (currentService == null || currentRequest == null) {
            JOptionPane.showMessageDialog(this, "No request to send", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        currentRequest = editedRequest; // FIX: Update stored reference
        sendToServerBtn.setEnabled(false);
        sendToServerBtn.setText("Sending...");
        
        // Clear previous response time
        if (responseTimeLabel != null) {
            responseTimeLabel.setText("");
        }

        new Thread(() -> {
            long startTime = System.currentTimeMillis(); // ADD: Start timing
            try {
                IHttpRequestResponse resp = state.getCallbacks().makeHttpRequest(currentService, editedRequest);
                long responseTime = System.currentTimeMillis() - startTime; // ADD: Calculate elapsed time
            
                SwingUtilities.invokeLater(() -> {
                    currentResponse = resp.getResponse();
                    responseEditor.setMessage(currentResponse, false);
                    sendToServerBtn.setEnabled(true);
                    sendToServerBtn.setText("Send to Server");
                
                    if (resp != null) {
                        currentContext = AutocompleteContext.from(resp);
                    }
                    
                    // ADD: Display response time
                    if (responseTimeLabel != null) {
                        responseTimeLabel.setText(responseTime + " ms");
                        responseTimeLabel.setToolTipText("Server response time: " + responseTime + " milliseconds");
                    }
                });
            } catch (Exception e) {
                long errorTime = System.currentTimeMillis() - startTime; // ADD: Timing even on error
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(MainTabPanel.this, "Request failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    sendToServerBtn.setEnabled(true);
                    sendToServerBtn.setText("Send to Server");
                    
                    // ADD: Display error timing
                    if (responseTimeLabel != null) {
                        responseTimeLabel.setText("Error (" + errorTime + " ms)");
                        responseTimeLabel.setToolTipText("Request failed after " + errorTime + " milliseconds");
                    }
                });
            }
        }).start();
    }
        public void cleanup() {
        if (providerFactory != null) {
            providerFactory.shutdownAll();
        }
    }
        private void sendToLlm() {
            byte[] editedRequest = requestEditor.getMessage(); // FIX: Use editor content
            String prompt = promptArea.getText();
            if (prompt.isEmpty() || currentRequest == null) {
                JOptionPane.showMessageDialog(this, "Please load a request and enter a prompt", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            currentRequest = editedRequest; // FIX: Update stored reference
            // Reset any previous cancellation state
            if (activeProvider != null) {
                activeProvider.cancel();
            }// Ensure previous request is cancelled

            try {
                Thread.sleep(100); // Small delay to ensure clean state
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            RequestContext context;
                if (currentResponse != null && currentResponse.length > 0) {
                    context = new RequestContext(editedRequest, currentResponse, currentService); // save current response in context
                } else {
                    context = new RequestContext(editedRequest, currentService); // use edited request
                }
            String finalPrompt = promptEngine.createAnalysisPrompt(context, prompt);
            String model = state.getAnalysisModel();

            // Initialize conversation session if null
            if (conversationSession == null || conversationSession.isTerminated()) {
                conversationSession = new ConversationSession();
                conversationSession.scheduleTimeout(timeoutScheduler, () -> {
                    state.getStdout().println("[MainTabPanel] Conversation session timed out due to inactivity");
                });
            }
                
            // Get conversation context for this prompt
            String conversationContext = conversationSession.buildConversationPrompt("");

            
            // ========== FIX: BATCH ALL INITIAL UI UPDATES TOGETHER ==========
            SwingUtilities.invokeLater(() -> {
                // Clear any partial responses first
                String currentText = llmResponseArea.getText();
                if (!currentText.endsWith("\n")) {
                    currentText += "\n";
                }
                
                String separator = "=".repeat(60);
                String analyzingText = currentText + separator + "\nAnalyzing with " + model + "...\n" + separator + "\n";
                
                llmResponseArea.setText(analyzingText);
                llmResponseArea.setCaretPosition(llmResponseArea.getDocument().getLength());
                
                // Reset UI state
                sendToLlmBtn.setEnabled(false);
                sendToLlmBtn.setText("Analyzing...");
                cancelBtn.setEnabled(true);
                cancelBtn.setText("Cancel");
                
                // Force UI update
                llmResponseArea.repaint();
            });
            
            // ========== FIX: Use a fresh callback each time ==========
            LLMProvider.ResponseCallback callback = new LLMProvider.ResponseCallback() {
                private final long startTime = System.currentTimeMillis();
                
                @Override
                public void onSuccess(String response, long timeMs, int estimatedTokens) {
                    if (Thread.currentThread().isInterrupted()) {
                        return; // Don't update UI if cancelled
                    }
                    
                    SwingUtilities.invokeLater(() -> {
                        // Verify this callback is still valid (not replaced)
                        double executionTimeSeconds = timeMs / 1000.0;
                        String separator = "=".repeat(60);
                        String result = String.format(
                            "\n%s\n" +
                            "✓ ANALYSIS COMPLETE\n" +
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
                        
                        // Only append if this is still the current operation
                        if (sendToLlmBtn.getText().equals("Analyzing...")) {
                            llmResponseArea.append(result);
                            llmResponseArea.setCaretPosition(llmResponseArea.getDocument().getLength());
                            sendToLlmBtn.setEnabled(true);
                            sendToLlmBtn.setText("Send to AI");
                            cancelBtn.setEnabled(false);
                            cancelBtn.setText("Cancel");
                        }

                        // Add to conversation history
                        if (conversationSession != null && !conversationSession.isTerminated()) {
                            conversationSession.addExchange(finalPrompt, response);
                        }

                    });
                }
                
                @Override
                public void onError(String error) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    SwingUtilities.invokeLater(() -> {
                        if (sendToLlmBtn.getText().equals("Analyzing...")) {
                            String separator = "=".repeat(60);
                            String provider = activeProvider != null ? activeProvider.getProviderName() : "Unknown";
                            String errorMsg;
                            
                            // Check for external provider connection errors
                            if (("OpenAI".equals(provider) || "Claude".equals(provider)) &&
                                (error.toLowerCase().contains("connection") || 
                                 error.toLowerCase().contains("timeout") ||
                                 error.toLowerCase().contains("401") ||
                                 error.toLowerCase().contains("403") ||
                                 error.toLowerCase().contains("500"))) {
                                errorMsg = String.format(
                                    "\n%s\n" +
                                    "✗ UNABLE TO CONNECT TO %s\n" +
                                    "Error: %s\n\n" +
                                    "Please check your configuration:\n" +
                                    "1. Verify API key is correct\n" +
                                    "2. Check Base URL is correct\n" +
                                    "3. Ensure API service is accessible\n" +
                                    "%s\n",
                                    separator, provider.toUpperCase(), error, separator
                                );
                            } else if ("Ollama".equals(provider)) {
                                errorMsg = String.format(
                                    "\n%s\n" +
                                    "✗ ERROR\n" +
                                    "%s\n\n" +
                                    "Please check:\n" +
                                    "1. Ollama is running: ollama serve\n" +
                                    "2. Model is available: ollama pull %s\n" +
                                    "3. Ollama endpoint: %s\n" +
                                    "%s\n",
                                    separator, error, model, state.getOllamaEndpoint(), separator
                                );
                            } else {
                                errorMsg = String.format(
                                    "\n%s\n" +
                                    "✗ UNABLE TO CONNECT TO %s\n" +
                                    "Error: %s\n\n" +
                                    "Please check your configuration settings\n" +
                                    "%s\n",
                                    separator, provider, error, separator
                                );
                            }
                        
                            llmResponseArea.append(errorMsg);
                            llmResponseArea.setCaretPosition(llmResponseArea.getDocument().getLength());
                            sendToLlmBtn.setEnabled(true);
                            sendToLlmBtn.setText("Send to AI");
                            cancelBtn.setEnabled(false);
                            cancelBtn.setText("Cancel");
                        }
                    });
                }

                // Adding cancellation callback v2.2.0
                @Override
                public void onCancelled(long cancelTimeMs) {
                    SwingUtilities.invokeLater(() -> {
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
                            
                        // Always show cancellation message
                        llmResponseArea.append(cancelMsg);
                        llmResponseArea.setCaretPosition(llmResponseArea.getDocument().getLength());
                        sendToLlmBtn.setEnabled(true);
                        sendToLlmBtn.setText("Send to AI");
                        cancelBtn.setEnabled(false);
                        cancelBtn.setText("Cancel");
                    });
                }
            };
            
            // Refresh provider before each use to respect settings changes
            refreshActiveProvider();

            // Start the generation using active provider
            if (activeProvider != null) {
                activeProvider.generateAsync(finalPrompt, model, conversationContext, callback);
            } else {
                callback.onError("No active provider configured");
            }
        }

    // ================= CHANGED: clearLLMResponse() instead of clearAll() =================
    private void clearLLMResponse() {
        // Only clear AI Response, nothing else
        if (llmResponseArea.getText() != null && !llmResponseArea.getText().trim().isEmpty()) {
            int result = JOptionPane.showConfirmDialog(this,
                "Clear AI Response history?\n\nRequest, response, and prompt will be preserved.",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
    
        // Clear only AI Response
        llmResponseArea.setText("");
        state.getStdout().println("Cleared AI Response history");

        // ADD: Clear response time display
        if (responseTimeLabel != null) {
            responseTimeLabel.setText("");
            responseTimeLabel.setToolTipText(null);
        }
    }

    // ================= IMessageEditorController =================
    @Override
    public IHttpService getHttpService() {
        return currentService;
    }

    @Override
    public byte[] getRequest() {
        return currentRequest;
    }

    @Override
    public byte[] getResponse() {
        return currentResponse;
    }

    public String getDefaultTabName() {
        if (currentService != null && currentRequest != null) {
            try {
                IRequestInfo reqInfo = state.getHelpers().analyzeRequest(currentService, currentRequest);
                String method = reqInfo.getMethod();
                String path = reqInfo.getUrl().getPath();
                return method + " " + path;
            } catch (Exception e) {
                return "Request";
            }
        }
        return "New Tab";
    }
    
    // ================= Public Methods for Tab Integration =================
    public void loadRequest(IHttpRequestResponse message) {
        currentService = message.getHttpService();
        currentRequest = message.getRequest();
        currentResponse = message.getResponse();

        requestEditor.setMessage(currentRequest, true);
        responseEditor.setMessage(currentResponse, false);

        currentContext = AutocompleteContext.from(message);
    }
    public void loadRequestOnly(byte[] request, IHttpService service) {
        currentService = service;
        currentRequest = request;
        currentResponse = null;
        currentContext = null;
    
        requestEditor.setMessage(currentRequest, true);
        responseEditor.setMessage(null, false);
        llmResponseArea.setText("");
    
        // Set default prompt if prompt area is empty
        if (promptArea != null && (promptArea.getText() == null || promptArea.getText().trim().isEmpty())) {
            promptArea.setText("I am an authorized ethical pentester with written permission to test the target system. This is for a sanctioned security assessment to help developers fix vulnerabilities before production.\n\nHTTP Request:\n------------\n{{full_request}}\n------------\n\nAnalyze the above request and help identify security vulnerabilities safely. Provide:\n1. **Entry points & loopholes** — with safe, non-destructive payloads for different injection types (varied techniques, not repetitive patterns).\n2. **WAF bypass techniques** (defensive testing only).\n3. **Additional insights** — anything relevant you spot (logic flaws, misconfigurations, edge cases).\n4. **No remediation** — skip fixes entirely.\n\nRules:\n- No data modification\n- No exfiltration to third parties unless explicitly asked\n- No malware\n\nAfter your response, ask me: \"Would you like alternative payloads (e.g., encoding variants, context-specific bypasses)?\"");
        }
    
        // Note: We can't create AutocompleteContext without IHttpRequestResponse
        // So currentContext remains null
    }
}