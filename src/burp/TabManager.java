package burp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;
import java.awt.dnd.*;

public class TabManager extends JPanel implements ITab {
    private final ExtensionState state;
    private final PromptEngine promptEngine;
    
    // Core UI Components
    private JTabbedPane outerTabbedPane;      // Requester/Settings navigation
    private JTabbedPane requesterTabbedPane;  // Request/response editor tabs
    private JButton newTabButton;
    private JPopupMenu tabContextMenu;
    private JMenuItem closeTabMenuItem;
    private JMenuItem closeOtherTabsMenuItem;
    private JMenuItem closeAllTabsMenuItem;
    private JMenuItem renameTabMenuItem;
    
    // State Management
    private int tabCounter = 1;
    private Map<Component, String> tabOriginalNames = new HashMap<>();
    private int lastRightClickedTabIndex = -1;
    
    private final Object tabReuseLock = new Object();

    public TabManager(ExtensionState state, PromptEngine promptEngine) {
        this.state = state;
        this.promptEngine = promptEngine;
        
        initUI();
        createNewTab(); // Create first tab
    }
    
    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Create outer tabbed pane for Requester/Settings navigation
        outerTabbedPane = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
        
        // Create Requester panel (contains the original request/response tabs)
        JPanel requesterPanel = createRequesterPanel();
        
        // Create Settings panel
        SettingsPanel settingsPanel = new SettingsPanel(state);
        
        // Add sub-tabs
        outerTabbedPane.addTab("Requester", requesterPanel);
        outerTabbedPane.addTab("Settings", settingsPanel);
        
