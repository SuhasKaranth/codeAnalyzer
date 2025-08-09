#!/bin/bash

# Complete Flow Test Script for Code Analysis Agent
# This script tests the entire pipeline: clone -> analyze -> store embeddings

BASE_URL="http://localhost:8080"
TEST_REPO="https://github.com/spring-guides/gs-rest-service.git"

echo "======================================"
echo "Code Analysis Agent - Complete Flow Test"
echo "======================================"
echo "Base URL: $BASE_URL"
echo "Test Repository: $TEST_REPO"
echo ""

# Function to make HTTP requests with better error handling
make_request() {
    local method=$1
    local endpoint=$2
    local data=$3
    local description=$4

    echo "[$description]"
    echo "→ $method $endpoint"

    if [ "$method" = "POST" ] && [ -n "$data" ]; then
        response=$(curl -s -X POST "$BASE_URL$endpoint" \
                      -H "Content-Type: application/json" \
                      -d "$data" \
                      -w "HTTPSTATUS:%{http_code}")
    elif [ "$method" = "POST" ]; then
        response=$(curl -s -X POST "$BASE_URL$endpoint" \
                      -w "HTTPSTATUS:%{http_code}")
    else
        response=$(curl -s -X GET "$BASE_URL$endpoint" \
                      -w "HTTPSTATUS:%{http_code}")
    fi

    http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    body=$(echo $response | sed -e 's/HTTPSTATUS:.*//g')

    if [ $http_code -eq 200 ] || [ $http_code -eq 202 ]; then
        echo "✅ Success ($http_code)"
        echo "$body" | jq '.' 2>/dev/null || echo "$body"
    else
        echo "❌ Failed ($http_code)"
        echo "$body"
        return 1
    fi
    echo ""
}

# Function to wait for completion with progress
wait_for_completion() {
    local check_endpoint=$1
    local description=$2
    local max_attempts=${3:-30}
    local sleep_time=${4:-10}

    echo "[$description]"
    echo "→ Waiting for completion (max ${max_attempts} attempts, ${sleep_time}s intervals)"

    for i in $(seq 1 $max_attempts); do
        echo -n "  Attempt $i/$max_attempts: "

        response=$(curl -s -X GET "$BASE_URL$check_endpoint" \
                      -w "HTTPSTATUS:%{http_code}")

        http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
        body=$(echo $response | sed -e 's/HTTPSTATUS:.*//g')

        if [ $http_code -eq 200 ]; then
            status=$(echo "$body" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
            echo "Status: $status"

            if [ "$status" = "COMPLETED" ]; then
                echo "✅ Completed!"
                echo "$body" | jq '.' 2>/dev/null || echo "$body"
                echo ""
                return 0
            elif [ "$status" = "FAILED" ]; then
                echo "❌ Failed!"
                echo "$body" | jq '.' 2>/dev/null || echo "$body"
                echo ""
                return 1
            fi
        else
            echo "HTTP $http_code"
        fi

        if [ $i -lt $max_attempts ]; then
            sleep $sleep_time
        fi
    done

    echo "❌ Timeout after $max_attempts attempts"
    echo ""
    return 1
}

# Step 1: Health Check
echo "Step 1: Health Check"
echo "===================="
make_request "GET" "/api/analysis/health" "" "Checking service health"

# Step 2: Clone Repository
echo "Step 2: Clone Repository"
echo "========================"
encoded_url=$(echo "$TEST_REPO" | sed 's/https:\/\///g' | sed 's/\//%2F/g')
make_request "POST" "/api/repository/clone-sync?url=$TEST_REPO" "" "Cloning repository"

if [ $? -ne 0 ]; then
    echo "❌ Repository cloning failed. Exiting."
    exit 1
fi

# Step 3: Check Repository Status
echo "Step 3: Repository Status"
echo "========================="
make_request "GET" "/api/repository/status?url=$TEST_REPO" "" "Checking repository status"

# Step 4: List Java Files
echo "Step 4: List Java Files"
echo "======================="
make_request "GET" "/api/repository/files?url=$TEST_REPO" "" "Listing Java files"

# Step 5: Start Analysis
echo "Step 5: Start Code Analysis"
echo "==========================="
make_request "POST" "/api/analysis/start?url=$TEST_REPO" "" "Starting code analysis"

if [ $? -ne 0 ]; then
    echo "❌ Analysis start failed. Exiting."
    exit 1
fi

# Step 6: Monitor Analysis Progress
echo "Step 6: Monitor Analysis Progress"
echo "================================="
wait_for_completion "/api/analysis/progress?url=$TEST_REPO" "Monitoring analysis progress" 30 5

if [ $? -ne 0 ]; then
    echo "❌ Analysis did not complete successfully."
    exit 1
fi

# Step 7: Get Analysis Summary
echo "Step 7: Analysis Summary"
echo "========================"
make_request "GET" "/api/analysis/summary" "" "Getting analysis summary"

# Step 8: Test with Specific Files (if we know the structure)
echo "Step 8: Test Specific File Analysis"
echo "===================================="
specific_files='["src/main/java/com/example/restservice/RestServiceApplication.java"]'
make_request "POST" "/api/analysis/files?url=$TEST_REPO" "$specific_files" "Analyzing specific files"

echo "======================================"
echo "✅ Complete Flow Test Finished!"
echo "======================================"
echo ""
echo "Next Steps:"
echo "1. Check that embeddings are stored in Chroma:"
echo "   curl http://localhost:8000/api/v1/collections/java-code-repo"
echo ""
echo "2. You can now implement the query functionality to search through the code!"
echo ""
echo "3. Test embedding search manually:"
echo "   curl -X POST http://localhost:8000/api/v1/collections/java-code-repo/query \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"query_embeddings\": [[...]], \"n_results\": 5}'"