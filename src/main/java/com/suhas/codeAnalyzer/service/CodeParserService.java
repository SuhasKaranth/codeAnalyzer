package com.suhas.codeAnalyzer.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CodeParserService {

    private static final Logger logger = LoggerFactory.getLogger(CodeParserService.class);
    private final JavaParser javaParser = new JavaParser();

    public static class CodeChunk {
        private String id;
        private String content;
        private String type; // CLASS, METHOD, INTERFACE
        private String className;
        private String methodName;
        private String packageName;
        private String filePath;
        private List<String> annotations;
        private List<String> imports;
        private Map<String, Object> metadata;

        public CodeChunk() {
            this.annotations = new ArrayList<>();
            this.imports = new ArrayList<>();
            this.metadata = new HashMap<>();
        }

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }

        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }

        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public List<String> getAnnotations() { return annotations; }
        public void setAnnotations(List<String> annotations) { this.annotations = annotations; }

        public List<String> getImports() { return imports; }
        public void setImports(List<String> imports) { this.imports = imports; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    /**
     * Parse all Java files in repository and extract code chunks
     */
    public List<CodeChunk> parseRepository(String repositoryPath) throws IOException {
        logger.info("Starting to parse repository: {}", repositoryPath);

        Path repoPath = Path.of(repositoryPath);
        List<Path> javaFiles = Files.walk(repoPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.toString().contains("/.git/"))
                .filter(path -> !path.toString().contains("/target/"))
                .filter(path -> !path.toString().contains("/build/"))
                .filter(path -> !path.toString().contains("/test/")) // Skip test files for POC
                .toList();

        logger.info("Found {} Java files to parse", javaFiles.size());

        List<CodeChunk> allChunks = new ArrayList<>();

        for (Path javaFile : javaFiles) {
            try {
                List<CodeChunk> fileChunks = parseJavaFile(javaFile, repositoryPath);
                allChunks.addAll(fileChunks);
                logger.debug("Parsed {} chunks from {}", fileChunks.size(), javaFile.getFileName());
            } catch (Exception e) {
                logger.warn("Failed to parse file {}: {}", javaFile, e.getMessage());
            }
        }

        logger.info("Successfully parsed {} total code chunks from repository", allChunks.size());
        return allChunks;
    }

    /**
     * Parse a single Java file and extract code chunks
     */
    public List<CodeChunk> parseJavaFile(Path javaFilePath, String repositoryRoot) throws IOException {
        String content = Files.readString(javaFilePath);

        try {
            CompilationUnit cu = javaParser.parse(content).getResult()
                    .orElseThrow(() -> new RuntimeException("Failed to parse Java file"));

            List<CodeChunk> chunks = new ArrayList<>();

            // Extract package and imports
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            List<String> imports = cu.getImports().stream()
                    .map(id -> id.getNameAsString())
                    .collect(Collectors.toList());

            // Use visitor pattern to extract classes and methods
            CodeExtractionVisitor visitor = new CodeExtractionVisitor(javaFilePath, repositoryRoot, packageName, imports);
            visitor.visit(cu, chunks);

            return chunks;

        } catch (Exception e) {
            logger.error("Error parsing Java file {}: {}", javaFilePath, e.getMessage());
            throw new RuntimeException("Failed to parse Java file: " + javaFilePath, e);
        }
    }

    /**
     * Visitor class to extract code structures
     */
    private static class CodeExtractionVisitor extends VoidVisitorAdapter<List<CodeChunk>> {
        private final Path javaFilePath;
        private final String repositoryRoot;
        private final String packageName;
        private final List<String> imports;

        public CodeExtractionVisitor(Path javaFilePath, String repositoryRoot, String packageName, List<String> imports) {
            this.javaFilePath = javaFilePath;
            this.repositoryRoot = repositoryRoot;
            this.packageName = packageName;
            this.imports = imports;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, List<CodeChunk> chunks) {
            // Create chunk for the entire class
            CodeChunk classChunk = createClassChunk(n);
            chunks.add(classChunk);

            // Extract methods from this class
            n.getMethods().forEach(method -> {
                CodeChunk methodChunk = createMethodChunk(method, n.getNameAsString());
                chunks.add(methodChunk);
            });

            super.visit(n, chunks);
        }

        private CodeChunk createClassChunk(ClassOrInterfaceDeclaration classDecl) {
            CodeChunk chunk = new CodeChunk();
            chunk.setId(generateChunkId("CLASS", classDecl.getNameAsString(), null));
            chunk.setType(classDecl.isInterface() ? "INTERFACE" : "CLASS");
            chunk.setClassName(classDecl.getNameAsString());
            chunk.setPackageName(packageName);
            chunk.setFilePath(getRelativePath());
            chunk.setImports(imports);

            // Extract annotations
            List<String> annotations = classDecl.getAnnotations().stream()
                    .map(AnnotationExpr::toString)
                    .collect(Collectors.toList());
            chunk.setAnnotations(annotations);

            // Create content with context
            StringBuilder contentBuilder = new StringBuilder();

            // Add package and important imports
            if (!packageName.isEmpty()) {
                contentBuilder.append("package ").append(packageName).append(";\n\n");
            }

            // Add Spring-related imports for context
            imports.stream()
                    .filter(imp -> imp.contains("springframework") ||
                            imp.contains("javax.persistence") ||
                            imp.contains("jakarta.persistence"))
                    .forEach(imp -> contentBuilder.append("import ").append(imp).append(";\n"));

            if (!imports.isEmpty()) {
                contentBuilder.append("\n");
            }

            // Add class declaration and basic structure
            contentBuilder.append(classDecl.toString());
            chunk.setContent(contentBuilder.toString());

            // Add metadata for better searchability
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("isController", hasAnnotation(annotations, "Controller", "RestController"));
            metadata.put("isService", hasAnnotation(annotations, "Service"));
            metadata.put("isRepository", hasAnnotation(annotations, "Repository"));
            metadata.put("isComponent", hasAnnotation(annotations, "Component"));
            metadata.put("isEntity", hasAnnotation(annotations, "Entity"));
            metadata.put("methodCount", classDecl.getMethods().size());

            chunk.setMetadata(metadata);

            return chunk;
        }

        private CodeChunk createMethodChunk(MethodDeclaration methodDecl, String className) {
            CodeChunk chunk = new CodeChunk();
            chunk.setId(generateChunkId("METHOD", className, methodDecl.getNameAsString()));
            chunk.setType("METHOD");
            chunk.setClassName(className);
            chunk.setMethodName(methodDecl.getNameAsString());
            chunk.setPackageName(packageName);
            chunk.setFilePath(getRelativePath());

            // Extract method annotations
            List<String> annotations = methodDecl.getAnnotations().stream()
                    .map(AnnotationExpr::toString)
                    .collect(Collectors.toList());
            chunk.setAnnotations(annotations);

            // Create method content with context
            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append("// Class: ").append(className).append("\n");
            contentBuilder.append("// Package: ").append(packageName).append("\n\n");
            contentBuilder.append(methodDecl.toString());

            chunk.setContent(contentBuilder.toString());

            // Add metadata for method
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("isPublic", methodDecl.isPublic());
            metadata.put("isStatic", methodDecl.isStatic());
            metadata.put("returnType", methodDecl.getTypeAsString());
            metadata.put("parameterCount", methodDecl.getParameters().size());
            metadata.put("isEndpoint", hasAnnotation(annotations, "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping"));
            metadata.put("isTransactional", hasAnnotation(annotations, "Transactional"));

            chunk.setMetadata(metadata);

            return chunk;
        }

        private String generateChunkId(String type, String className, String methodName) {
            StringBuilder id = new StringBuilder();
            id.append(packageName.isEmpty() ? "default" : packageName);
            id.append(".").append(className);
            if (methodName != null) {
                id.append(".").append(methodName);
            }
            id.append(".").append(type.toLowerCase());
            return id.toString();
        }

        private String getRelativePath() {
            return Path.of(repositoryRoot).relativize(javaFilePath).toString();
        }

        private boolean hasAnnotation(List<String> annotations, String... annotationNames) {
            return annotations.stream()
                    .anyMatch(ann -> Arrays.stream(annotationNames)
                            .anyMatch(name -> ann.contains("@" + name)));
        }
    }

    /**
     * Filter chunks based on criteria (e.g., only Spring components)
     */
    public List<CodeChunk> filterChunks(List<CodeChunk> chunks, ChunkFilter filter) {
        return chunks.stream()
                .filter(chunk -> {
                    if (filter.includeClasses && "CLASS".equals(chunk.getType())) return true;
                    if (filter.includeMethods && "METHOD".equals(chunk.getType())) return true;
                    if (filter.includeInterfaces && "INTERFACE".equals(chunk.getType())) return true;
                    if (filter.onlySpringComponents) {
                        Map<String, Object> metadata = chunk.getMetadata();
                        return Boolean.TRUE.equals(metadata.get("isController")) ||
                                Boolean.TRUE.equals(metadata.get("isService")) ||
                                Boolean.TRUE.equals(metadata.get("isRepository")) ||
                                Boolean.TRUE.equals(metadata.get("isComponent"));
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    public static class ChunkFilter {
        public boolean includeClasses = true;
        public boolean includeMethods = true;
        public boolean includeInterfaces = true;
        public boolean onlySpringComponents = false;
        public int minContentLength = 50;
        public int maxContentLength = 5000;

        public static ChunkFilter defaultFilter() {
            return new ChunkFilter();
        }

        public static ChunkFilter springComponentsOnly() {
            ChunkFilter filter = new ChunkFilter();
            filter.onlySpringComponents = true;
            return filter;
        }
    }
}