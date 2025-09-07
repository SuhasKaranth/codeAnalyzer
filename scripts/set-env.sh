#!/bin/bash

# Load environment variables from .env file for Spring Boot application

if [ -f .env ]; then
    echo "Loading environment variables from .env file..."
    export $(cat .env | grep -v '^#' | xargs)
    echo "Environment variables loaded successfully"
    echo "GROQ_API_KEY is set: ${GROQ_API_KEY:0:20}..."
else
    echo "Error: .env file not found"
    exit 1
fi