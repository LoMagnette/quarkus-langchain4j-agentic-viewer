package io.quarkiverse.langchain4j.agentic.viewer.deployment.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.jboss.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.AgenticGraph;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.model.ImperativePattern;
import io.quarkus.deployment.ApplicationArchive;

/**
 * Scanner that uses ASM to detect imperative agent patterns.
 * <p>
 * This scanner looks for usages of:
 * <ul>
 * <li>AgenticServices.agentBuilder(Class)</li>
 * <li>AgenticServices.sequenceBuilder()</li>
 * <li>AgenticServices.parallelBuilder()</li>
 * <li>AgenticServices.conditionalBuilder()</li>
 * <li>AgenticServices.loopBuilder()</li>
 * <li>AgenticServices.createAgenticSystem()</li>
 * </ul>
 */
public class ImperativeAgentScanner {

    private static final Logger LOG = Logger.getLogger(ImperativeAgentScanner.class);

    private static final String AGENTIC_SERVICES_INTERNAL_NAME = "dev/langchain4j/agentic/AgenticServices";
    private static final String AGENTIC_PACKAGE_INTERNAL_PREFIX = "dev/langchain4j/agentic/";
    private static final Set<String> IMPERATIVE_METHODS = Set.of(
            "agentBuilder",
            "sequenceBuilder",
            "parallelBuilder",
            "conditionalBuilder",
            "loopBuilder",
            "createAgenticSystem");

    private static final Map<String, String> METHOD_TO_BUILDER_TYPE = Map.of(
            "agentBuilder", "AGENT",
            "sequenceBuilder", "SEQUENCE",
            "parallelBuilder", "PARALLEL",
            "conditionalBuilder", "CONDITIONAL",
            "loopBuilder", "LOOP",
            "createAgenticSystem", "SYSTEM");


    /**
     * Enhances the graph with imperative pattern detection information.
     *
     * @param graph    the graph to enhance
     * @param archives the application archives to scan
     * @return the enhanced graph with imperative pattern information
     */
    public AgenticGraph enhanceGraph(AgenticGraph graph, Iterable<ApplicationArchive> archives) {
        List<ImperativePattern> patterns = new ArrayList<>();

        int archiveCount = 0;
        for (ApplicationArchive archive : archives) {
            archiveCount++;
            Path root = archive.getChildPath("");
            if (root == null || !Files.isDirectory(root)) {
                LOG.debugf("Skipping archive (root is null or not directory): %s", archive);
                continue;
            }
            LOG.debugf("Scanning archive root: %s", root);

            try (Stream<Path> pathStream = Files.walk(root)) {
                pathStream
                        .filter(path -> path.toString().endsWith(".class"))
                        .forEach(classFile -> scanClassFile(classFile, patterns));
            } catch (IOException | UncheckedIOException e) {
                LOG.warnf("Failed to scan archive: %s", e.getMessage());
            }
        }

        LOG.debugf("Imperative scan complete: scanned %d archives, found %d patterns", archiveCount, patterns.size());

        if (!patterns.isEmpty()) {
            List<String> locations = patterns.stream()
                    .map(ImperativePattern::className)
                    .distinct()
                    .toList();
            return graph.withImperativePatterns(true, locations, patterns);
        }

        return graph;
    }

    /**
     * Scans class paths for imperative patterns.
     *
     * @param classPaths the class paths to scan
     * @return list of class names containing imperative patterns
     */
    public List<String> scanClassPaths(List<Path> classPaths) {
        List<ImperativePattern> patterns = new ArrayList<>();

        for (Path classPath : classPaths) {
            if (!Files.exists(classPath)) {
                continue;
            }

            try (Stream<Path> pathStream = Files.walk(classPath)) {
                pathStream
                        .filter(path -> path.toString().endsWith(".class"))
                        .forEach(classFile -> scanClassFile(classFile, patterns));
            } catch (IOException | UncheckedIOException e) {
                LOG.warnf("Failed to scan class path: %s", e.getMessage());
            }
        }

        return patterns.stream()
                .map(ImperativePattern::className)
                .distinct()
                .toList();
    }

