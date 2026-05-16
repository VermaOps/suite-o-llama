[![Burp Suite Extension](https://img.shields.io/badge/Burp%20Suite-Extension-orange)](https://portswigger.net/burp)
[![Version](https://img.shields.io/badge/Version-2.2.1-blue)](https://github.com/BerserkiKun/suite-o-llama/releases)
[![Ollama](https://img.shields.io/badge/Ollama-Required-yellow)](https://ollama.com)

# Suite-o-llama: AI-Powered Burp Suite Extension for Penetration Testing

## 📋 Table of Contents
- [Overview](#overview)
- [Key Highlights](#key-highlights)
- [Deep Burp Suite Integration](#deep-burp-suite-integration)
  - [Multi-Tab Integration](#multi-tab-integration)
  - [Tab Management Features](#tab-management-features)
  - [True Conversational AI](#true-conversational-ai)
  - [Core Template Variables](#core-template-variables)
- [Model Configuration](#model-configuration)
  - [Default Models](#default-models)
  - [Configuration Options](#configuration-options)
  - [Model Compatibility](#model-compatibility)
- [Local LLM Architecture](#local-llm-architecture)
- [Installation Guide](#installation-guide)
  - [Prerequisites](#prerequisites)
  - [Method 1: Pre-compiled Installation](#method-1-pre-compiled-installation-recommended)
  - [Method 2: Custom Build Installation](#method-2-custom-build-installation)
- [Key Features](#key-features)
  - [Security & Privacy](#security--privacy)
  - [Productivity Tools](#productivity-tools)
  - [Performance](#performance)
  - [Advanced Features](#advanced-features)
- [Usage Workflow](#usage-workflow)
  - [Basic Analysis](#basic-analysis)
  - [Multi-tab Operations](#multi-tab-operations)
  - [Repeater Integration](#repeater-integration)
  - [Advanced Features](#advanced-features)
- [Screenshots](#screenshots)
- [Support Development](#support-development)
- [Report Issues](#report-issues)
- [Community & Feedback](#community--feedback)

## Overview

Suite-o-llama is a professional-grade Burp Suite extension that seamlessly integrates local Ollama LLM capabilities into your web security testing workflow. Designed specifically for penetration testers and bug bounty hunters, this tool transforms traditional security testing by adding AI-powered analysis and payload generation directly within Burp Suite's interface.

## Key Highlights

- **Multi-tab workspace** like modern IDEs with drag-and-drop reordering
- **Local LLM processing** via Ollama — no data leaves your machine
- **Deep Burp integration** across Proxy, Repeater, and context menus
- **Smart tab management** with automatic request-based naming
- **Enhanced cancellation** with real-time feedback
- **Response analysis** with dedicated template variables
- **Version update checking** via GitHub API
- **True multi-turn conversational AI** with persistent context across all AI tabs
- **Built-in server response time (ms)** tracking in the suite-o-llama main tab

## Deep Burp Suite Integration

The extension integrates comprehensively across Burp Suite's ecosystem:

### Multi-Tab Integration
Suite-o-llama v2.2.0 features a complete multi-tab interface that mimics Burp Suite's own workspace:

- **Main Tab**: Dedicated "Suite-o-llama" tab for comprehensive analysis and payload generation
- **Repeater Sub-tabs**: "Suite-o-llama AI" tabs appear automatically for both request and response analysis
- **Proxy Context Menu and Sub-tabs**: Right-click any request in Proxy history → "Send to Suite-o-llama". Also have a sub-tab of suite-o-llama AI in proxy-interception to generate payloads.
- **Multiple Tabs**: Create, reorder, and manage analysis sessions independently
- **Smart Tab Naming**: Automatic naming based on HTTP method and path
- **Drag-and-Drop**: Reorder tabs freely for optimal workflow
- **Settings Tab**: Dedicated configuration panel for Ollama connection and model settings
- **Prompts Ready**: Core variables can be used in manual prompts across all Burp modules

### Tab Management Features
- **Close Button**: Each tab has its own close button (×)
- **Context Menu**: Right-click tabs for close/rename operations
- **Smart Reuse**: Initial empty tab gets reused automatically
- **Batch Processing**: Send multiple requests from context menu
- **State Isolation**: Each tab maintains independent Ollama connections

### True Conversational AI
Suite-o-llama now supports real multi-turn conversations with Ollama, ensuring prompts and responses are part of a continuous session rather than isolated requests:

- **Persistent Context:** Follow-up prompts correctly reference prior user inputs and LLM responses with up to 30 queries reference
- **Session-Based Conversations:** AI interactions maintain conversational state across multiple actions
- **Cross-Tab Continuity:** Conversation state persists across Repeater AI, Response AI, and suite-o-llama main tab
- **Non-Destructive Sessions:** Conversation history and prompts remain visible even after session termination
- **Smart Session Timeout:** Inactive sessions automatically close after 10 minutes without clearing existing data
- **UI-State Awareness:** Ongoing conversations remain active across tab switches and request resends


### Core Template Variables
The system uses smart templating with these variables (v2.2.0 updated):

| Variable | Description | Example |
|----------|-------------|---------|
| `{{method}}` | HTTP method (GET, POST, etc.) | `POST` |
| `{{url}}` | Full request URL | `https://example.com/api/login` |
| `{{req_headers}}` | Request headers (redacted if configured) | `Content-Type: application/json` |
| `{{req_body}}` | Request body content | `{"user":"admin"}` |
| `{{full_request}}` | Complete HTTP request | Full request with headers/body |
| `{{res_headers}}` | Response headers (if available) | `HTTP/1.1 200 OK` |
| `{{res_body}}` | Response body (if available) | `{"status":"success"}` |
| `{{full_response}}` | Complete HTTP response (if available) | Full response with headers/body |

## Model Configuration

Suite-o-llama is pre-configured with two specialized Ollama models optimized for penetration testing:

### Default Models
- **Analysis Model**: `qwen2.5:7b-instruct` - For general security analysis
- **Payload Model**: `qwen2.5-coder:7b` - For payload generation
- **Custom Model Support**: While configured for these base models, advanced users can modify the source code to use higher-capacity models or different architectures. Customization requires manual compilation using the provided build script `suite.sh`.

### Configuration Options
- **Temperature**: 0.7 (configurable 0.0-2.0)
- **Max Tokens**: 4096 (configurable 128-16384)
- **Context Size**: 16,384 characters (configurable 1024-65536)
- **Endpoint**: `http://127.0.0.1:11434` (customizable)

### Model Compatibility
- Supports any Ollama-compatible model
- Automatic model availability checking
- One-click model refresh from Settings tab
- Model switching between analysis/payload modes

## Local LLM Architecture
```text
┌─────────────────────────────────────────────────────────────┐
│                    Burp Suite Professional                  │
└───────────────────────────────┬─────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────┐
│                Suite-o-llama Extension v2.2.1               │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    MainTabPanel(s)                    │  │
│  │                                                       │  │
│  │  Tab 1            Tab 2             ...               │  │
│  │  ├─ OllamaClient  ├─ OllamaClient                     │  │
│  │  ├─ Autocomplete  ├─ Autocomplete                     │  │
│  │  └─ Msg Editors   └─ Msg Editors                      │  │
│  └───────────────────────────────────────────────────────┘  │
└───────────────────────────────┬─────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────┐
│               Ollama HTTP API (localhost:11434)             │
└───────────────────────────────┬─────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────┐
│                 Local Large Language Model                  │
│               (Qwen2.5, LLaMA, etc.)                        │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  ✓ All processing happens locally                     │  │
│  │  ✓ No internet connection required                    │  │
│  │  ✓ No API keys needed                                 │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## Installation Guide

### Prerequisites
- **Ollama Running**: Ensure Ollama is installed and running (`ollama serve`)
- **Java**: openjdk21
- **Models Available**: Pull required models:
  ```bash
  ollama pull qwen2.5:7b-instruct
  ollama pull qwen2.5-coder:7b
- **Burp Suite**: Professional or Community Edition as of Jan 2026 (at the time of creating repository) or later.

### Method 1: Pre-compiled Installation recommended

1. **Download**: Get stable release of `suite-o-llama.jar` from the [Releases page](https://github.com/BerserkiKun/suite-o-llama/releases)
2. **Install in Burp**:
   ```bash
   Burp Suite → Extender → Extensions
   Click "Add" → Select the JAR file
   Make sure JAVA is selected
3. **Load Dependencies**: Load any additional required JAR files if prompted
4. **Configure**: Go to "Suite-o-llama Settings" tab and verify connection. Make sure your ollama is installed and up.
   ```bash
   # Check ollama health at http://localhost:11434 or http://127.0.0.1:11434
   # if it is not running then try this to start ollama
   ollama serve

### Method 2: Custom Build Installation

For custom modifications:

1. **Clone Repository**: 
   ```bash
   git clone https://github.com/berserkikun/suite-o-llama.git
   cd suite-o-llama

2. **Modify Files**:
   - Edit `suite.sh` for your system paths
   - Modify Java files for custom models/features
   - Adjust `ExtensionState.java` for different defaults

3. **Build**:
   ```bash
   chmod +x suite.sh
   ./suite.sh
*Note: Build script `suite.sh` works on macOS and Linux only. But you might need to change paths in the script file.*

4. **Install**: Load the generated JAR from `dist/` directory into Burp

## Key Features

### Security & Privacy
- **Local Processing**: All AI analysis happens on your machine via Ollama
- **Header Redaction**: Automatic redaction of Authorization/Cookie headers before LLM processing (configurable)
- **No Data Exfiltration**: Complete privacy with local LLM setup
- **Sensitive Data Protection**: Configurable redaction of authentication tokens
- **Offline Capable**: Works entirely without internet connection

### Productivity Tools
- **Multi-tab Interface**: Work on multiple requests simultaneously
- **Saved Prompts**: Reusable prompt templates for common security tasks
- **Prompt Manager**: GUI for organizing and managing prompt templates
- **Batch Analysis**: Send multiple requests from context menu to separate tabs
- **Response Analysis**: Dedicated response analysis with `res_headers`/`res_body` variables

### Performance
- **Per-tab Ollama Instances**: Each tab gets independent connection
- **Thread Pooling**: 3-thread executor for concurrent operations
- **High Concurrency**: Each tab can handle up to 3 concurrent requests independently
- **Smart Caching**: Reduced duplicate LLM calls with intelligent caching
- **Context Trimming**: Automatic size management for large requests
- **Streaming Responses**: Real-time token streaming from Ollama
- **Enhanced Cancellation**: Immediate cancellation with timing metrics

### Advanced Features
- **Drag-and-Drop Tabs**: Reorder tabs freely for workflow optimization
- **Update Checking**: Automatic GitHub release monitoring
- **Cancellation Support**: Stop ongoing LLM requests with timing feedback
- **Request Editing**: Edit requests directly in AI workspace
- **Server Integration**: Send requests to target from within extension
- **Template Variables**: Comprehensive variable system for prompt engineering

## Usage Workflow

### Basic Analysis
1. **Send to Suite-o-llama**: Right-click any request in Proxy/Repeater/Intruder
2. **Multiple Requests**: Select multiple requests for batch processing
3. **Tab Interface**: Each request opens in a new tab (first reuses initial empty tab)
4. **Analyze**: Enter prompt or use default, click "Send to LLM"
5. **Review**: Results appear in LLM Response area with timing metrics

### Multi-tab Operations
- **New Tab**: Click "+" button or send new request
- **Reorder Tabs**: Drag tabs to rearrange
- **Close Tabs**: Click × button or right-click menu
- **Rename Tabs**: Double-click tab name or right-click → Rename
- **Smart Naming**: Tabs auto-name based on request (GET /api/login)

### Repeater Integration
1. **Open Request**: Any request in Repeater
2. **Switch Tab**: Go to "Suite-o-llama AI" tab
3. **Analyze Request/Response**: Separate tabs for request vs response analysis
4. **Include Context**: Checkbox to include request in response analysis

### Advanced Features
- **Clear History**: "Clear" button removes only LLM responses
- **Cancel Requests**: "Cancel" button stops ongoing LLM analysis
- **Template Variables**: Use `{{variable}}` syntax in prompts
- **Update Checks**: "New releases" button shows available updates

## Screenshots
| | | |
|:---:|:---:|:---:|
| <img width="1470" height="852" alt="img4 burp" src="https://github.com/user-attachments/assets/a81528e5-d050-47f4-8c3a-0ab28ad49d01" /> | <img width="1470" height="781" alt="img5 burp" src="https://github.com/user-attachments/assets/feb03089-c456-4955-9bfa-1f4d1c69b139" /> | <img width="1437" height="851" alt="img6 urp" src="https://github.com/user-attachments/assets/d46badd5-d402-410e-8f27-dd230c9b1979" /> |
| <img width="2940" height="1706" alt="img 7" src="https://github.com/user-attachments/assets/1c8e1cb0-c1a2-47c1-b0dc-a13a4c7ed1b6" /> | <img width="2940" height="1758" alt="img3" src="https://github.com/user-attachments/assets/3c81b90a-d142-4852-b3a6-725492624e1f" /> | |


## Support Development

If Suite-o-llama helps your security testing, consider supporting its development:

**Star the Repository**: Show your support by starring the project on GitHub!

**Support Links**:
- 💰 **PayPal**: [PayPal](https://www.paypal.com/ncp/payment/7Y3836GETVF94)

Your support helps maintain the project and add new features.

---

## Report Issues
Found a bug? Have a feature request?
- **Bug Reports**: Include Burp version, Ollama version, and reproduction steps
- **Feature Requests**: Describe use case and expected behavior
- **Security Issues**: Report privately on Discord DM - @BerserkiKun
 
## Community & Feedback

Community feedback is welcome. The local-first design ensures you can use Suite-o-llama confidently in sensitive environments while benefiting from AI-assisted security testing.

---

<div align="center">

**Built with ❤️ by [BerserkiKun](https://github.com/berserkikun)**

[![GitHub Stars](https://img.shields.io/github/stars/berserkikun/suite-o-llama?style=social)](https://github.com/berserkikun/suite-o-llama/stargazers)
[![GitHub Issues](https://img.shields.io/github/issues/berserkikun/suite-o-llama)](https://github.com/berserkikun/suite-o-llama/issues)
[![GitHub Forks](https://img.shields.io/github/forks/berserkikun/suite-o-llama?style=social)](https://github.com/berserkikun/suite-o-llama/network/members)

**⭐ Star this repo if you find it useful!**

</div>
