package io.quarkiverse.langchain4j.agentic.viewer.deployment.builder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.function.Function;

import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder.BuilderCandidate;
import io.quarkus.arc.processor.BeanInfo;

/**
 * Tells which beans are agentic systems built with the {@code AgenticServices} builders (T5.1, finding doc-6): producer
 * methods and fields whose bean type is an agent, and producer methods calling a langchain4j-agentic builder's
 * {@code build()} (a typed interface tells nothing by its type). Only their identity is recorded: they are created when
 * the Dev UI asks.
 */
public class BuilderSystemBeans {

    static final Set<String> AGENT_TYPES = Set.of(
            "dev.langchain4j.agentic.UntypedAgent",
            "dev.langchain4j.agentic.supervisor.SupervisorAgent",
            "dev.langchain4j.agentic.planner.AgentInstance");
    static final String AGENTIC_PACKAGE = "dev/langchain4j/agentic/";
    /** langchain4j, quarkus-langchain4j-agentic and this extension: their beans are no application systems. */
    private static final Set<String> LIBRARY_PACKAGES = Set.of("dev.langchain4j.", "io.quarkiverse.langchain4j.agentic.runtime.",
            "io.quarkiverse.langchain4j.agentic.viewer.runtime.");

    private final Function<String, InputStream> classBytes;

    /**
     * @param classBytes the bytecode of a class by name, null when unknown
     */
    public BuilderSystemBeans(Function<String, InputStream> classBytes) {
        this.classBytes = classBytes;
    }

    public static BuilderSystemBeans of(ClassLoader classLoader) {
        return new BuilderSystemBeans(name -> classLoader.getResourceAsStream(name.replace('.', '/') + ".class"));
    }

    public boolean isBuilderSystem(BeanInfo bean) {
        if (!bean.isProducer() || bean.getTarget().isEmpty() || library(declaringClass(bean))) {
            return false;
        }
        if (bean.getTypes().stream().anyMatch(type -> AGENT_TYPES.contains(type.name().toString()))) {
            return true;
        }
        AnnotationTarget target = bean.getTarget().get();
        return target.kind() == AnnotationTarget.Kind.METHOD && callsBuild(target.asMethod())
                && bean.getProviderType().kind() == Type.Kind.CLASS;
    }

    public BuilderCandidate candidate(BeanInfo bean) {
        AnnotationTarget target = bean.getTarget().orElseThrow();
        String member = target.kind() == AnnotationTarget.Kind.METHOD ? target.asMethod().name() : target.asField().name();
        return new BuilderCandidate(bean.getIdentifier(), declaringClass(bean), member, bean.getName(),
                bean.getProviderType().name().toString());
    }

    private static String declaringClass(BeanInfo bean) {
        AnnotationTarget target = bean.getTarget().orElseThrow();
        return (target.kind() == AnnotationTarget.Kind.METHOD ? target.asMethod().declaringClass()
                : target.asField().declaringClass()).name().toString();
    }

    static boolean library(String className) {
        return LIBRARY_PACKAGES.stream().anyMatch(className::startsWith);
    }

    /** Whether the producer's bytecode calls build() on a langchain4j-agentic builder. */
    boolean callsBuild(MethodInfo producer) {
        boolean[] found = { false };
        try (InputStream bytes = classBytes.apply(producer.declaringClass().name().toString())) {
            if (bytes == null) {
                return false;
            }
            String descriptor = producer.descriptor();
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                        String[] exceptions) {
                    if (!name.equals(producer.name()) || !desc.equals(descriptor)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method, String methodDesc,
                                boolean isInterface) {
                            found[0] |= method.equals("build") && methodDesc.startsWith("()")
                                    && owner.startsWith(AGENTIC_PACKAGE);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException | RuntimeException e) {
            return false;
        }
        return found[0];
    }
}
