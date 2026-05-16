#!/bin/bash
# Clean Build Script for Suite-o-llama - UPDATED
# Updated (now 23 files total)

set -e

echo "=========================================="
echo "Suite-o-llama Build Script - UPDATED"
echo "=========================================="
echo ""

# Configuration
BASE_DIR="/Users/VermaOps/Downloads"
SRC_DIR="$BASE_DIR/suite/src/burp"
BUILD_DIR="$BASE_DIR/build"
LIB_DIR="$BASE_DIR/lib"
OUTPUT_JAR="$BASE_DIR/Suite-o-llama.jar"
JSON_VERSION="20240303"
JSON_JAR="$LIB_DIR/json-$JSON_VERSION.jar"

# Burp JAR location (macOS default)
BURP_JAR="/Applications/Burp Suite.app/Contents/Resources/app/burpsuite.jar"

# Check if Burp JAR exists
if [ ! -f "$BURP_JAR" ]; then
    echo "ERROR: Burp Suite JAR not found!"
    echo "Expected at: $BURP_JAR"
    echo ""
    echo "Please set BURP_JAR environment variable:"
    echo "  export BURP_JAR=/path/to/your/burpsuite.jar"
    echo ""
    read -p "Enter Burp JAR path: " user_burp_jar
    if [ -f "$user_burp_jar" ]; then
        BURP_JAR="$user_burp_jar"
    else
        echo "ERROR: Invalid path: $user_burp_jar"
        exit 1
    fi
fi

echo "[1/8] Checking prerequisites..."
echo "  Java version:"
java -version 2>&1 | head -1
echo "  Burp JAR: $BURP_JAR"
echo "  Source directory: $SRC_DIR"

# Check source files
echo ""
echo "[2/8] Checking Java files..."
if [ ! -d "$SRC_DIR" ]; then
    echo "  ✗ Source directory not found: $SRC_DIR"
    exit 1
fi

# Count Java files
JAVA_FILES_COUNT=$(find "$SRC_DIR" -name "*.java" | wc -l)
if [ "$JAVA_FILES_COUNT" -eq 0 ]; then
    echo "  ✗ No Java files found in $SRC_DIR"
    echo "  Found files:"
    ls -la "$SRC_DIR" | head -10
    exit 1
fi

