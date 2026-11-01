package com.context_platform.aether.workflow.service;

import com.context_platform.aether.workflow.model.CodeDependencyRecord;
import com.context_platform.aether.workflow.model.CodeSymbolRecord;
import com.context_platform.aether.workflow.model.dto.ChangeImpactResult;
import com.context_platform.aether.workflow.repository.CodeDependencyRepository;
import com.context_platform.aether.workflow.repository.CodeSymbolRepository;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(CodeIntelligenceService.class);

    private final CodeSymbolRepository symbolRepository;
    private final CodeDependencyRepository dependencyRepository;

    private static final Pattern TS_EXPORT_PATTERN = Pattern.compile("(?m)^\\s*export\\s+(class|function|interface|type|const)\\s+([A-Za-z0-9_]+)");
    private static final Pattern TS_IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+.*?from\\s+['\"](.*?)['\"]");

    public CodeIntelligenceService(CodeSymbolRepository symbolRepository, CodeDependencyRepository dependencyRepository) {
        this.symbolRepository = symbolRepository;
        this.dependencyRepository = dependencyRepository;
    }

    public void indexSource(UUID projectId, String filePath, String content) {
        if (filePath.endsWith(".java")) {
            indexJava(projectId, filePath, content);
        } else if (filePath.endsWith(".ts") || filePath.endsWith(".tsx") || filePath.endsWith(".js") || filePath.endsWith(".jsx")) {
            indexTypeScript(projectId, filePath, content);
        } else if (filePath.endsWith("pom.xml")) {
            indexPom(projectId, filePath, content);
        }
    }

    private void indexJava(UUID projectId, String filePath, String content) {
        try {
            symbolRepository.deleteByFilePath(projectId, filePath);
            dependencyRepository.deleteBySourcePath(projectId, filePath);

            CompilationUnit cu = StaticJavaParser.parse(content);

            // 1. Classes and Methods
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid -> {
                String className = cid.getNameAsString();
                int start = cid.getRange().map(r -> r.begin.line).orElse(1);
                int end = cid.getRange().map(r -> r.end.line).orElse(start);

                symbolRepository.save(new CodeSymbolRecord(
                        null, projectId, filePath, "JAVA", className,
                        cid.isInterface() ? "INTERFACE" : "CLASS",
                        (cid.isInterface() ? "interface " : "class ") + className,
                        start, end, null
                ));

                for (MethodDeclaration md : cid.getMethods()) {
                    int mStart = md.getRange().map(r -> r.begin.line).orElse(start);
                    int mEnd = md.getRange().map(r -> r.end.line).orElse(mStart);
                    symbolRepository.save(new CodeSymbolRecord(
                            null, projectId, filePath, "JAVA", className + "." + md.getNameAsString(),
                            "METHOD",
                            md.getDeclarationAsString(true, true, true),
                            mStart, mEnd, null
                    ));
                }
            });

            // 2. Imports -> Dependencies
            cu.getImports().forEach(imp -> {
                String imported = imp.getNameAsString();
                dependencyRepository.save(new CodeDependencyRecord(
                        null, projectId, filePath, imported, "IMPORT", "HIGH", Map.of("isStatic", imp.isStatic()), null
                ));
            });

            // 3. Method Calls -> Dependencies
            cu.findAll(MethodCallExpr.class).forEach(call -> {
                String callName = call.getNameAsString();
                dependencyRepository.save(new CodeDependencyRecord(
                        null, projectId, filePath, callName, "API_CALL", "MEDIUM", Map.of("scope", call.getScope().map(Object::toString).orElse("")), null
                ));
            });

        } catch (Exception e) {
            log.warn("Failed to parse Java file: {}", filePath, e);
        }
    }

    private void indexTypeScript(UUID projectId, String filePath, String content) {
        try {
            symbolRepository.deleteByFilePath(projectId, filePath);
            dependencyRepository.deleteBySourcePath(projectId, filePath);

            // Match export statements
            Matcher mExport = TS_EXPORT_PATTERN.matcher(content);
            while (mExport.find()) {
                String kind = mExport.group(1).toUpperCase();
                String name = mExport.group(2);
                int line = countLines(content.substring(0, mExport.start())) + 1;

                symbolRepository.save(new CodeSymbolRecord(
                        null, projectId, filePath, "TYPESCRIPT", name, kind, "export " + kind.toLowerCase() + " " + name, line, line + 10, null
                ));
            }

            // Match import statements
            Matcher mImport = TS_IMPORT_PATTERN.matcher(content);
            while (mImport.find()) {
                String target = mImport.group(1);
                dependencyRepository.save(new CodeDependencyRecord(
                        null, projectId, filePath, target, "IMPORT", "HIGH", Map.of(), null
                ));
            }

        } catch (Exception e) {
            log.warn("Failed to parse TypeScript file: {}", filePath, e);
        }
    }

    private void indexPom(UUID projectId, String filePath, String content) {
        try {
            dependencyRepository.deleteBySourcePath(projectId, filePath);
            Pattern depPattern = Pattern.compile("<groupId>(.*?)</groupId>\\s*<artifactId>(.*?)</artifactId>");
            Matcher m = depPattern.matcher(content);
            while (m.find()) {
                String dep = m.group(1) + ":" + m.group(2);
                dependencyRepository.save(new CodeDependencyRecord(
                        null, projectId, filePath, dep, "PACKAGE_DEPENDENCY", "HIGH", Map.of(), null
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to parse POM: {}", filePath, e);
        }
    }

    public ChangeImpactResult analyzeChangeImpact(UUID projectId, List<String> modifiedFiles) {
        Set<String> affectedModules = new LinkedHashSet<>();
        Set<String> affectedApis = new LinkedHashSet<>();
        Set<String> affectedTests = new LinkedHashSet<>();
        Set<String> affectedTasks = new LinkedHashSet<>();
        Map<String, String> confidenceLevels = new LinkedHashMap<>();
        List<String> reasoningEdges = new ArrayList<>();

        List<CodeDependencyRecord> allDeps = dependencyRepository.findAllForProject(projectId);

        for (String file : modifiedFiles) {
            String baseName = getBaseName(file);

            // Direct imports or references
            for (CodeDependencyRecord dep : allDeps) {
                if (dep.targetPath().contains(baseName) || dep.targetPath().equalsIgnoreCase(file)) {
                    String caller = dep.sourcePath();
                    reasoningEdges.add(caller + " imports " + file + " (" + dep.dependencyType() + ")");

                    if (caller.contains("Test") || caller.contains("test/")) {
                        affectedTests.add(caller);
                        confidenceLevels.put(caller, "HIGH");
                    } else if (caller.contains("Controller") || caller.contains("Resource") || caller.contains("Endpoint")) {
                        affectedApis.add(caller);
                        confidenceLevels.put(caller, "HIGH");
                    } else {
                        affectedModules.add(getModuleName(caller));
                        confidenceLevels.put(caller, "HIGH");
                    }

                    // 2nd degree callers
                    for (CodeDependencyRecord secondDep : allDeps) {
                        String secondBase = getBaseName(caller);
                        if (secondDep.targetPath().contains(secondBase)) {
                            String secondCaller = secondDep.sourcePath();
                            reasoningEdges.add(secondCaller + " calls " + caller + " (TRANSITIVE)");
                            if (secondCaller.contains("Test")) {
                                affectedTests.add(secondCaller);
                                confidenceLevels.put(secondCaller, "MEDIUM");
                            } else {
                                affectedModules.add(getModuleName(secondCaller));
                                confidenceLevels.put(secondCaller, "MEDIUM");
                            }
                        }
                    }
                }
            }
        }

        // Add heuristic label for test files having same base name
        for (String file : modifiedFiles) {
            String base = getBaseName(file);
            String heuristicTest = base + "Test.java";
            if (!affectedTests.contains(heuristicTest)) {
                affectedTests.add(heuristicTest);
                confidenceLevels.put(heuristicTest, "HEURISTIC");
                reasoningEdges.add(heuristicTest + " matched by naming convention (HEURISTIC)");
            }
        }

        return new ChangeImpactResult(
                modifiedFiles,
                new ArrayList<>(affectedModules),
                new ArrayList<>(affectedApis),
                new ArrayList<>(affectedTests),
                new ArrayList<>(affectedTasks),
                confidenceLevels,
                reasoningEdges
        );
    }

    public List<String> getRelevantTests(UUID projectId, List<String> modifiedFiles) {
        ChangeImpactResult impact = analyzeChangeImpact(projectId, modifiedFiles);
        return impact.affectedTests();
    }

    private static int countLines(String str) {
        return str.split("\r\n|\r|\n", -1).length;
    }

    private static String getBaseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.indexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private static String getModuleName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(0, slash) : path;
    }
}
