# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an AI Code Analysis Agent built with Spring Boot 3.5.4 and Java 17. It implements a RAG (Retrieval-Augmented Generation) system that clones Git repositories, parses Java code, generates embeddings using local LLMs, and provides intelligent code analysis through natural language queries.

## External Dependencies Required

The application requires three external services to be running:

1. **Ollama** (Local LLM service) - Port 11434
   - Models: `codellama:7b-instruct` (code analysis), `unclemusclez/jina-embeddings-v2-base-code:latest` (embeddings), `phi3:3.8b` (chat intent analysis)
   - Install: `brew install ollama` then `ollama serve`
   - Pull models: `ollama pull codellama:7b-instruct && ollama pull unclemusclez/jina-embeddings-v2-base-code:latest && ollama pull phi3:3.8b`

2. **Chroma** (Vector database) - Port 8000
   - Run via Docker: `docker run -d --name chroma-db -p 8000:8000 -v $(pwd)/chroma_data:/chroma/chroma -e IS_PERSISTENT=TRUE -e PERSIST_DIRECTORY=/chroma/chroma ghcr.io/chroma-core/chroma:latest`

3. **Git** (for repository cloning)

## Common Development Commands

### Build & Run
```bash
# Build the application
./gradlew build

# Run the application (with auto-reload during development)
./gradlew bootRun

# Run tests
./gradlew test

# Run single test class
./gradlew test --tests "ClassName"

# Run with debug output
./gradlew bootRun --debug
```

### Testing the Complete Flow
```bash
# Run the integrated test script (tests full pipeline)
chmod +x src/main/resources/test_complete_flow.sh
./src/main/resources/test_complete_flow.sh

# Alternative test script
./src/main/resources/test_complete_flow_v2.sh

# Query testing
./src/main/resources/query_test_script.sh
```

### Health Checks
```bash
# Check application health
curl http://localhost:8080/api/analysis/health

# Verify external services
curl http://localhost:11434/api/tags        # Ollama
curl http://localhost:8000/api/v2/heartbeat # Chroma
```

## Architecture Overview

The system follows a layered RAG architecture with these key services:

### Service Layer Components

- **CodeAnalysisService**: Main orchestrator for the analysis pipeline, manages progress tracking
- **RepositoryService**: Git repository management using JGit (cloning, workspace management)
- **CodeParserService**: Java code parsing with JavaParser library (AST extraction, Spring annotation detection)
- **EmbeddingService**: Vector embedding generation via Ollama's embedding models
- **VectorStoreService**: Chroma vector database integration for similarity search
- **QueryService**: RAG orchestrator combining vector search with LLM analysis
- **LLMService**: CodeLlama integration for code explanation and analysis
- **ChatAgentService**: Interactive chat session management and conversation context
- **ChatLLMService**: Chat-specific LLM integration with action execution capabilities

### Data Flow Pipeline

1. **Repository Ingestion**: Clone → Parse Java files → Generate embeddings → Store in Chroma
2. **Query Processing**: User query → Embedding → Vector search → Context building → LLM analysis → Response

### API Structure

The REST API is organized into four main controllers:
- **RepositoryController**: Repository management (`/api/repository/*`)
- **AnalysisController**: Code analysis operations (`/api/analysis/*`)
- **QueryController**: Querying and search (`/api/query/*`)
- **ChatController**: Interactive chat interface (`/api/chat/*`)

Refer to `src/main/resources/static/openapi.yaml` for complete API documentation.

## Configuration

Key configuration files:
- `src/main/resources/application.properties`: Main application configuration
- `build.gradle`: Dependencies and build configuration
- `src/main/resources/static/openapi.yaml`: API specification

Important configuration sections:
- External service URLs (Ollama, Chroma)
- Processing limits (file size, concurrent files, chunk sizes)
- Workspace directory settings
- Model names for embeddings and LLM
- Chat agent settings (session timeout, max sessions)
- Code chunking strategy and priorities
- Async processing configuration

## Key Development Patterns

### Async Processing
The application uses CompletableFuture extensively for non-blocking operations, especially in:
- Repository cloning (`RepositoryService`)
- Code analysis pipeline (`CodeAnalysisService`)

### Error Handling
- Comprehensive exception handling with detailed logging
- Retry mechanisms for external service calls
- Progress tracking includes error states
- Health checking across all external dependencies

### Code Structure Conventions
- Service layer pattern with clear separation of concerns
- Use of Spring Boot auto-configuration and dependency injection
- Visitor pattern for AST traversal in code parsing
- Template method pattern for different LLM prompt types
- Chat system uses strategy pattern for action execution
- WebClient for all external service communication (non-blocking)

## Testing and Validation

- Unit tests in `src/test/java/com/suhas/codeAnalyzer/`
- Integration test scripts in `src/main/resources/`
- Health check endpoints for monitoring service dependencies
- The complete flow test script validates the entire RAG pipeline

## Chat Interface

The application includes an interactive chat interface for code analysis:

### Chat Features
- **Session Management**: Persistent conversation contexts with timeout handling
- **Action Execution**: LLM can trigger repository analysis, code queries, and searches
- **SLM Intent Analysis**: Uses phi3:3.8b model for understanding user intent instead of keyword matching
- **Pattern Matching**: Fallback system when LLM integration is unavailable
- **Debug Interface**: Separate debug endpoints for troubleshooting chat functionality

### Chat API Endpoints
- `/api/chat/message`: Send chat messages and receive responses
- `/api/chat/debug/*`: Debug interfaces for chat troubleshooting
- `/api/chat/test/*`: Test endpoints for chat functionality validation

### Chat Configuration
Key chat settings in `application.properties`:
```properties
# Chat Agent Configuration
chat.agent.enabled=true
chat.agent.session-timeout=36000000
chat.agent.max-sessions=100
chat.force-pattern-matching=false
chat.use-pattern-matching=true

# SLM Intent Analysis Configuration
slm.intent.model=phi3:3.8b
slm.intent.temperature=0.1
slm.intent.max-tokens=200
slm.intent.timeout=10s
```

## Workspace Management

- Local repositories are cloned to `./workspace/` directory
- Workspace cleanup is configurable via `app.workspace.cleanup-on-startup`
- Maximum repositories limit: 10 (configurable)
- Supported file extensions: `.java`, `.kt`, `.groovy`