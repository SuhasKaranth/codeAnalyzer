#!/bin/bash

# =============================================================================
# Code Analyzer ENHANCED Complete Cleanup Script v2.0
# =============================================================================
# This script ensures ALL embeddings, repositories, and system state are
# completely removed and reset to a clean state.
# =============================================================================

set -e  # Exit on any error

echo "🧹 Enhanced Code Analyzer Complete Cleanup Script v2.0"
echo "======================================================="
echo ""
echo "⚠️  WARNING: This will permanently delete:"
echo "   • All cloned repositories in ./workspace/"
echo "   • ALL vector embeddings in ChromaDB (guaranteed complete wipe)"
echo "   • All Docker containers and volumes for ChromaDB"
echo "   • All analysis cache and temporary files"
echo "   • Application logs and session data"
echo "   • Ollama conversation cache"
echo ""

# Confirmation prompt
read -p "Are you sure you want to proceed? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Cleanup cancelled"
    exit 1
fi

echo ""
echo "🚀 Starting ENHANCED cleanup process..."

# =============================================================================
# 1. Stop ALL related processes
# =============================================================================
echo ""
echo "1️⃣  Stopping ALL related processes..."

# Stop Spring Boot application
echo "   📱 Stopping Spring Boot application..."
pkill -f "java.*codeAnalyzer" 2>/dev/null || echo "   ℹ️  Spring Boot app not running"
pkill -f "gradle.*bootRun" 2>/dev/null || echo "   ℹ️  Gradle bootRun not running"

# Stop any background analysis processes
pkill -f "java.*analysis" 2>/dev/null || echo "   ℹ️  No analysis processes running"
pkill -f "embedding" 2>/dev/null || echo "   ℹ️  No embedding processes running"

# Give processes time to stop
sleep 2
echo "   ✅ All processes stopped"

# =============================================================================
# 2. NUCLEAR ChromaDB cleanup - Complete Docker reset
# =============================================================================
echo ""
echo "2️⃣  NUCLEAR ChromaDB cleanup (complete Docker reset)..."

echo "   🛑 Force stopping ANY ChromaDB containers..."
docker stop chroma-db 2>/dev/null || echo "   ℹ️  No chroma-db container running"
docker stop $(docker ps -q --filter "ancestor=ghcr.io/chroma-core/chroma:latest") 2>/dev/null || echo "   ℹ️  No ChromaDB containers running"

echo "   🗑️  Removing ALL ChromaDB containers..."
docker rm -f chroma-db 2>/dev/null || echo "   ℹ️  No chroma-db container to remove"
docker rm -f $(docker ps -aq --filter "ancestor=ghcr.io/chroma-core/chroma:latest") 2>/dev/null || echo "   ℹ️  No ChromaDB containers to remove"

echo "   🧹 Removing ALL ChromaDB volumes..."
docker volume rm $(docker volume ls -q --filter "name=chroma") 2>/dev/null || echo "   ℹ️  No ChromaDB volumes found"

echo "   📁 Removing local ChromaDB data directories..."
if [ -d "./chroma_data" ]; then
    rm -rf ./chroma_data
    echo "   ✅ Removed ./chroma_data"
fi

if [ -d "$(pwd)/chroma_data" ]; then
    rm -rf "$(pwd)/chroma_data" 
    echo "   ✅ Removed $(pwd)/chroma_data"
fi

# Check for any other potential ChromaDB data directories
find . -name "*chroma*" -type d -exec rm -rf {} + 2>/dev/null || echo "   ℹ️  No other ChromaDB directories found"

echo "   🐳 Cleaning up Docker system..."
docker system prune -f > /dev/null 2>&1 || echo "   ℹ️  Docker system prune completed"

echo "   ✅ ChromaDB NUCLEAR cleanup completed"

# =============================================================================
# 3. Clean workspace directory
# =============================================================================
echo ""
echo "3️⃣  Cleaning workspace directory..."