echo "  ✓ Found $JAVA_FILES_COUNT Java files"
echo "  Files found:"
ls "$SRC_DIR"/*.java | xargs basename -a | sort | column -c 80

# All 23 required files
echo ""
echo "[3/8] Checking all 23 required files..."

CRITICAL_FILES=(
    # Core files
    "BurpExtender.java"
    "MainTabPanel.java"
    "TabManager.java"
    "ExtensionState.java"
    "OllamaClient.java"
    "PromptEngine.java"
    
    # UI Components
    "SettingsPanel.java"
    "RepeaterAITab.java"
    "RepeaterAIResponseTab.java"
    "PromptManagerDialog.java"
    
    # Factories
    "ContextMenuFactory.java"
    "MessageEditorTabFactory.java"
    "ProviderFactory.java"
    
    # Providers
    "LLMProvider.java"
    "OpenAIProvider.java"
    "ClaudeProvider.java"
    "ProviderType.java"
    
    # Support/Utility
    "AutocompleteContext.java"
    "AutocompleteEngine.java"
    "ContextTrimmer.java"
    "RequestContext.java"
    "UpdateChecker.java"

    # Session handler
    "ConversationSession.java"
)

echo "  Checking ${#CRITICAL_FILES[@]} required files:"
missing_files=()
found_count=0
for file in "${CRITICAL_FILES[@]}"; do
    if [ ! -f "$SRC_DIR/$file" ]; then
        missing_files+=("$file")
        echo "    ✗ Missing: $file"
    else
        echo "    ✓ Found: $file"
        ((found_count++))
    fi
done

echo ""
echo "  Found $found_count of ${#CRITICAL_FILES[@]} required files"

if [ ${#missing_files[@]} -gt 0 ]; then
    echo ""
    echo "  ⚠️ Missing files:"
    printf "    %s\n" "${missing_files[@]}"
    
    # Special handling for renamed files
    if [[ " ${missing_files[*]} " == *"MainTabPanel.java"* ]] && [ -f "$SRC_DIR/MainTab.java" ]; then
        echo ""
        echo "  === RENAMING DETECTED ==="
        echo "  MainTab.java exists but needs to be renamed to MainTabPanel.java"
        read -p "  Auto-rename MainTab.java to MainTabPanel.java? (y/n): " rename_choice
        if [ "$rename_choice" = "y" ] || [ "$rename_choice" = "Y" ]; then
            mv "$SRC_DIR/MainTab.java" "$SRC_DIR/MainTabPanel.java"
            echo "  ✓ Renamed MainTab.java to MainTabPanel.java"
            
            # Update class references in the file
            sed -i '' 's/public class MainTab/public class MainTabPanel/g' "$SRC_DIR/MainTabPanel.java"
            sed -i '' 's/class MainTab/class MainTabPanel/g' "$SRC_DIR/MainTabPanel.java"
            
            # Update references in BurpExtender.java
            if [ -f "$SRC_DIR/BurpExtender.java" ]; then
                sed -i '' 's/new MainTab(/new MainTabPanel(/g' "$SRC_DIR/BurpExtender.java"
                echo "  ✓ Updated references in BurpExtender.java"
            fi
            
            # Update in ContextMenuFactory.java
            if [ -f "$SRC_DIR/ContextMenuFactory.java" ]; then
                sed -i '' 's/MainTab mainTab/MainTabPanel mainTab/g' "$SRC_DIR/ContextMenuFactory.java"
                echo "  ✓ Updated references in ContextMenuFactory.java"
            fi
            
            # Remove from missing list
            missing_files=("${missing_files[@]/MainTabPanel.java}")
            ((found_count++))
            
            echo "  ✓ MainTabPanel.java now ready"
        fi
    fi
    
    if [ ${#missing_files[@]} -gt 0 ]; then
        echo ""
        echo "  ERROR: Missing ${#missing_files[@]} required files"
        echo "  Build cannot proceed without all files"
        exit 1
    else
        echo "  ✓ All required files accounted for"
    fi
else
    echo "  ✓ All 23 required files found"
fi

# Create directories
echo ""
echo "[4/8] Creating build directories..."
mkdir -p "$BUILD_DIR"
mkdir -p "$LIB_DIR"

# Download JSON library if not exists
echo ""
echo "[5/8] Downloading JSON library..."
if [ ! -f "$JSON_JAR" ]; then
    JSON_URL="https://repo1.maven.org/maven2/org/json/json/$JSON_VERSION/json-$JSON_VERSION.jar"
    echo "  Downloading from: $JSON_URL"
    
    if command -v curl &> /dev/null; then
        curl -L -o "$JSON_JAR" "$JSON_URL"
    elif command -v wget &> /dev/null; then
        wget -O "$JSON_JAR" "$JSON_URL"
    else
        echo "  ✗ Need curl or wget to download JSON library"
        exit 1
    fi
    
    if [ $? -eq 0 ]; then
        echo "  ✓ Downloaded: $JSON_JAR"
    else
        echo "  ✗ Download failed"
        exit 1
    fi
else
    echo "  ✓ JSON library already exists: $JSON_JAR"
fi

# Compile
echo ""
echo "[6/8] Compiling Java source files..."
cd "$BASE_DIR"

# Get all Java files
JAVA_FILES=$(find "$SRC_DIR" -name "*.java")

echo "  Compiling $JAVA_FILES_COUNT files..."

# First, compile without showing all warnings
javac -d "$BUILD_DIR" \
      -cp "$BURP_JAR:$JSON_JAR" \
      -Xlint:unchecked \
      $JAVA_FILES 2>&1 | tee "$BASE_DIR/compile.log"

COMPILE_STATUS=$?

if [ $COMPILE_STATUS -eq 0 ]; then
    echo "  ✓ Compilation successful"
    echo "  Compiled classes in: $BUILD_DIR/burp/"
else
    echo "  ✗ Compilation failed"
    echo ""
    echo "=== COMPILATION ERRORS ==="
    grep -A 2 -B 2 "error:" "$BASE_DIR/compile.log" | head -50
    echo ""
    echo "See full log: $BASE_DIR/compile.log"
    
    # Check for common errors
    if grep -q "cannot find symbol.*MainTab" "$BASE_DIR/compile.log"; then
        echo ""
        echo "=== RENAMING ISSUE DETECTED ==="
        echo "MainTab.java needs to be renamed to MainTabPanel.java"
        echo "Or update references in:"
        echo "  - BurpExtender.java"
        echo "  - ContextMenuFactory.java"
        echo "  - TabManager.java (if referencing)"
    fi
    
    exit 1
fi

# Check for warnings
WARNINGS=$(grep -c "warning:" "$BASE_DIR/compile.log" || true)
if [ "$WARNINGS" -gt 0 ]; then
    echo "  ⚠️ Found $WARNINGS warnings (see compile.log)"
fi

# Package
echo ""
echo "[7/8] Packaging JAR..."

# Create JAR with our classes
cd "$BUILD_DIR"
jar -cf "../Suite-o-llama.jar" burp/*.class
cd "$BASE_DIR"

echo "  ✓ Created base JAR"

# Add JSON library classes
echo "  Adding JSON library..."
TEMP_DIR="$BASE_DIR/temp_json"
mkdir -p "$TEMP_DIR"
cd "$TEMP_DIR"
jar -xf "$JSON_JAR"
cd "$BASE_DIR"

jar -uf "$OUTPUT_JAR" -C "$TEMP_DIR" org/

# Cleanup
rm -rf "$TEMP_DIR"

echo "  ✓ Added JSON library to JAR"

# Verify
echo ""
echo "[8/8] Verifying JAR..."
JAR_SIZE=$(du -h "$OUTPUT_JAR" | cut -f1)
CLASS_COUNT=$(jar -tf "$OUTPUT_JAR" | grep "\.class$" | wc -l)

echo "  JAR file: $OUTPUT_JAR"
echo "  Size: $JAR_SIZE"
echo "  Total classes: $CLASS_COUNT"
echo ""
echo "  Core classes:"
jar -tf "$OUTPUT_JAR" | grep -E "^(burp/TabManager|burp/UpdateChecker|burp/MainTabPanel)" | sed 's/^/    /' | sort
echo ""
echo "  All classes (23 files → ~$(($CLASS_COUNT)) classes):"
jar -tf "$OUTPUT_JAR" | grep "^burp/.*\.class$" | sed 's/^/    /' | sort

# Final check
if [ ! -f "$OUTPUT_JAR" ]; then
    echo "  ✗ JAR creation failed!"
    exit 1
fi

echo ""
echo "=========================================="
echo "BUILD SUCCESSFUL! 🎉"
echo "=========================================="
echo ""
echo "Output: $OUTPUT_JAR"
echo "Files compiled: $JAVA_FILES_COUNT"
echo "Classes in JAR: $CLASS_COUNT"
echo ""
echo "To install in Burp Suite:"
echo "  1. Open Burp Suite"
echo "  2. Go to Extender → Extensions"
echo "  3. Click 'Add'"
echo "  4. Select: $OUTPUT_JAR"
echo ""
echo "=========================================="