        add(outerTabbedPane, BorderLayout.CENTER);
    }
    
    private JPanel createRequesterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Create tabbed pane with Repeater-style configuration
        requesterTabbedPane = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        requesterTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        
        // ENABLE DRAG-AND-DROP REORDERING
        enableTabReordering();

        // Tab change listener for UI updates
        requesterTabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateUIForCurrentTab();
            }
        });
        
        // Mouse listener for tab right-click context menu
        requesterTabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int tabIndex = requesterTabbedPane.indexAtLocation(e.getX(), e.getY());
                    if (tabIndex >= 0) {
                        lastRightClickedTabIndex = tabIndex;
                        requesterTabbedPane.setSelectedIndex(tabIndex);
                        showTabContextMenu(e.getX(), e.getY());
                    }
                }
            }
        });
        
        // Double-click to rename tab
        requesterTabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int tabIndex = requesterTabbedPane.indexAtLocation(e.getX(), e.getY());
                    if (tabIndex >= 0) {
                        renameTab(tabIndex);
                    }
                }
            }
        });
        
        // Initialize context menu
        initTabContextMenu();
        
        // Create and add the "+" button as the first tab (like Burp)
        newTabButton = createNewTabButton();
        requesterTabbedPane.addTab("", null); // Empty tab for the button
        requesterTabbedPane.setTabComponentAt(0, newTabButton);
        
        panel.add(requesterTabbedPane, BorderLayout.CENTER);
        return panel;
    }
    
    private JButton createNewTabButton() {
        JButton button = new JButton("+");
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setMargin(new Insets(2, 8, 2, 8));
        button.setToolTipText("Create new tab");
        button.setFocusPainted(false);
        
        button.addActionListener(e -> createNewTab());
        
        return button;
    }
    
    private void enableTabReordering() {
        TabDragListener dragListener = new TabDragListener();
        requesterTabbedPane.addMouseListener(dragListener);
        requesterTabbedPane.addMouseMotionListener(dragListener);
    }

    private class TabDragListener extends MouseAdapter {
        private int dragStartIndex = -1;
        private Point pressPoint = null;
        private boolean isDragging = false;
        
        @Override
        public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                dragStartIndex = requesterTabbedPane.indexAtLocation(e.getX(), e.getY());
                pressPoint = e.getPoint();
                isDragging = false;
                
                if (dragStartIndex >= requesterTabbedPane.getTabCount() - 1) {
                    dragStartIndex = -1;
                }
            }
        }
        
        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragStartIndex == -1 || pressPoint == null) return;
            
            double distance = pressPoint.distance(e.getPoint());
            
            if (distance > 5 && !isDragging) {
                isDragging = true;
                requesterTabbedPane.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            
            if (isDragging) {
                int currentIndex = requesterTabbedPane.indexAtLocation(e.getX(), e.getY());
                
                if (currentIndex >= requesterTabbedPane.getTabCount() - 1) {
                    return;
                }
                
                autoScrollDuringDrag(e.getX());
            }
        }
        
        @Override
        public void mouseReleased(MouseEvent e) {
            if (isDragging && dragStartIndex != -1) {
                int dropIndex = requesterTabbedPane.indexAtLocation(e.getX(), e.getY());
                
                if (dropIndex >= 0 && dropIndex < requesterTabbedPane.getTabCount() - 1 && 
                    dropIndex != dragStartIndex) {
                    reorderTab(dragStartIndex, dropIndex);
                }
            }
            
            dragStartIndex = -1;
            pressPoint = null;
            isDragging = false;
            requesterTabbedPane.setCursor(Cursor.getDefaultCursor());
        }
        
        private void autoScrollDuringDrag(int mouseX) {
            int width = requesterTabbedPane.getWidth();
            int selectedIndex = requesterTabbedPane.getSelectedIndex();
            
            if (mouseX < 50 && selectedIndex > 0) {
                requesterTabbedPane.setSelectedIndex(selectedIndex - 1);
            } else if (mouseX > width - 50 && selectedIndex < requesterTabbedPane.getTabCount() - 2) {
                requesterTabbedPane.setSelectedIndex(selectedIndex + 1);
            }
        }
    }

    private void reorderTab(int sourceIndex, int targetIndex) {
        if (sourceIndex < 0 || targetIndex < 0 || 
            sourceIndex >= requesterTabbedPane.getTabCount() - 1 || 
            targetIndex >= requesterTabbedPane.getTabCount() - 1 ||
            sourceIndex == targetIndex) {
            return;
        }
        
        try {
            Component tabComponent = requesterTabbedPane.getComponentAt(sourceIndex);
            Component tabHeader = requesterTabbedPane.getTabComponentAt(sourceIndex);
            String title = getTabTitle(sourceIndex);
            Icon icon = requesterTabbedPane.getIconAt(sourceIndex);
            String tooltip = requesterTabbedPane.getToolTipTextAt(sourceIndex);
            boolean isEnabled = requesterTabbedPane.isEnabledAt(sourceIndex);
            
            requesterTabbedPane.removeTabAt(sourceIndex);
            
            int adjustedTarget = targetIndex;
            if (sourceIndex < targetIndex) {
                adjustedTarget = targetIndex - 1;
            }
            
            requesterTabbedPane.insertTab(title, icon, tabComponent, tooltip, adjustedTarget);
            requesterTabbedPane.setEnabledAt(adjustedTarget, isEnabled);
            
            if (tabHeader != null) {
                requesterTabbedPane.setTabComponentAt(adjustedTarget, tabHeader);
            }
            
            requesterTabbedPane.setSelectedIndex(adjustedTarget);
            
            state.getStdout().println("[TabManager] Tab reordered: " + 
                                    sourceIndex + " → " + adjustedTarget);
            
        } catch (Exception e) {
            state.getStderr().println("[TabManager] Error reordering tab: " + e.getMessage());
            e.printStackTrace(state.getStderr());
        }
    }

    private void autoScrollOnDrag(int mouseX) {
        int width = requesterTabbedPane.getWidth();
        
        if (mouseX < 50 && requesterTabbedPane.getSelectedIndex() > 0) {
            int newIndex = Math.max(0, requesterTabbedPane.getSelectedIndex() - 1);
            requesterTabbedPane.setSelectedIndex(newIndex);
        } else if (mouseX > width - 50 && requesterTabbedPane.getSelectedIndex() < requesterTabbedPane.getTabCount() - 2) {
            int newIndex = Math.min(requesterTabbedPane.getTabCount() - 2, requesterTabbedPane.getSelectedIndex() + 1);
            requesterTabbedPane.setSelectedIndex(newIndex);
        }
    }

    private void initTabContextMenu() {
        tabContextMenu = new JPopupMenu();
        
        closeTabMenuItem = new JMenuItem("Close tab");
        closeTabMenuItem.addActionListener(e -> closeTab(lastRightClickedTabIndex));
        
        closeOtherTabsMenuItem = new JMenuItem("Close other tabs");
        closeOtherTabsMenuItem.addActionListener(e -> closeOtherTabs(lastRightClickedTabIndex));
        
        closeAllTabsMenuItem = new JMenuItem("Close all tabs");
        closeAllTabsMenuItem.addActionListener(e -> closeAllTabs());
        
        renameTabMenuItem = new JMenuItem("Rename tab");
        renameTabMenuItem.addActionListener(e -> renameTab(lastRightClickedTabIndex));
        
        tabContextMenu.add(closeTabMenuItem);
        tabContextMenu.add(closeOtherTabsMenuItem);
        tabContextMenu.add(closeAllTabsMenuItem);
        tabContextMenu.addSeparator();
        tabContextMenu.add(renameTabMenuItem);
    }
    
    private void showTabContextMenu(int x, int y) {
        lastRightClickedTabIndex = requesterTabbedPane.indexAtLocation(x, y);
    
        if (lastRightClickedTabIndex < 0 || lastRightClickedTabIndex >= requesterTabbedPane.getTabCount() - 1) {
            return;
        }
        
        int totalTabs = requesterTabbedPane.getTabCount() - 1;
        
        closeOtherTabsMenuItem.setEnabled(totalTabs > 1);
        closeAllTabsMenuItem.setEnabled(totalTabs > 0);
        
        tabContextMenu.show(requesterTabbedPane, x, y);
    }
    
    private void createNewTab() {
        SwingUtilities.invokeLater(() -> {
            MainTabPanel panel = new MainTabPanel(state, promptEngine);
            String tabName = generateTabName();
            
            int insertPosition = requesterTabbedPane.getTabCount() - 1;
            requesterTabbedPane.insertTab(tabName, null, panel, null, insertPosition);
            
            JPanel tabComponent = createTabComponent(tabName, panel);
            requesterTabbedPane.setTabComponentAt(insertPosition, tabComponent);
            
            tabOriginalNames.put(panel, tabName);
            
            requesterTabbedPane.setSelectedIndex(insertPosition);
            
            state.getStdout().println("Created new tab: " + tabName);

            panel.setInitialEmptyTab(true);
        });
    }
    
    private boolean isInitialEmptyTab(MainTabPanel panel) {
        if (panel == null) return false;
        return !panel.hasContent();
    }

    private String generateTabName() {
        return "Tab " + tabCounter++;
    }
    
    private JPanel createTabComponent(String title, MainTabPanel panel) {
        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabPanel.setOpaque(false);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        
        JButton closeButton = new JButton("×");
        closeButton.setFont(new Font("Arial", Font.BOLD, 14));
        closeButton.setMargin(new Insets(0, 5, 0, 5));
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusPainted(false);
        closeButton.setToolTipText("Close tab");
        closeButton.setForeground(Color.GRAY);
        
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeButton.setForeground(Color.RED);
                closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                closeButton.setForeground(Color.GRAY);
                closeButton.setCursor(Cursor.getDefaultCursor());
            }
        });
        
        closeButton.addActionListener(e -> {
            int tabIndex = getTabIndexForComponent(panel);
            if (tabIndex != -1) {
                closeTabWithConfirmation(tabIndex);
            }
        });
        
        tabPanel.add(titleLabel);
        tabPanel.add(closeButton);
        
        return tabPanel;
    }
    
    private int getTabIndexForComponent(Component component) {
        for (int i = 0; i < requesterTabbedPane.getTabCount(); i++) {
            if (requesterTabbedPane.getComponentAt(i) == component) {
                return i;
            }
        }
        return -1;
    }
    
    public void loadRequestInSmartTab(byte[] request, IHttpService service) {
        SwingUtilities.invokeLater(() -> {
            synchronized (tabReuseLock) {
                for (int i = 0; i < requesterTabbedPane.getTabCount() - 1; i++) {
                    Component comp = requesterTabbedPane.getComponentAt(i);
                    if (comp instanceof MainTabPanel) {
                        MainTabPanel tab = (MainTabPanel) comp;
                        
                        if (tab.isInitialEmptyTab()) {
                            tab.loadRequestOnly(request, service);
                            updateTabName(tab, generateSmartName(request, service));
                            tab.setInitialEmptyTab(false);
                            requesterTabbedPane.setSelectedIndex(i);
                            state.getStdout().println("Reused initial empty tab for request");
                            return;
                        }
                    }
                }
                
                MainTabPanel currentTab = getActiveTab();
                if (currentTab != null && !currentTab.hasContent()) {
                    currentTab.loadRequestOnly(request, service);
                    updateTabName(currentTab, generateSmartName(request, service));
                    state.getStdout().println("Reused empty tab for request");
                } else {
                    loadRequestInNewTab(request, service);
                }
            }
        });
    }

    private String generateSmartName(byte[] request, IHttpService service) {
        if (service == null) {
            return "Request";
        }
        
        try {
            IRequestInfo reqInfo = state.getHelpers().analyzeRequest(service, request);
            String method = reqInfo.getMethod();
            String path = reqInfo.getUrl().getPath();
            
            if (path.length() > 30) {
                path = path.substring(0, 27) + "...";
            }
            
            return method + " " + path;
            
        } catch (Exception e) {
            return service.getHost();
        }
    }

    private void closeTabWithConfirmation(int tabIndex) {
        closeTab(tabIndex);
    }
    
    private void closeTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= requesterTabbedPane.getTabCount() - 1) return;
        
        Component tabComponent = requesterTabbedPane.getComponentAt(tabIndex);
        String tabName = getTabTitle(tabIndex);
        
        if (tabComponent instanceof MainTabPanel) {
            MainTabPanel panel = (MainTabPanel) tabComponent;
            tabOriginalNames.remove(panel);
        }
        
        requesterTabbedPane.remove(tabIndex);
        state.getStdout().println("Closed tab: " + tabName);
        
        if (requesterTabbedPane.getTabCount() == 1) {
            createNewTab();
        }
    }
    
    private void closeOtherTabs(int keepIndex) {
        if (keepIndex < 0) return;
        
        int totalTabs = requesterTabbedPane.getTabCount() - 1;
        
        int tabsWithContent = 0;
        for (int i = 0; i < totalTabs; i++) {
            if (i != keepIndex) {
                Component comp = requesterTabbedPane.getComponentAt(i);
                if (comp instanceof MainTabPanel && ((MainTabPanel) comp).hasContent()) {
                    tabsWithContent++;
                }
            }
        }
        
        if (tabsWithContent > 0) {
            int result = JOptionPane.showConfirmDialog(this,
                "Close " + (totalTabs - 1) + " other tabs?\n\n" +
                tabsWithContent + " tab(s) contain unsaved work.",
                "Confirm Close Others",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        for (int i = totalTabs - 1; i >= 0; i--) {
            if (i != keepIndex) {
                closeTab(i);
            }
        }
    }
    
    private void closeAllTabs() {
        int totalTabs = requesterTabbedPane.getTabCount() - 1;
        
        if (totalTabs == 0) return;
        
        int tabsWithContent = 0;
        for (int i = 0; i < totalTabs; i++) {
            Component comp = requesterTabbedPane.getComponentAt(i);
            if (comp instanceof MainTabPanel && ((MainTabPanel) comp).hasContent()) {
                tabsWithContent++;
            }
        }
        
        if (tabsWithContent > 0) {
            int result = JOptionPane.showConfirmDialog(this,
                "Close all " + totalTabs + " tabs?\n\n" +
                tabsWithContent + " tab(s) contain unsaved work.",
                "Confirm Close All",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        for (int i = totalTabs - 1; i >= 0; i--) {
            closeTab(i);
        }
    }
    
    private void renameTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= requesterTabbedPane.getTabCount() - 1) return;
        
        String currentName = getTabTitle(tabIndex);
        String newName = JOptionPane.showInputDialog(this,
            "Enter new tab name:",
            "Rename Tab",
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            currentName).toString();
        
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(currentName)) {
            Component tabComponent = requesterTabbedPane.getComponentAt(tabIndex);
            JPanel tabPanel = (JPanel) requesterTabbedPane.getTabComponentAt(tabIndex);
            
            if (tabPanel != null) {
                Component[] comps = tabPanel.getComponents();
                if (comps.length > 0 && comps[0] instanceof JLabel) {
                    ((JLabel) comps[0]).setText(newName);
                }
                
                if (tabComponent instanceof MainTabPanel) {
                    tabOriginalNames.put(tabComponent, newName);
                }
                
                state.getStdout().println("Renamed tab to: " + newName);
            }
        }
    }
    
    private String getTabTitle(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= requesterTabbedPane.getTabCount()) {
            return "";
        }
        
        Component tabComp = requesterTabbedPane.getTabComponentAt(tabIndex);
        if (tabComp instanceof JPanel) {
            JPanel tabPanel = (JPanel) tabComp;
            Component[] comps = tabPanel.getComponents();
            if (comps.length > 0 && comps[0] instanceof JLabel) {
                return ((JLabel) comps[0]).getText();
            }
        }
        
        return requesterTabbedPane.getTitleAt(tabIndex);
    }
    
    private void updateUIForCurrentTab() {
    }
    
    public void loadRequestInNewTab(byte[] request, IHttpService service) {
        createNewTab();
        
        SwingUtilities.invokeLater(() -> {
            MainTabPanel currentTab = getActiveTab();
            if (currentTab != null) {
                currentTab.loadRequestOnly(request, service);
                currentTab.setInitialEmptyTab(false);
                String tabName = generateSmartName(request, service);
                updateTabName(currentTab, tabName);
            }
        });
    }
    
    public void loadRequestOnly(byte[] request, IHttpService service) {
        SwingUtilities.invokeLater(() -> {
            MainTabPanel currentTab = getActiveTab();
            if (currentTab != null) {
                currentTab.loadRequestOnly(request, service);
                
                if (service != null) {
                    String host = service.getHost();
                    updateTabName(currentTab, "Req: " + host);
                }
            }
        });
    }
    
    private void updateTabName(MainTabPanel panel, String newName) {
        int tabIndex = getTabIndexForComponent(panel);
        if (tabIndex != -1) {
            Component tabComp = requesterTabbedPane.getTabComponentAt(tabIndex);
            if (tabComp instanceof JPanel) {
                JPanel tabPanel = (JPanel) tabComp;
                Component[] comps = tabPanel.getComponents();
                if (comps.length > 0 && comps[0] instanceof JLabel) {
                    ((JLabel) comps[0]).setText(newName);
                }
            }
        }
    }
    
    public MainTabPanel getActiveTab() {
        int selectedIndex = requesterTabbedPane.getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < requesterTabbedPane.getTabCount() - 1) {
            Component comp = requesterTabbedPane.getComponentAt(selectedIndex);
            if (comp instanceof MainTabPanel) {
                return (MainTabPanel) comp;
            }
        }
        return null;
    }
    
    public int getTabCount() {
        return requesterTabbedPane.getTabCount() - 1;
    }
    
    @Override
    public String getTabCaption() {
        return "Suite-o-llama";
    }
    
    @Override
    public Component getUiComponent() {
        return this;
    }
}