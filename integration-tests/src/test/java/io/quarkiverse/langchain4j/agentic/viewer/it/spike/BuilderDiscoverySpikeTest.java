package io.quarkiverse.langchain4j.agentic.viewer.it.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkiverse.langchain4j.agentic.viewer.it.builder.BuilderStoryCreator;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableBean;
import io.quarkus.test.junit.QuarkusTest;

/**
 * T0.6 spike (TASK-11): which builder samples each discovery strategy finds. CDI: the beans the container knows,
 * judged by their types without creating them. Transform: the call sites of a builder's {@code build()} in application
 * code, which a dev-mode transform would wrap (found here by a static scan of the same bytecode).
 */
@QuarkusTest
class BuilderDiscoverySpikeTest {

    private static final String AGENTIC_PACKAGE = "dev/langchain4j/agentic/";
    private static final String PRODUCES = "Ljakarta/enterprise/inject/Produces;";

    /** Beans whose types tell an agentic system, without creating them; the declarative agents (synthetic) left out. */
    private static Set<String> cdiCandidates() {
        Set<String> found = new TreeSet<>();
        for (Bean<?> bean : Arc.container().beanManager().getBeans(Object.class, Any.Literal.INSTANCE)) {
            InjectableBean<?> injectable = (InjectableBean<?>) bean;
            if (injectable.getKind() == InjectableBean.Kind.SYNTHETIC) {
                continue; // quarkus-langchain4j-agentic's beans of the declarative agent interfaces
            }
            boolean agentType = bean.getTypes().stream().anyMatch(type -> type instanceof Class<?> c
                    && (UntypedAgent.class.isAssignableFrom(c) || AgentInstance.class.isAssignableFrom(c)));
            if (agentType) {
                found.add(injectable.getKind() + " " + injectable.getBeanClass().getSimpleName() + " "
                        + bean.getTypes().stream().filter(t -> t != Object.class).map(t -> t.getTypeName()
                                .substring(t.getTypeName().lastIndexOf('.') + 1)).sorted().toList());
            }
        }
        return found;
    }

    record CallSite(String className, String method, int line, String builder, boolean producer) {
        @Override
        public String toString() {
            return className.substring(className.lastIndexOf('/') + 1) + "#" + method + ":" + line + " " + builder
                    + (producer ? " (producer)" : "");
        }
    }

    /** Every call of a langchain4j-agentic builder's build() in the application classes. */
    private static List<CallSite> buildCallSites() throws Exception {
        Path classes = Path.of(BuilderStoryCreator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<CallSite> sites = new ArrayList<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                try (InputStream bytes = Files.newInputStream(file)) {
                    scan(bytes, sites);
                }
            }
        }
        return sites;
    }

    private static void scan(InputStream bytes, List<CallSite> sites) throws IOException {
        ClassReader reader = new ClassReader(bytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                List<CallSite> inMethod = new ArrayList<>();
                boolean[] producer = { false };
                return new MethodVisitor(Opcodes.ASM9) {
                    int line;

                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        producer[0] |= PRODUCES.equals(annotation);
                        return null;
                    }

                    @Override
                    public void visitLineNumber(int number, Label start) {
                        line = number;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                        if (method.equals("build") && desc.startsWith("()") && owner.startsWith(AGENTIC_PACKAGE)) {
                            inMethod.add(new CallSite(reader.getClassName(), name, line,
                                    owner.substring(owner.lastIndexOf('/') + 1), false));
                        }
                    }

                    @Override
                    public void visitEnd() {
                        inMethod.forEach(site -> sites.add(new CallSite(site.className(), site.method(), site.line(),
                                site.builder(), producer[0])));
                    }
                };
            }
        }, 0);
    }

    @Test
    void cdiFindsOnlyBeansTypedAsAgents() {
        Set<String> found = cdiCandidates();
        System.out.println("CDI candidates:\n  " + String.join("\n  ", found));

        // the UntypedAgent bean is found by its type; the typed interface bean (BuilderStoryWriter) is not: nothing in
        // its types tells an agent before the bean is created; the field and per-request systems are no beans at all
        assertEquals(Set.of("PRODUCER_METHOD BuilderDraftReviewer [AgenticScopeAccess, UntypedAgent]",
                "PRODUCER_METHOD BuilderStoryCreator [AgenticScopeAccess, UntypedAgent]"), found);
    }

    @Test
    void transformSeesEveryBuildCallSite() throws Exception {
        List<CallSite> sites = buildCallSites();
        System.out.println("build() call sites:\n  " + String.join("\n  ", sites.stream().map(CallSite::toString).toList()));

        Set<String> methods = new TreeSet<>();
        sites.forEach(site -> methods.add(site.className().substring(site.className().lastIndexOf('/') + 1) + "#"
                + site.method()));
        // bean (UntypedAgent and typed), not a bean, per request: all four builder samples
        assertTrue(methods.containsAll(Set.of("BuilderStoryCreator#storyCreator", "BuilderStoryWriter$Producer#storyWriter",
                "ResearchService#build", "ExpertResource#ask")), methods::toString);
        // a nested system is built too (the loop of BuilderStoryCreator): the registry must keep roots only
        assertEquals(2, sites.stream().filter(site -> site.className().endsWith("/BuilderStoryCreator")).count());
        // producers are recognisable: a build-time scan of producer bodies would let CDI find typed beans too
        assertTrue(sites.stream().filter(CallSite::producer).map(CallSite::method).toList()
                .containsAll(List.of("storyCreator", "styleReviewLoop", "storyWriter")));
    }
}
