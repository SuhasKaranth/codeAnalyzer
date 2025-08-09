#!/bin/bash

# Chroma v2 API Verification Script
echo "=========================================="
echo "Chroma v2 API Verification"
echo "=========================================="
echo ""

CHROMA_BASE="http://localhost:8000"
TENANT="default_tenant"
DATABASE="default_database"

# Function to make requests with error handling
check_api() {
    local description="$1"
    local url="$2"
    local method="${3:-GET}"
    local data="$4"

    echo "🔍 $description"
    echo "→ $method $url"

    if [ "$method" = "POST" ] && [ -n "$data" ]; then
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST "$url" \
                      -H "Content-Type: application/json" \
                      -d "$data")
    else
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X "$method" "$url")
    fi

    http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    body=$(echo $response | sed -e 's/HTTPSTATUS:.*//g')

    if [ $http_code -eq 200 ] || [ $http_code -eq 201 ]; then
        echo "✅ Success ($http_code)"
        echo "$body" | jq '.' 2>/dev/null || echo "$body"
    else
        echo "❌ Failed ($http_code)"
        echo "$body"
    fi
    echo ""
}

# 1. Test heartbeat
check_api "Testing Chroma heartbeat" "$CHROMA_BASE/api/v2/heartbeat"

# 2. List all collections
check_api "Listing all collections" "$CHROMA_BASE/api/v2/tenants/$TENANT/databases/$DATABASE/collections"

# 3. Get collection details (if exists)
echo "🔍 Getting collection details..."
collections_response=$(curl -s "$CHROMA_BASE/api/v2/tenants/$TENANT/databases/$DATABASE/collections")
collection_uuid=$(echo "$collections_response" | jq -r '.[] | select(.name=="java-code-repo") | .id' 2>/dev/null)

if [ "$collection_uuid" != "" ] && [ "$collection_uuid" != "null" ]; then
    echo "✅ Found collection 'java-code-repo' with UUID: $collection_uuid"
    echo ""

    # Get collection count
    check_api "Getting collection count" "$CHROMA_BASE/api/v2/tenants/$TENANT/databases/$DATABASE/collections/$collection_uuid/count"

    # Get collection details (this endpoint might not work reliably in v2)
    echo "🔍 Getting collection details..."
    collection_details_response=$(curl -s -w "HTTPSTATUS:%{http_code}" "$CHROMA_BASE/api/v2/tenants/$TENANT/databases/$DATABASE/collections/$collection_uuid")
    details_http_code=$(echo $collection_details_response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    details_body=$(echo $collection_details_response | sed -e 's/HTTPSTATUS:.*//g')

    if [ $details_http_code -eq 200 ]; then
        echo "✅ Collection details retrieved"
        echo "$details_body" | jq '.' 2>/dev/null || echo "$details_body"
    else
        echo "⚠️  Collection details endpoint returned $details_http_code (this is often expected in Chroma v2)"
        echo "   Collection is still functional for queries and operations"
    fi
    echo ""

    # Test query with proper 768-dimension embedding (filled with zeros and a few test values)
    echo "🔍 Testing query with proper 768-dimension embedding..."
    # Create a 768-dimension array with mostly zeros and a few non-zero values for testing
    embedding_768d="[0.1, 0.2, 0.3, 0.4, 0.5"
    for i in {6..768}; do
        embedding_768d="$embedding_768d, 0.0"
    done
    embedding_768d="$embedding_768d]"

    sample_query="{
        \"query_embeddings\": [$embedding_768d],
        \"n_results\": 2
    }"
    check_api "Testing collection query with 768-dim embedding" "$CHROMA_BASE/api/v2/tenants/$TENANT/databases/$DATABASE/collections/$collection_uuid/query" "POST" "$sample_query"

else
    echo "❌ Collection 'java-code-repo' not found"
    echo "Available collections:"
    echo "$collections_response" | jq '.[] | {name: .name, id: .id}' 2>/dev/null || echo "$collections_response"
    echo ""
fi

echo "=========================================="
echo "Verification Complete"
echo "=========================================="
echo ""
echo "Manual Commands for Testing:"
echo ""
echo "# List collections:"
echo "curl '$CHROMA_BASE/api/v2/tenants/$TENANT/databases/$DATABASE/collections'"
echo ""

if [ "$collection_uuid" != "" ] && [ "$collection_uuid" != "null" ]; then
    echo "# Get collection count:"
    echo "curl '$CHROMA_BASE/api/v2/tenants/$TENANT/databases/$DATABASE/collections/$collection_uuid/count'"
    echo ""
    echo "# Query collection:"
    echo "curl -X POST '$CHROMA_BASE/api/v2/tenants/$TENANT/databases/$DATABASE/collections/$collection_uuid/query' \\"
    echo "  -H 'Content-Type: application/json' \\"
    echo "  -d '{\"query_embeddings\": [[0.1, 0.2, 0.3]], \"n_results\": 5}'"
    echo ""
fi

echo "# Generate real embedding for testing:"
echo "curl -X POST 'http://localhost:11434/api/embeddings' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"model\": \"nomic-embed-text\", \"prompt\": \"Spring Boot REST controller\"}'"