package io.quarkiverse.langchain4j.agentic.viewer.deployment.builder;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The dev-mode transform of {@code builder-discovery=transform} (T5.4): after each call of a langchain4j-agentic
 * builder's {@code build()} in an application class, the result is also handed to {@code BuiltSystems.register} with
 * its call site ({@code Class#method:line}). Only the application classes calling a builder are transformed.
 */
public final class BuildCallTransformer {

    static final String REGISTRY = "io/quarkiverse/langchain4j/agentic/viewer/runtime/dev/builder/BuiltSystems";

    private BuildCallTransformer() {
    }

    /** Whether an invocation is a langchain4j-agentic builder's build(), returning the built object. */
    static boolean isAgenticBuild(String owner, String name, String descriptor) {
        return name.equals("build") && descriptor.startsWith("()L") && owner.startsWith(BuilderSystemBeans.AGENTIC_PACKAGE);
    }

    /**
     * @return the application classes calling a builder's build()
     */
    public static List<String> classesToTransform(IndexView applicationIndex, Function<String, InputStream> classBytes) {
        List<String> classes = new ArrayList<>();
        for (ClassInfo type : applicationIndex.getKnownClasses()) {
            String name = type.name().toString();
            if (!BuilderSystemBeans.library(name) && callsBuild(classBytes.apply(name))) {
                classes.add(name);
            }
        }
        return classes.stream().sorted().toList();
    }

    private static boolean callsBuild(InputStream bytes) {
        if (bytes == null) {
            return false;
        }
        boolean[] found = { false };
        try (bytes) {
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                        String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method, String methodDesc,
                                boolean isInterface) {
                            found[0] |= isAgenticBuild(owner, method, methodDesc);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException | RuntimeException e) {
            return false;
        }
        return found[0];
    }

    /**
     * @return the visitor rewriting a class: {@code build()} → {@code build()}, then
     *         {@code BuiltSystems.register(result, "Class#method:line")} on a copy of the result
     */
    public static ClassVisitor visitor(String className, ClassVisitor next) {
        return new ClassVisitor(Opcodes.ASM9, next) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String desc, String signature,
                    String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, methodName, desc, signature, exceptions)) {
                    int line;

                    @Override
                    public void visitLineNumber(int number, Label start) {
                        line = number;
                        super.visitLineNumber(number, start);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String methodDesc,
                            boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, method, methodDesc, isInterface);
                        if (isAgenticBuild(owner, method, methodDesc)) {
                            super.visitInsn(Opcodes.DUP);
                            super.visitLdcInsn(className + "#" + methodName + ":" + line);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, REGISTRY, "register",
                                    "(Ljava/lang/Object;Ljava/lang/String;)V", false);
                        }
                    }
                };
            }
        };
    }
}