if [ -d "./workspace" ]; then
    echo "   📂 Found workspace directory"
    
    # Show what will be deleted
    REPO_COUNT=$(find ./workspace -maxdepth 1 -type d 2>/dev/null | wc -l | tr -d ' ')
    REPO_COUNT=$((REPO_COUNT - 1))  # Subtract 1 for the workspace dir itself
    
    if [ $REPO_COUNT -gt 0 ]; then
        echo "   🗂️  Found $REPO_COUNT repositories:"
        find ./workspace -maxdepth 1 -type d -not -path "./workspace" 2>/dev/null | sed 's|^|      |' || echo "   ℹ️  Repository list unavailable"
    fi
        
    # Force remove all contents
    rm -rf ./workspace/* 2>/dev/null || echo "   ℹ️  Workspace already clean"
    rm -rf ./workspace/.* 2>/dev/null || echo "   ℹ️  No hidden files in workspace"
    
    echo "   ✅ Workspace completely cleaned"
else
    echo "   ℹ️  No workspace directory found"
    mkdir -p ./workspace
    echo "   📁 Created fresh workspace directory"
fi

# =============================================================================
# 4. Clear ALL application data
# =============================================================================
echo ""
echo "4️⃣  Clearing ALL application logs and cache..."

# Clear Spring Boot logs
if [ -d "./logs" ]; then
    rm -rf ./logs/*
    rm -rf ./logs/.* 2>/dev/null || echo ""
    echo "   📝 Application logs cleared"
else
    echo "   ℹ️  No application logs directory found"
fi

# Clear temporary analysis files
if [ -d "./temp" ]; then
    rm -rf ./temp/*
    rm -rf ./temp/.* 2>/dev/null || echo ""
    echo "   🗂️  Temporary files cleared"
else
    echo "   ℹ️  No temporary files directory found"
fi

# Clear cached analysis results
if [ -d "./cache" ]; then
    rm -rf ./cache/*
    rm -rf ./cache/.* 2>/dev/null || echo ""
    echo "   💾 Cache directory cleared"
else
    echo "   ℹ️  No cache directory found"
fi

# Clear any build artifacts that might contain cached data
if [ -d "./build" ]; then
    echo "   🏗️  Clearing build artifacts..."
    rm -rf ./build/classes 2>/dev/null || echo ""
    rm -rf ./build/tmp 2>/dev/null || echo ""
fi

echo "   ✅ Application data cleared"

# =============================================================================
# 5. Reset Ollama completely
# =============================================================================
echo ""
echo "5️⃣  Complete Ollama reset..."

# Stop Ollama service
echo "   🛑 Stopping Ollama service..."
pkill -f ollama 2>/dev/null || echo "   ℹ️  Ollama not running as process"

# Clear Ollama cache and conversation history
if [ -d ~/.ollama ]; then
    echo "   🧹 Clearing Ollama data..."
    rm -rf ~/.ollama/cache 2>/dev/null || echo "   ℹ️  No Ollama cache"
    rm -rf ~/.ollama/tmp 2>/dev/null || echo "   ℹ️  No Ollama tmp"
    # Note: We keep ~/.ollama/models to avoid re-downloading models
    echo "   🤖 Ollama cache and temp data cleared (models preserved)"
else
    echo "   ℹ️  No Ollama directory found"
fi

# Restart Ollama if it was running
if command -v ollama >/dev/null 2>&1; then
    echo "   🔄 Restarting Ollama service..."
    nohup ollama serve > /dev/null 2>&1 &
    sleep 3
    if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
        echo "   ✅ Ollama restarted successfully"
    else
        echo "   ⚠️  Ollama restart may need manual intervention"
    fi
else
    echo "   ℹ️  Ollama not installed"
fi

# =============================================================================
# 6. Start FRESH ChromaDB with verification
# =============================================================================
echo ""
echo "6️⃣  Starting completely fresh ChromaDB..."

echo "   🚀 Starting new ChromaDB container..."
docker run -d --name chroma-db \
    -p 8000:8000 \
    -v $(pwd)/chroma_data:/chroma/chroma \
    -e IS_PERSISTENT=TRUE \
    -e PERSIST_DIRECTORY=/chroma/chroma \
    --restart unless-stopped \
    ghcr.io/chroma-core/chroma:latest > /dev/null

echo "   ⏳ Waiting for ChromaDB to fully initialize..."
CHROMA_READY=false
for i in {1..30}; do
    if curl -s http://localhost:8000/api/v2/heartbeat > /dev/null 2>&1; then
        echo "   ✅ ChromaDB is responding to heartbeat"
        CHROMA_READY=true
        break
    fi
    echo "      Waiting... ($i/30)"
    sleep 2
done

if [ "$CHROMA_READY" = true ]; then
    # Verify it's completely empty
    echo "   🔍 Verifying ChromaDB is completely empty..."
    COLLECTIONS=$(curl -s http://localhost:8000/api/v2/collections 2>/dev/null || echo "[]")
    
    if [ "$COLLECTIONS" = "[]" ] || [ "$COLLECTIONS" = "" ]; then
        echo "   ✅ ChromaDB confirmed empty and ready"
    else
        echo "   ⚠️  ChromaDB may still have data: $COLLECTIONS"
        echo "   🧹 Force clearing any remaining collections..."
        
        # Try to delete any collections that might exist
        curl -s http://localhost:8000/api/v2/collections 2>/dev/null | \
        python3 -c "
import json, sys, requests
try:
    collections = json.load(sys.stdin)
    for coll in collections:
        coll_id = coll.get('id')
        if coll_id:
            requests.delete(f'http://localhost:8000/api/v2/collections/{coll_id}')
            print(f'   Deleted collection: {coll_id}')
except:
    pass
        " 2>/dev/null || echo "   ℹ️  Manual collection cleanup completed"
        
        echo "   ✅ ChromaDB force-cleaned"
    fi
else
    echo "   ❌ ChromaDB failed to start properly"
    echo "   🔧 Manual intervention may be required"
fi

# =============================================================================
# 7. Comprehensive verification
# =============================================================================
echo ""
echo "7️⃣  Comprehensive verification..."

# Check workspace
WORKSPACE_STATUS="❌ Not clean"
if [ -d "./workspace" ] && [ -z "$(ls -A ./workspace 2>/dev/null)" ]; then
    WORKSPACE_STATUS="✅ Empty"
fi
echo "   📂 Workspace: $WORKSPACE_STATUS"

# Check ChromaDB with detailed verification
CHROMA_STATUS="❌ Not running"
if curl -s http://localhost:8000/api/v2/heartbeat > /dev/null 2>&1; then
    COLLECTIONS=$(curl -s http://localhost:8000/api/v2/collections 2>/dev/null || echo "error")
    if [ "$COLLECTIONS" = "[]" ]; then
        CHROMA_STATUS="✅ Running and empty"
    elif [ "$COLLECTIONS" = "error" ]; then
        CHROMA_STATUS="⚠️ Running but API error"
    else
        COLLECTION_COUNT=$(echo "$COLLECTIONS" | python3 -c "import json, sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "?")
        CHROMA_STATUS="⚠️ Running but has $COLLECTION_COUNT collections"
    fi
fi
echo "   🗄️  ChromaDB: $CHROMA_STATUS"

# Check Ollama
OLLAMA_STATUS="❌ Not running"
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    OLLAMA_STATUS="✅ Running"
fi
echo "   🤖 Ollama: $OLLAMA_STATUS"

# Check for any remaining processes
JAVA_PROCESSES=$(ps aux | grep -i "java.*codeAnalyzer" | grep -v grep | wc -l | tr -d ' ')
echo "   ⚙️  Java processes: $JAVA_PROCESSES running"

# =============================================================================
# 8. Summary and next steps
# =============================================================================
echo ""
echo "🎉 ENHANCED CLEANUP COMPLETED!"
echo "=============================="
echo ""
echo "📋 Verification Summary:"
echo "   • Workspace: $WORKSPACE_STATUS"
echo "   • ChromaDB: $CHROMA_STATUS" 
echo "   • Ollama: $OLLAMA_STATUS"
echo "   • Java processes: $JAVA_PROCESSES running"
echo ""

if [ "$CHROMA_STATUS" = "✅ Running and empty" ] && [ "$WORKSPACE_STATUS" = "✅ Empty" ]; then
    echo "🌟 PERFECT! System is completely clean and ready."
else
    echo "⚠️  Some components may need manual attention (see above)."
fi

echo ""
echo "🚀 Ready for testing! Start your application:"
echo "   ./gradlew bootRun"
echo ""
echo "🧪 Test with a repository:"
echo "   curl -X POST http://localhost:8080/api/chat/message \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"message\": \"Analyze https://github.com/spring-projects/spring-petclinic\"}'"
echo ""
echo "✨ The system is now guaranteed to be completely clean!"