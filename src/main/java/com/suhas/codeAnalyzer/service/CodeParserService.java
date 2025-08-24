package com.suhas.codeAnalyzer.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.beans.factory.annotation.Value;
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

    // Configurable chunking parameters
    @Value("${code.chunking.max-size:3000}")
    private int maxChunkSize;

    @Value("${code.chunking.min-size:200}")
    private int minChunkSize;

    @Value("${code.chunking.overlap:200}")
    private int overlapSize;

    @Value("${code.chunking.strategy:ADAPTIVE}")
    private String chunkingStrategy;

    public static class CodeChunk {
        private String id;
        private String content;
        private String type; // CLASS, METHOD, INTERFACE, CLASS_METADATA, REST_ENDPOINTS, BUSINESS_LOGIC, CRUD_OPERATIONS, ACCESSORS
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

        public CodeChunk(String id, String content, String type, String className, String filePath, Map<String, Object> metadata) {
            this();
            this.id = id;
            this.content = content;
            this.type = type;
            this.className = className;
            this.filePath = filePath;
            this.metadata = metadata;
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
     * Method group for organizing related functionality
     */
    private static class MethodGroup {
        private final String type;
        private final List<MethodDeclaration> methods = new ArrayList<>();

        public MethodGroup(String type) {
            this.type = type;
        }

        public void addMethod(MethodDeclaration method) {
            methods.add(method);
        }

        public boolean isEmpty() {
            return methods.isEmpty();
        }

        public String getType() { return type; }
        public List<MethodDeclaration> getMethods() { return methods; }

        public int getTotalSize() {
            return methods.stream()
                    .mapToInt(method -> method.toString().length())
                    .sum();
        }
    }

    /**
     * Parse all Java files in repository and extract code chunks using optimal strategy
     */
    public List<CodeChunk> parseRepository(String repositoryPath) throws IOException {
        logger.info("Starting to parse repository with {} chunking strategy: {}", chunkingStrategy, repositoryPath);

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

        logger.info("Successfully parsed {} total code chunks from repository using {} strategy",
                allChunks.size(), chunkingStrategy);
        return allChunks;
    }

    /**
     * Parse a single Java file using intelligent chunking strategy
     */
    public List<CodeChunk> parseJavaFile(Path javaFilePath, String repositoryRoot) throws IOException {
        String content = Files.readString(javaFilePath);

        try {
            CompilationUnit cu = javaParser.parse(content).getResult()
                    .orElseThrow(() -> new RuntimeException("Failed to parse Java file"));

            // Use optimal chunking strategy
            return chunkJavaClass(cu, getRelativePath(javaFilePath, repositoryRoot));

        } catch (Exception e) {
            logger.error("Error parsing Java file {}: {}", javaFilePath, e.getMessage());
            throw new RuntimeException("Failed to parse Java file: " + javaFilePath, e);
        }
    }

    /**
     * Intelligent code chunking strategy for optimal RAG performance
     */
    public List<CodeChunk> chunkJavaClass(CompilationUnit cu, String filePath) {
        List<CodeChunk> chunks = new ArrayList<>();

        // Extract package and imports
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        List<String> imports = cu.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .collect(Collectors.toList());

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            String className = cls.getNameAsString();

            // 1. Always create class metadata chunk (for overview and search)
            CodeChunk classMetadata = createClassMetadataChunk(cu, cls, filePath, packageName, imports);
            chunks.add(classMetadata);

            // 2. Determine chunking strategy based on class size and configuration
            String fullClassCode = cls.toString();

            if ("CLASS_ONLY".equals(chunkingStrategy) || fullClassCode.length() <= maxChunkSize) {
                // Small class or forced class-only: One chunk with everything
                chunks.add(createFullClassChunk(cu, cls, filePath, packageName, imports));
            } else if ("METHOD_ONLY".equals(chunkingStrategy)) {
                // Force method-only chunking
                chunks.addAll(createIndividualMethodChunks(cls, filePath, packageName));
            } else {
                // ADAPTIVE strategy: Intelligent splitting for large classes
                chunks.addAll(chunkLargeClass(cls, filePath, packageName, imports));
            }
        });

        logger.debug("Created {} chunks for file {} using {} strategy",
                chunks.size(), filePath, chunkingStrategy);
        return chunks;
    }

    /**
     * Create metadata chunk with class overview (always useful for search)
     */
    private CodeChunk createClassMetadataChunk(CompilationUnit cu, ClassOrInterfaceDeclaration cls,
                                               String filePath, String packageName, List<String> imports) {
        StringBuilder metadata = new StringBuilder();

        // Package and important imports
        if (!packageName.isEmpty()) {
            metadata.append("package ").append(packageName).append(";\n\n");
        }

        // Key imports (Spring, JPA, etc.)
        imports.stream()
                .filter(this::isImportantImport)
                .forEach(imp -> metadata.append("import ").append(imp).append(";\n"));

        if (!imports.isEmpty()) {
            metadata.append("\n");
        }

        // Class declaration with annotations
        cls.getAnnotations().forEach(ann ->
                metadata.append(ann).append("\n"));

        // Class signature
        metadata.append(buildClassSignature(cls)).append(" {\n");

        // Method signatures only (for overview)
        cls.getMethods().forEach(method -> {
            method.getAnnotations().forEach(ann ->
                    metadata.append("    ").append(ann).append("\n"));
            metadata.append("    ").append(buildMethodSignature(method)).append(";\n");
        });

        metadata.append("}\n");

        return new CodeChunk(
                generateChunkId(filePath, "metadata", cls.getNameAsString(), null),
                metadata.toString(),
                "CLASS_METADATA",
                cls.getNameAsString(),
                filePath,
                createMetadataMap(cls, packageName, "CLASS_METADATA")
        );
    }

    /**
     * Create full class chunk for small classes
     */
    private CodeChunk createFullClassChunk(CompilationUnit cu, ClassOrInterfaceDeclaration cls,
                                           String filePath, String packageName, List<String> imports) {
        StringBuilder content = new StringBuilder();

        // Add package and important imports for context
        if (!packageName.isEmpty()) {
            content.append("package ").append(packageName).append(";\n\n");
        }

        imports.stream()
                .filter(this::isImportantImport)
                .forEach(imp -> content.append("import ").append(imp).append(";\n"));

        if (!imports.isEmpty()) {
            content.append("\n");
        }

        // Full class content
        content.append(cls.toString());

        CodeChunk chunk = new CodeChunk(
                generateChunkId(filePath, "class", cls.getNameAsString(), null),
                content.toString(),
                cls.isInterface() ? "INTERFACE" : "CLASS",
                cls.getNameAsString(),
                filePath,
                createMetadataMap(cls, packageName, "FULL_CLASS")
        );

        chunk.setPackageName(packageName);
        chunk.setAnnotations(extractAnnotations(cls));
        chunk.setImports(imports);

        return chunk;
    }

    /**
     * Chunk large classes intelligently by grouping related methods
     */
    private List<CodeChunk> chunkLargeClass(ClassOrInterfaceDeclaration cls, String filePath,
                                            String packageName, List<String> imports) {
        List<CodeChunk> chunks = new ArrayList<>();

        // Group related methods together
        List<MethodGroup> methodGroups = groupRelatedMethods(cls);

        for (MethodGroup group : methodGroups) {
            if (group.isEmpty()) continue;

            String groupCode = buildGroupCode(cls, group, packageName, imports);

            if (groupCode.length() <= maxChunkSize) {
                // Group fits in one chunk
                chunks.add(createMethodGroupChunk(cls, group, groupCode, filePath, packageName));
            } else {
                // Split group into individual methods
                chunks.addAll(createIndividualMethodChunks(cls, group, filePath, packageName));
            }
        }

        // Handle fields separately if they exist
        if (!cls.getFields().isEmpty()) {
            chunks.add(createFieldsChunk(cls, filePath, packageName));
        }

        return chunks;
    }

    /**
     * Group related methods to maintain context
     */
    private List<MethodGroup> groupRelatedMethods(ClassOrInterfaceDeclaration cls) {
        List<MethodGroup> groups = new ArrayList<>();

        MethodGroup restEndpoints = new MethodGroup("REST_ENDPOINTS");
        MethodGroup crudOperations = new MethodGroup("CRUD_OPERATIONS");
        MethodGroup businessLogic = new MethodGroup("BUSINESS_LOGIC");
        MethodGroup accessors = new MethodGroup("ACCESSORS");

        cls.getMethods().forEach(method -> {
            String methodName = method.getNameAsString().toLowerCase();

            if (hasRestAnnotation(method)) {
                restEndpoints.addMethod(method);
            } else if (methodName.startsWith("get") || methodName.startsWith("set") ||
                    methodName.startsWith("is")) {
                accessors.addMethod(method);
            } else if (methodName.contains("save") || methodName.contains("delete") ||
                    methodName.contains("create") || methodName.contains("update") ||
                    methodName.contains("find") || methodName.contains("search")) {
                crudOperations.addMethod(method);
            } else {
                businessLogic.addMethod(method);
            }
        });

        // Add groups in priority order (REST endpoints first, accessors last)
        if (!restEndpoints.isEmpty()) groups.add(restEndpoints);
        if (!businessLogic.isEmpty()) groups.add(businessLogic);
        if (!crudOperations.isEmpty()) groups.add(crudOperations);
        if (!accessors.isEmpty()) groups.add(accessors);

        return groups;
    }

    /**
     * Build code for a method group with context
     */
    private String buildGroupCode(ClassOrInterfaceDeclaration cls, MethodGroup group,
                                  String packageName, List<String> imports) {
        StringBuilder code = new StringBuilder();

        // Package for context
        if (!packageName.isEmpty()) {
            code.append("package ").append(packageName).append(";\n\n");
        }

        // Important imports
        imports.stream()
                .filter(this::isImportantImport)
                .forEach(imp -> code.append("import ").append(imp).append(";\n"));

        if (!imports.isEmpty()) {
            code.append("\n");
        }

        // Class header for context
        code.append("// Class: ").append(cls.getNameAsString()).append("\n");
        code.append("// Group: ").append(group.getType()).append("\n\n");

        // Include important class annotations
        cls.getAnnotations().stream()
                .filter(this::isImportantAnnotation)
                .forEach(ann -> code.append(ann).append("\n"));

        // Class declaration (simplified)
        code.append("public class ").append(cls.getNameAsString()).append(" {\n\n");

        // Relevant fields for this group
        cls.getFields().stream()
                .filter(field -> isRelevantForGroup(field, group))
                .forEach(field -> code.append("    ").append(field).append("\n"));

        if (!cls.getFields().isEmpty()) code.append("\n");

        // Methods in this group
        group.getMethods().forEach(method -> {
            code.append("    ").append(method).append("\n\n");
        });

        code.append("}\n");

        return code.toString();
    }

    /**
     * Create chunk for a method group
     */
    private CodeChunk createMethodGroupChunk(ClassOrInterfaceDeclaration cls, MethodGroup group,
                                             String content, String filePath, String packageName) {
        CodeChunk chunk = new CodeChunk(
                generateChunkId(filePath, group.getType().toLowerCase(), cls.getNameAsString(), null),
                content,
                group.getType(),
                cls.getNameAsString(),
                filePath,
                createMetadataMap(cls, packageName, group.getType())
        );

        chunk.setPackageName(packageName);
        chunk.setAnnotations(extractAnnotations(cls));

        // Add method-specific metadata
        chunk.getMetadata().put("methodCount", String.valueOf(group.getMethods().size()));
        chunk.getMetadata().put("methodNames",
                group.getMethods().stream()
                        .map(MethodDeclaration::getNameAsString)
                        .collect(Collectors.joining(",")));

        return chunk;
    }

    /**
     * Create individual method chunks for very large method groups
     */
    private List<CodeChunk> createIndividualMethodChunks(ClassOrInterfaceDeclaration cls, MethodGroup group,
                                                         String filePath, String packageName) {
        return group.getMethods().stream()
                .map(method -> createMethodChunk(method, cls, filePath, packageName))
                .collect(Collectors.toList());
    }

    /**
     * Create individual method chunks (fallback for method-only strategy)
     */
    private List<CodeChunk> createIndividualMethodChunks(ClassOrInterfaceDeclaration cls,
                                                         String filePath, String packageName) {
        return cls.getMethods().stream()
                .map(method -> createMethodChunk(method, cls, filePath, packageName))
                .collect(Collectors.toList());
    }

    /**
     * Create a single method chunk with context
     */
    private CodeChunk createMethodChunk(MethodDeclaration method, ClassOrInterfaceDeclaration cls,
                                        String filePath, String packageName) {
        StringBuilder content = new StringBuilder();
        content.append("// Class: ").append(cls.getNameAsString()).append("\n");
        content.append("// Package: ").append(packageName).append("\n\n");

        // Include relevant class annotations for context
        cls.getAnnotations().stream()
                .filter(this::isImportantAnnotation)
                .forEach(ann -> content.append("// Class annotation: ").append(ann).append("\n"));

        if (!cls.getAnnotations().isEmpty()) {
            content.append("\n");
        }

        content.append(method.toString());

        CodeChunk chunk = new CodeChunk(
                generateChunkId(filePath, "method", cls.getNameAsString(), method.getNameAsString()),
                content.toString(),
                "METHOD",
                cls.getNameAsString(),
                filePath,
                createMethodMetadata(method, cls, packageName)
        );

        chunk.setPackageName(packageName);
        chunk.setMethodName(method.getNameAsString());
        chunk.setAnnotations(extractAnnotations(method));

        return chunk;
    }

    /**
     * Create fields chunk for classes with many fields
     */
    private CodeChunk createFieldsChunk(ClassOrInterfaceDeclaration cls, String filePath, String packageName) {
        StringBuilder content = new StringBuilder();
        content.append("// Class: ").append(cls.getNameAsString()).append("\n");
        content.append("// Package: ").append(packageName).append("\n");
        content.append("// Fields and Properties\n\n");

        cls.getFields().forEach(field -> {
            content.append(field).append("\n");
        });

        return new CodeChunk(
                generateChunkId(filePath, "fields", cls.getNameAsString(), null),
                content.toString(),
                "FIELDS",
                cls.getNameAsString(),
                filePath,
                createMetadataMap(cls, packageName, "FIELDS")
        );
    }

    // Helper methods
    private boolean hasRestAnnotation(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .anyMatch(ann -> {
                    String name = ann.getNameAsString();
                    return name.contains("Mapping") || name.equals("GET") ||
                            name.equals("POST") || name.equals("PUT") || name.equals("DELETE") ||
                            name.equals("Path");
                });
    }

    private boolean isImportantImport(String importName) {
        return importName.contains("springframework") ||
                importName.contains("javax.persistence") ||
                importName.contains("jakarta.persistence") ||
                importName.contains("javax.ws.rs") ||
                importName.contains("javax.validation") ||
                importName.contains("jakarta.validation");
    }

    private boolean isImportantAnnotation(AnnotationExpr ann) {
        String name = ann.getNameAsString();
        return name.equals("RestController") || name.equals("Controller") ||
                name.equals("Service") || name.equals("Repository") ||
                name.equals("Entity") || name.equals("Component") ||
                name.equals("Configuration") || name.equals("Path");
    }

    private boolean isRelevantForGroup(FieldDeclaration field, MethodGroup group) {
        // Include @Autowired fields for service/controller groups
        // Include @Column/@JoinColumn fields for entity groups
        return field.getAnnotations().stream()
                .anyMatch(ann -> {
                    String name = ann.getNameAsString();
                    return name.equals("Autowired") || name.equals("Column") ||
                            name.equals("JoinColumn") || name.equals("Id") ||
                            name.equals("Value") || name.equals("Qualifier");
                });
    }

    private List<String> extractAnnotations(ClassOrInterfaceDeclaration cls) {
        return cls.getAnnotations().stream()
                .map(AnnotationExpr::toString)
                .collect(Collectors.toList());
    }

    private List<String> extractAnnotations(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .map(AnnotationExpr::toString)
                .collect(Collectors.toList());
    }

    private Map<String, Object> createMetadataMap(ClassOrInterfaceDeclaration cls, String packageName, String chunkType) {
        Map<String, Object> metadata = new HashMap<>();
        List<String> annotations = extractAnnotations(cls);

        // ChromaDB requires string values for metadata, convert all to strings
        metadata.put("chunkType", chunkType != null ? chunkType : "");
        metadata.put("packageName", packageName != null ? packageName : "");
        metadata.put("isController", String.valueOf(hasAnnotation(annotations, "Controller", "RestController")));
        metadata.put("isService", String.valueOf(hasAnnotation(annotations, "Service")));
        metadata.put("isRepository", String.valueOf(hasAnnotation(annotations, "Repository")));
        metadata.put("isComponent", String.valueOf(hasAnnotation(annotations, "Component")));
        metadata.put("isEntity", String.valueOf(hasAnnotation(annotations, "Entity")));
        metadata.put("isConfiguration", String.valueOf(hasAnnotation(annotations, "Configuration")));
        metadata.put("methodCount", String.valueOf(cls.getMethods().size()));
        metadata.put("fieldCount", String.valueOf(cls.getFields().size()));

        return metadata;
    }

    private Map<String, Object> createMethodMetadata(MethodDeclaration method, ClassOrInterfaceDeclaration cls, String packageName) {
        Map<String, Object> metadata = new HashMap<>();
        List<String> annotations = extractAnnotations(method);

        // ChromaDB requires string values for metadata, convert all to strings
        metadata.put("chunkType", "METHOD");
        metadata.put("packageName", packageName != null ? packageName : "");
        metadata.put("isPublic", String.valueOf(method.isPublic()));
        metadata.put("isStatic", String.valueOf(method.isStatic()));
        metadata.put("returnType", method.getTypeAsString());
        metadata.put("parameterCount", String.valueOf(method.getParameters().size()));
        metadata.put("isEndpoint", String.valueOf(hasAnnotation(annotations, "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "GET", "POST", "PUT", "DELETE")));
        metadata.put("isTransactional", String.valueOf(hasAnnotation(annotations, "Transactional")));

        return metadata;
    }

    private boolean hasAnnotation(List<String> annotations, String... annotationNames) {
        return annotations.stream()
                .anyMatch(ann -> Arrays.stream(annotationNames)
                        .anyMatch(name -> ann.contains("@" + name)));
    }

    private String generateChunkId(String filePath, String type, String className, String methodName) {
        StringBuilder id = new StringBuilder();
        id.append(filePath.replace("/", ".").replace(".java", ""));
        id.append(".").append(className);
        if (methodName != null) {
            id.append(".").append(methodName);
        }
        id.append(".").append(type);
        return id.toString();
    }

    private String getRelativePath(Path javaFilePath, String repositoryRoot) {
        return Path.of(repositoryRoot).relativize(javaFilePath).toString();
    }

    /**
     * Build class signature string manually
     */
    private String buildClassSignature(ClassOrInterfaceDeclaration cls) {
        StringBuilder signature = new StringBuilder();

        // Access modifiers
        if (cls.isPublic()) signature.append("public ");
        if (cls.isPrivate()) signature.append("private ");
        if (cls.isProtected()) signature.append("protected ");
        if (cls.isStatic()) signature.append("static ");
        if (cls.isFinal()) signature.append("final ");
        if (cls.isAbstract()) signature.append("abstract ");

        // Class or interface
        if (cls.isInterface()) {
            signature.append("interface ");
        } else {
            signature.append("class ");
        }

        // Class name
        signature.append(cls.getNameAsString());

        // Type parameters (generics)
        if (!cls.getTypeParameters().isEmpty()) {
            signature.append("<");
            signature.append(cls.getTypeParameters().stream()
                    .map(tp -> tp.toString())
                    .collect(Collectors.joining(", ")));
            signature.append(">");
        }

        // Extended classes
        if (!cls.getExtendedTypes().isEmpty()) {
            signature.append(" extends ");
            signature.append(cls.getExtendedTypes().stream()
                    .map(et -> et.toString())
                    .collect(Collectors.joining(", ")));
        }

        // Implemented interfaces
        if (!cls.getImplementedTypes().isEmpty()) {
            signature.append(" implements ");
            signature.append(cls.getImplementedTypes().stream()
                    .map(it -> it.toString())
                    .collect(Collectors.joining(", ")));
        }

        return signature.toString();
    }

    /**
     * Build method signature string manually
     */
    private String buildMethodSignature(MethodDeclaration method) {
        StringBuilder signature = new StringBuilder();

        // Access modifiers
        if (method.isPublic()) signature.append("public ");
        if (method.isPrivate()) signature.append("private ");
        if (method.isProtected()) signature.append("protected ");
        if (method.isStatic()) signature.append("static ");
        if (method.isFinal()) signature.append("final ");
        if (method.isAbstract()) signature.append("abstract ");
        if (method.isSynchronized()) signature.append("synchronized ");

        // Type parameters (generics)
        if (!method.getTypeParameters().isEmpty()) {
            signature.append("<");
            signature.append(method.getTypeParameters().stream()
                    .map(tp -> tp.toString())
                    .collect(Collectors.joining(", ")));
            signature.append("> ");
        }

        // Return type
        signature.append(method.getTypeAsString()).append(" ");

        // Method name
        signature.append(method.getNameAsString());

        // Parameters
        signature.append("(");
        signature.append(method.getParameters().stream()
                .map(param -> param.getTypeAsString() + " " + param.getNameAsString())
                .collect(Collectors.joining(", ")));
        signature.append(")");

        // Thrown exceptions
        if (!method.getThrownExceptions().isEmpty()) {
            signature.append(" throws ");
            signature.append(method.getThrownExceptions().stream()
                    .map(ex -> ex.toString())
                    .collect(Collectors.joining(", ")));
        }

        return signature.toString();
    }

    /**
     * Filter chunks based on criteria (enhanced with new chunk types)
     */
    public List<CodeChunk> filterChunks(List<CodeChunk> chunks, ChunkFilter filter) {
        return chunks.stream()
                .filter(chunk -> {
                    String type = chunk.getType();

                    // Basic type filtering
                    if (filter.includeClasses && "CLASS".equals(type)) return true;
                    if (filter.includeMethods && "METHOD".equals(type)) return true;
                    if (filter.includeInterfaces && "INTERFACE".equals(type)) return true;

                    // New chunk type filtering
                    if (filter.includeMetadata && "CLASS_METADATA".equals(type)) return true;
                    if (filter.includeRestEndpoints && "REST_ENDPOINTS".equals(type)) return true;
                    if (filter.includeBusinessLogic && "BUSINESS_LOGIC".equals(type)) return true;
                    if (filter.includeCrudOperations && "CRUD_OPERATIONS".equals(type)) return true;
                    if (filter.includeAccessors && "ACCESSORS".equals(type)) return true;

                    // Spring components filtering
                    if (filter.onlySpringComponents) {
                        Map<String, Object> metadata = chunk.getMetadata();
                        return Boolean.TRUE.equals(metadata.get("isController")) ||
                                Boolean.TRUE.equals(metadata.get("isService")) ||
                                Boolean.TRUE.equals(metadata.get("isRepository")) ||
                                Boolean.TRUE.equals(metadata.get("isComponent"));
                    }

                    // Content length filtering
                    int contentLength = chunk.getContent().length();
                    if (contentLength < filter.minContentLength || contentLength > filter.maxContentLength) {
                        return false;
                    }

                    return false;
                })
                .collect(Collectors.toList());
    }

    public static class ChunkFilter {
        public boolean includeClasses = true;
        public boolean includeMethods = true;
        public boolean includeInterfaces = true;
        public boolean includeMetadata = true;
        public boolean includeRestEndpoints = true;
        public boolean includeBusinessLogic = true;
        public boolean includeCrudOperations = true;
        public boolean includeAccessors = false;  // Usually not needed for search
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

        public static ChunkFilter endpointsOnly() {
            ChunkFilter filter = new ChunkFilter();
            filter.includeClasses = false;
            filter.includeMethods = false;
            filter.includeInterfaces = false;
            filter.includeBusinessLogic = false;
            filter.includeCrudOperations = false;
            filter.includeAccessors = false;
            filter.includeRestEndpoints = true;
            filter.includeMetadata = true;
            return filter;
        }
    }
}