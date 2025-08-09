#!/bin/bash

# Query API Test Script for AI Code Analysis Agent
echo "=============================================="
echo "Testing Query API Endpoints (Fixed Version)"
echo "=============================================="
echo ""

BASE_URL="http://localhost:8080"

# Function to make HTTP requests with better formatting and timeout handling
test_api() {
    local description="$1"
    local method="$2"
    local endpoint="$3"
    local data="$4"
    local timeout="${5:-30}"  # Default 30s timeout, can be overridden

    echo "🔍 $description"
    echo "→ $method $endpoint (timeout: ${timeout}s)"

    if [ "$method" = "POST" ] && [ -n "$data" ]; then
        response=$(curl -s -X POST "$BASE_URL$endpoint" \
                      -H "Content-Type: application/json" \
                      -d "$data" \
                      --max-time $timeout \
                      -w "HTTPSTATUS:%{http_code}")
    else
        response=$(curl -s -X GET "$BASE_URL$endpoint" \
                      --max-time $timeout \
                      -w "HTTPSTATUS:%{http_code}")
    fi

    http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    body=$(echo $response | sed -e 's/HTTPSTATUS:.*//g')

    if [ $http_code -eq 200 ] || [ $http_code -eq 202 ]; then
        echo "✅ Success ($http_code)"
        # Pretty print with jq if available, otherwise show raw
        echo "$body" | jq '.' 2>/dev/null || echo "$body"
    elif [ $http_code -eq 000 ]; then
        echo "⏱️  Timeout (${timeout}s) - LLM might be slow, try again"
    else
        echo "❌ Failed ($http_code)"
        echo "$body"
    fi
    echo ""
    sleep 1  # Small delay between requests
}

echo "Step 1: Quick Tests (No LLM - Should be Fast)"
echo "=============================================="

# Test POST search without explanation (should be fast)
test_api "Simple search without explanation" "POST" "/api/query/search" '{
    "query": "controller",
    "maxResults": 3,
    "includeExplanation": false,
    "filters": {}
}' 10

# Test type search (known to work)
test_api "Search for CLASS types" "GET" "/api/query/type/CLASS?limit=3" "" 10

# Test Spring components (should work now)
test_api "Find Spring Boot components" "GET" "/api/query/spring-components?limit=3" "" 10

echo ""
echo "Step 2: LLM-Powered Tests (Slower - May Take 30-60s Each)"
echo "========================================================"

# Test with LLM explanation (slower)
test_api "Search with LLM explanation" "POST" "/api/query/search" '{
    "query": "Spring Boot controller",
    "maxResults": 2,
    "includeExplanation": true,
    "filters": {}
}' 90

# Test API endpoints (has LLM analysis)
test_api "Find API endpoints with analysis" "GET" "/api/query/endpoints" "" 90

# Test simple question (has LLM)
test_api "Simple question (may be slow)" "GET" "/api/query/ask?q=What+controllers+are+available?" "" 90

echo ""
echo "Step 3: Utility Tests (Fast)"
echo "============================"

# Test getting suggestions (no LLM)
test_api "Get query suggestions" "GET" "/api/query/suggestions" "" 10

# Test getting search stats (no LLM)
test_api "Get search statistics" "GET" "/api/query/stats" "" 10

echo ""
echo "Step 4: Advanced Search Tests"
echo "============================="

# Test structured search
test_api "Structured search request" "POST" "/api/query/search" '{
    "query": "How does the greeting functionality work?",
    "maxResults": 3,
    "includeCode": true,
    "includeExplanation": true,
    "filters": {}
}' 90

# Test advanced search with filters
test_api "Advanced search with filters" "POST" "/api/query/advanced" '{
    "query": "Find REST methods",
    "maxResults": 3,
    "includeCode": true,
    "includeExplanation": false,
    "filters": {
        "type": "METHOD"
    }
}' 30

echo ""
echo "=============================================="
echo "Query API Testing Complete!"
echo "=============================================="
echo ""
echo "📊 Performance Notes:"
echo "- Searches without LLM explanations: ~1-5 seconds"
echo "- Searches with LLM explanations: ~30-90 seconds"
echo "- First LLM request may be slower (model loading)"
echo ""
echo "🚀 Quick Test Commands:"
echo ""
echo "# Fast search (no LLM):"
echo "curl -X POST '$BASE_URL/api/query/search' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"query\": \"controller\", \"maxResults\": 3, \"includeExplanation\": false}'"
echo ""
echo "# Find specific types:"
echo "curl -X GET '$BASE_URL/api/query/type/CLASS'"
echo "curl -X GET '$BASE_URL/api/query/type/METHOD'"
echo ""
echo "# With LLM explanation (slower):"
echo "curl -X POST '$BASE_URL/api/query/search' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"query\": \"Spring Boot\", \"maxResults\": 2, \"includeExplanation\": true}'"
echo ""
echo "Available Endpoints:"
echo "- GET  /api/query/ask?q=<question>          (with LLM - slow)"
echo "- POST /api/query/search                    (fast without explanation)"
echo "- GET  /api/query/type/{type}              (fast)"
echo "- GET  /api/query/spring-components        (fast)"
echo "- GET  /api/query/endpoints                (with LLM - slow)"
echo "- POST /api/query/business-logic           (with LLM - slow)"
echo "- GET  /api/query/suggestions              (fast)"
echo "- GET  /api/query/stats                    (fast)"
echo "- POST /api/query/advanced                 (variable speed)"