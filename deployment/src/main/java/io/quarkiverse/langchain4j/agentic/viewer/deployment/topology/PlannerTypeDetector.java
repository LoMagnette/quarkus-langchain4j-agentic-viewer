package io.quarkiverse.langchain4j.agentic.viewer.deployment.topology;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PatternType;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PlannerDesire;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.PlannerInfo;

/**
 * Tells which planner a declarative {@code @PlannerSupplier} method creates (T4.2): its return type is only
 * {@code Planner}, so the method body is read. The first object it constructs that is a planner gives the planner
 * class; the patterns of langchain4j-agentic-patterns (or subclasses of their planners) give the pattern type.
 * Factories and conditional suppliers may hide the class: the planner is then CUSTOM with no class.
 */
public class PlannerTypeDetector {

    static final Map<String, PatternType> PATTERN_PLANNERS = Map.of(
            "dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner", PatternType.GOAP,
            "dev.langchain4j.agentic.patterns.p2p.P2PPlanner", PatternType.P2P,
            "dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner", PatternType.BLACKBOARD,
            "dev.langchain4j.agentic.patterns.bdi.BDIPlanner", PatternType.BDI,
            "dev.langchain4j.agentic.patterns.debate.DebatePlanner", PatternType.DEBATE,
            "dev.langchain4j.agentic.patterns.voting.VotingPlanner", PatternType.VOTING);
    private static final DotName PLANNER = DotName.createSimple("dev.langchain4j.agentic.planner.Planner");

    private final Function<String, InputStream> classBytes;
    private final Predicate<String> isPlanner;
    private final Function<String, PatternType> patternOf;

    /**
     * @param classBytes the bytecode of a class by name, null when unknown
     * @param isPlanner whether a class is a Planner implementation
     * @param patternOf the pattern of a planner class, null for none
     */
    public PlannerTypeDetector(Function<String, InputStream> classBytes, Predicate<String> isPlanner,
            Function<String, PatternType> patternOf) {
        this.classBytes = classBytes;
        this.isPlanner = isPlanner;
        this.patternOf = patternOf;
    }

    /**
     * A detector for the application: class bytes from a class loader, planner implementations and pattern subclasses
     * from the index.
     */
    public static PlannerTypeDetector of(IndexView index, ClassLoader classLoader) {
        return new PlannerTypeDetector(
                name -> classLoader.getResourceAsStream(name.replace('.', '/') + ".class"),
                name -> PATTERN_PLANNERS.containsKey(name)
                        || index.getAllKnownImplementors(PLANNER).stream().anyMatch(c -> c.name().toString().equals(name)),
                name -> patternOf(index, name));
    }

    private static PatternType patternOf(IndexView index, String className) {
        for (String name = className; name != null;) {
            PatternType pattern = PATTERN_PLANNERS.get(name);
            if (pattern != null) {
                return pattern;
            }
            ClassInfo type = index.getClassByName(DotName.createSimple(name));
            name = type == null || type.superName() == null ? null : type.superName().toString();
        }
        return null;
    }

    /**
     * @param supplier {@code Class#method} of the {@code @PlannerSupplier}
     * @return the planner it creates, as far as its bytecode tells
     */
    public PlannerInfo detect(String supplier) {
        Map<String, String> extras = supplier == null ? Map.of() : Map.of("supplier", supplier);
        String plannerClass = supplier == null ? null : plannerClass(supplier);
        PatternType pattern = plannerClass == null ? null : patternOf.apply(plannerClass);
        return new PlannerInfo(plannerClass, pattern == null ? PatternType.CUSTOM : pattern, extras);
    }

    /**
     * @param supplier {@code Class#method} of the {@code @PlannerSupplier} of a BDI planner
     * @param nodeIds the node id of a sub-agent of the planner by its class name, null when it is none
     * @return the desires it declares literally, null when unknown (DesireReader) or when a desire names an agent
     *         that is no sub-agent of the planner (langchain4j rejects it)
     */
    public List<PlannerDesire> desires(String supplier, Function<String, String> nodeIds) {
        DesireReader reader = new DesireReader();
        List<DesireReader.RawDesire> raw = supplier != null && readSupplier(supplier, reader) ? reader.desires() : null;
        if (raw == null) {
            return null;
        }
        List<PlannerDesire> desires = new ArrayList<>();
        for (DesireReader.RawDesire desire : raw) {
            List<String> agents = desire.agentClasses().stream().map(nodeIds).toList();
            if (agents.contains(null)) {
                return null;
            }
            desires.add(new PlannerDesire(desire.name(), desire.priority(), desire.achievableKey(), desire.satisfiedKey(),
                    agents));
        }
        return desires;
    }

    private String plannerClass(String supplier) {
        List<String> constructed = new ArrayList<>();
        boolean read = readSupplier(supplier, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (opcode == Opcodes.NEW) {
                    constructed.add(type.replace('/', '.'));
                }
            }
        });
        return read ? constructed.stream().filter(this::isPlannerOrPattern).findFirst().orElse(null) : null;
    }

    /**
     * Visits the body of a supplier method: no argument, named after {@code Class#method}.
     *
     * @return false when the class could not be read
     */
    private boolean readSupplier(String supplier, MethodVisitor body) {
        int hash = supplier.indexOf('#');
        if (hash < 0) {
            return false;
        }
        String className = supplier.substring(0, hash);
        String methodName = supplier.substring(hash + 1);
        try (InputStream bytes = classBytes.apply(className)) {
            if (bytes == null) {
                return false;
            }
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                        String[] exceptions) {
                    // the supplier takes no argument and returns a planner
                    return name.equals(methodName) && descriptor.startsWith("()") ? body : null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private boolean isPlannerOrPattern(String className) {
        return isPlanner.test(className) || patternOf.apply(className) != null;
    }
}
