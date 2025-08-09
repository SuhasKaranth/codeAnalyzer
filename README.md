# AI Code Analysis Agent

A Spring Boot application that analyzes Java codebases using RAG (Retrieval-Augmented Generation) with local LLMs. The system clones Git repositories, parses Java code, generates embeddings, and provides intelligent code analysis through natural language queries.

## 🏗️ Architecture

The system consists of four main components:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│     Ollama      │    │     Chroma      │    │  Spring Boot    │    │   User Query    │
│   (LLM + Embed) │    │  (Vector Store) │    │     Service     │    │   Interface     │
│   Port 11434    │    │   Port 8000     │    │   Port 8080     │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Pipeline Flow:
1. **Repository Cloning**: Downloads Java repositories locally
2. **Code Parsing**: Uses JavaParser to extract classes, methods, annotations
3. **Embedding Generation**: Creates vector representations using Ollama
4. **Vector Storage**: Stores embeddings in Chroma for similarity search
5. **Query Processing**: RAG-based question answering about the codebase

## 🚀 Features

- **Repository Analysis**: Clone and analyze any public Java Git repository
- **Code Understanding**: Parse Spring Boot applications, identify controllers, services, repositories
- **Embedding Storage**: Local vector database for code similarity search
- **REST API**: Complete API for repository management and code analysis
- **Async Processing**: Non-blocking analysis with progress tracking
- **Local AI**: No external API calls - everything runs locally

## 📋 Prerequisites

- **Java 17+**
- **Docker** (for Chroma vector database)
- **Homebrew** (for macOS - to install Ollama)
- **Git**

## 🛠️ Installation & Setup

### 1. Install Ollama (Local LLM)

```bash
# Install Ollama using Homebrew (macOS)
brew install ollama

# Start Ollama service
ollama serve

# In a new terminal, pull the required models
ollama pull codellama:7b-instruct    # For code analysis
ollama pull nomic-embed-text         # For embeddings

# Verify installation
curl http://localhost:11434/api/tags
```

### 2. Install & Start Chroma (Vector Database)

```bash
# Pull Chroma Docker image
docker pull ghcr.io/chroma-core/chroma:latest

# Start Chroma with persistence
docker run -d \
  --name chroma-db \
  -p 8000:8000 \
  -v $(pwd)/chroma_data:/chroma/chroma \
  -e IS_PERSISTENT=TRUE \
  -e PERSIST_DIRECTORY=/chroma/chroma \
  ghcr.io/chroma-core/chroma:latest

# Verify Chroma is running
curl http://localhost:8000/api/v2/heartbeat
```

### 3. Clone & Setup the Application

```bash
# Clone this repository
git clone <your-repo-url>
cd ai-code-analysis-agent

# Build the application
./gradlew build

# Run the application
./gradlew bootRun
```

### 4. Verify Setup

```bash
# Check all services are running
curl http://localhost:11434/api/tags        # Ollama
curl http://localhost:8000/api/v2/heartbeat # Chroma
curl http://localhost:8080/api/analysis/health # Application
```

## 🎯 Usage

### Quick Start - Analyze a Repository

```bash
# 1. Clone a repository
curl -X POST "http://localhost:8080/api/repository/clone-sync?url=https://github.com/spring-guides/gs-rest-service.git"

# 2. Start analysis
curl -X POST "http://localhost:8080/api/analysis/start?url=https://github.com/spring-guides/gs-rest-service.git"

# 3. Monitor progress
curl -X GET "http://localhost:8080/api/analysis/progress?url=https://github.com/spring-guides/gs-rest-service.git"

# 4. Get analysis summary
curl -X GET "http://localhost:8080/api/analysis/summary"
```

### Run Complete Test Suite

```bash
# Make the test script executable
chmod +x src/main/resources/test_complete_flow.sh

# Run the complete flow test
./src/main/resources/test_complete_flow.sh
```

## 📚 API Documentation

