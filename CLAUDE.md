# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an AI Code Analysis Agent built with Spring Boot 3.5.4 and Java 17. It implements a RAG (Retrieval-Augmented Generation) system that clones Git repositories, parses Java code, generates embeddings using local LLMs, and provides intelligent code analysis through natural language queries.

## External Dependencies Required

The application requires three external services to be running:

1. **Ollama** (Local LLM service) - Port 11434
   - Models: `codellama:7b-instruct` (code analysis), `nomic-embed-text` (embeddings)
   - Install: `brew install ollama` then `ollama serve`
   - Pull models: `ollama pull codellama:7b-instruct && ollama pull nomic-embed-text`

2. **Chroma** (Vector database) - Port 8000
   - Run via Docker: `docker run -d --name chroma-db -p 8000:8000 -v $(pwd)/chroma_data:/chroma/chroma -e IS_PERSISTENT=TRUE -e PERSIST_DIRECTORY=/chroma/chroma ghcr.io/chroma-core/chroma:latest`

3. **Git** (for repository cloning)

## Common Development Commands

### Build & Run
```bash
# Build the application
./gradlew build

# Run the application
./gradlew bootRun

# Run tests
./gradlew test
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

### Data Flow Pipeline

1. **Repository Ingestion**: Clone → Parse Java files → Generate embeddings → Store in Chroma
2. **Query Processing**: User query → Embedding → Vector search → Context building → LLM analysis → Response

### API Structure

The REST API is organized into three main controllers:
- **RepositoryController**: Repository management (`/api/repository/*`)
- **AnalysisController**: Code analysis operations (`/api/analysis/*`)
- **QueryController**: Querying and search (`/api/query/*`)

Refer to `src/main/resources/static/openapi.yaml` for complete API documentation.

## Configuration

Key configuration files:
- `src/main/resources/application.properties`: Main application configuration
- `build.gradle`: Dependencies and build configuration
- `src/main/resources/static/openapi.yaml`: API specification

Important configuration sections:
- External service URLs (Ollama, Chroma)
- Processing limits (file size, concurrent files)
- Workspace directory settings
- Model names for embeddings and LLM

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

## Testing and Validation

- Unit tests in `src/test/java/com/suhas/codeAnalyzer/`
- Integration test scripts in `src/main/resources/`
- Health check endpoints for monitoring service dependencies
- The complete flow test script validates the entire RAG pipeline

## Workspace Management

- Local repositories are cloned to `./workspace/` directory
- Workspace cleanup is configurable via `app.workspace.cleanup-on-startup`
- Maximum repositories limit: 10 (configurable)
- Supported file extensions: `.java`, `.kt`, `.groovy`