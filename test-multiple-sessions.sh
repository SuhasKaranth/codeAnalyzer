#!/bin/bash

# Test Multiple Browser Session Scenario
echo "🧪 Testing Multiple Session Management"
echo "====================================="

BASE_URL="http://localhost:8080/api/v1/user"
USER_EMAIL="test@example.com"

# Generate unique session IDs to simulate different browsers
SESSION_ID_1="browser_1_$(uuidgen | tr '[:upper:]' '[:lower:]')"
SESSION_ID_2="browser_2_$(uuidgen | tr '[:upper:]' '[:lower:]')"

echo "📧 User: $USER_EMAIL"
echo "🌐 Browser 1 Session ID: $SESSION_ID_1"
echo "🌐 Browser 2 Session ID: $SESSION_ID_2"
echo ""

# Test 1: Create first session (Browser 1)
echo "🔷 Test 1: Creating session for Browser 1"
curl -s -X POST "$BASE_URL/session/start" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\": \"$SESSION_ID_1\", \"email\": \"$USER_EMAIL\"}" | jq '.'
echo ""

# Test 2: Create second session (Browser 2) 
echo "🔷 Test 2: Creating session for Browser 2 (same user)"
curl -s -X POST "$BASE_URL/session/start" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\": \"$SESSION_ID_2\", \"email\": \"$USER_EMAIL\"}" | jq '.'
echo ""

echo "✅ Multiple session scenario test completed!"