### Repository Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/repository/clone` | POST | Clone repository (async) |
| `/api/repository/clone-sync` | POST | Clone repository (sync) |
| `/api/repository/status` | GET | Get repository status |
| `/api/repository/files` | GET | List Java files |
| `/api/repository/cleanup` | DELETE | Clean up repository |

### Code Analysis

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/analysis/start` | POST | Start code analysis |
| `/api/analysis/progress` | GET | Get analysis progress |
| `/api/analysis/files` | POST | Analyze specific files |
| `/api/analysis/summary` | GET | Get analysis summary |
| `/api/analysis/health` | GET | Health check |

### Example API Calls

```bash
# Clone a repository
curl -X POST "http://localhost:8080/api/repository/clone?url=https://github.com/user/repo.git"

# Check status
curl -X GET "http://localhost:8080/api/repository/status?url=https://github.com/user/repo.git"

# Start analysis
curl -X POST "http://localhost:8080/api/analysis/start?url=https://github.com/user/repo.git"

# Analyze specific files
curl -X POST "http://localhost:8080/api/analysis/files?url=https://github.com/user/repo.git" \
  -H "Content-Type: application/json" \
  -d '["src/main/java/Controller.java", "src/main/java/Service.java"]'
```

## ⚙️ Configuration

Main configuration in `src/main/resources/application.properties`:

```properties
# Server
server.port=8080

# External Services
external.ollama.base-url=http://localhost:11434
external.ollama.embedding-model=nomic-embed-text
external.ollama.llm-model=codellama:7b-instruct

external.chroma.base-url=http://localhost:8000
external.chroma.collection-name=java-code-repo

# Application
app.workspace.directory=./workspace
```

## 🔧 Troubleshooting

### Common Issues

1. **Ollama not responding**
   ```bash
   # Restart Ollama
   pkill ollama
   ollama serve
   ```

2. **Chroma connection failed**
   ```bash
   # Restart Chroma
   docker restart chroma-db
   # Or recreate
   docker stop chroma-db && docker rm chroma-db
   # Then run the docker run command again
   ```

3. **Application startup errors**
   - Check if all services are running on correct ports
   - Verify `application.properties` configuration
   - Check Java version (requires Java 17+)

4. **Analysis fails**
   - Check application logs for detailed errors
   - Verify repository has Java files
   - Ensure sufficient disk space for cloning

### Debug Commands

```bash
# Check service health
curl http://localhost:8080/api/analysis/health

# Check Ollama models
ollama list

# Check Chroma collections
curl http://localhost:8000/api/v2/collections

# View application logs
./gradlew bootRun --debug
```

## 🧪 Current Status & Known Issues

### ✅ Working Features
- Repository cloning and file parsing
- Code analysis with JavaParser
- Embedding generation with Ollama
- Health checks and monitoring
- REST API endpoints

### 🔄 In Progress
- Vector storage in Chroma (debugging storage issues)
- Query functionality for code search
- Sequence diagram generation

### 🚧 Planned Features
- Business logic flow analysis
- API endpoint detection and mapping
- Integration with RAG for code Q&A
- Web UI for easier interaction

## 🏛️ Project Structure

```
src/
├── main/
│   ├── java/com/suhas/codeAnalyzer/
│   │   ├── CodeAnalyzerApplication.java
│   │   ├── controller/
│   │   │   ├── RepositoryController.java
│   │   │   └── AnalysisController.java
│   │   └── service/
│   │       ├── RepositoryService.java
│   │       ├── CodeParserService.java
│   │       ├── EmbeddingService.java
│   │       ├── VectorStoreService.java
│   │       └── CodeAnalysisService.java
│   └── resources/
│       ├── application.properties
│       └── test_complete_flow.sh
└── build.gradle
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📝 Development Notes

- Uses Spring Boot 3.5.4 with Java 17
- JavaParser for AST-based code analysis
- WebClient for async HTTP operations
- Docker for containerized dependencies
- Local-first approach - no external API dependencies

## 📄 License

To be updated!

---

For issues or questions, please create a GitHub issue with detailed information about your setup and the problem you're encountering.