    private void scanClassFile(Path classFile, List<ImperativePattern> patterns) {
        try (InputStream is = Files.newInputStream(classFile)) {
            ClassReader reader = new ClassReader(is);
            ImperativePatternVisitor visitor = new ImperativePatternVisitor();
            reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            patterns.addAll(visitor.getPatterns());
        } catch (IOException e) {
            LOG.debugf("Failed to scan class file %s: %s", classFile, e.getMessage());
        }
    }

    /**
     * ASM class visitor that detects imperative agent patterns.
     */
    private static class ImperativePatternVisitor extends ClassVisitor {

        private String className;
        private final List<ImperativePattern> patterns = new ArrayList<>();

        public ImperativePatternVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            this.className = name.replace('/', '.');
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {
            return new ImperativeMethodVisitor(name);
        }

        public List<ImperativePattern> getPatterns() {
            return patterns;
        }

        /**
         * Method visitor that looks for calls to AgenticServices methods.
         */
        private class ImperativeMethodVisitor extends MethodVisitor {

            private final String methodName;
            private final Set<String> builderTypes = new HashSet<>();
            private final List<String> agentClassRefs = new ArrayList<>();
            // Class constants loaded since the last method call (several for a varargs subAgents(...) call)
            private final List<String> pendingLdcClasses = new ArrayList<>();

            public ImperativeMethodVisitor(String methodName) {
                super(Opcodes.ASM9);
                this.methodName = methodName;
            }

            @Override
            public void visitLdcInsn(Object value) {
                // Track class constants loaded before agentBuilder(Class) or subAgents(...) calls
                if (value instanceof Type) {
                    Type type = (Type) value;
                    if (type.getSort() == Type.OBJECT) {
                        pendingLdcClasses.add(type.getClassName());
                    }
                }
                super.visitLdcInsn(value);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name,
                    String descriptor, boolean isInterface) {

                // Detect AgenticServices.xxxBuilder() calls
                if (opcode == Opcodes.INVOKESTATIC &&
                        AGENTIC_SERVICES_INTERNAL_NAME.equals(owner) &&
                        IMPERATIVE_METHODS.contains(name)) {

                    String builderType = METHOD_TO_BUILDER_TYPE.get(name);
                    if (builderType != null) {
                        builderTypes.add(builderType);
                    }

                    // If this is agentBuilder(Class), try to capture the class reference
                    if ("agentBuilder".equals(name) && !pendingLdcClasses.isEmpty()) {
                        String agentClass = pendingLdcClasses.get(pendingLdcClasses.size() - 1);
                        agentClassRefs.add(agentClass);
                        LOG.debugf("Detected agentBuilder with class: %s", agentClass);
                    }

                    LOG.debugf("Detected imperative pattern in %s.%s: %s()",
                            className, methodName, name);
                }

                // Detect builder.subAgents(Class...) calls - captures class refs passed to builders
                if ("subAgents".equals(name) && owner.startsWith(AGENTIC_PACKAGE_INTERNAL_PREFIX)
                        && !pendingLdcClasses.isEmpty()) {
                    agentClassRefs.addAll(pendingLdcClasses);
                    LOG.debugf("Detected builder.subAgents() with classes: %s in %s.%s",
                            pendingLdcClasses, className, methodName);
                }

                // Reset after any method call
                pendingLdcClasses.clear();
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }

            @Override
            public void visitEnd() {
                if (!builderTypes.isEmpty()) {
                    patterns.add(new ImperativePattern(
                            className,
                            methodName,
                            new ArrayList<>(builderTypes),
                            new ArrayList<>(agentClassRefs)));
                }
                super.visitEnd();
            }
        }
    }
}
