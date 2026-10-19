package io.quarkiverse.langchain4j.agentic.viewer.deployment.topology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Reads the desires a {@code @PlannerSupplier} method gives a BDI planner (langchain4j-agentic-patterns), when it
 * declares them with literals: {@code Desire.of("name", 10, "achievableKey", "satisfiedKey", Agent.class, ...)}, the
 * same with predicates (conditions then unknown), or {@code new Desire("name", 10, predicate, predicate,
 * List.of(Agent.class, ...))}. A small simulation of the operand stack follows constants, class literals, class arrays
 * and {@code List.of} of class literals; anything else is an unknown value. Desires built elsewhere (a helper method, a
 * field) are not seen: the result is then {@link #desires()} null.
 */
class DesireReader extends MethodVisitor {

    static final String DESIRE = "dev/langchain4j/agentic/patterns/bdi/Desire";

    /** A desire as read: agents by class name. */
    record RawDesire(String name, Integer priority, String achievableKey, String satisfiedKey, List<String> agentClasses) {
    }

    private static final Object UNKNOWN = new Object();

    /** A class literal. */
    private record ClassRef(String className) {
    }

    /** An array or list of class literals, null entries while unknown. */
    private record Classes(String[] classNames) {
        List<String> complete() {
            return Arrays.stream(classNames).anyMatch(name -> name == null) ? null : List.of(classNames);
        }
    }

    private final Deque<Object> stack = new ArrayDeque<>();
    private final List<RawDesire> desires = new ArrayList<>();
    private boolean incomplete;

    DesireReader() {
        super(Opcodes.ASM9);
    }

    /**
     * @return the desires created by the method, in creation order; null when it creates none or one could not be read
     */
    List<RawDesire> desires() {
        return incomplete || desires.isEmpty() ? null : List.copyOf(desires);
    }

    private Object pop() {
        return stack.isEmpty() ? UNKNOWN : stack.pop();
    }

    private void pop(int count) {
        for (int i = 0; i < count; i++) {
            pop();
        }
    }

    /** Stops following values: what is on the stack is no longer known. */
    private void lose() {
        stack.clear();
    }

    @Override
    public void visitLdcInsn(Object value) {
        stack.push(value instanceof Type type && type.getSort() == Type.OBJECT ? new ClassRef(type.getClassName())
                : value instanceof String || value instanceof Integer ? value : UNKNOWN);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
            stack.push(operand);
        } else {
            lose();
        }
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
            stack.push(opcode - Opcodes.ICONST_0);
        } else if (opcode == Opcodes.ACONST_NULL) {
            stack.push(UNKNOWN);
        } else if (opcode == Opcodes.DUP) {
            stack.push(stack.isEmpty() ? UNKNOWN : stack.peek());
        } else if (opcode == Opcodes.AASTORE) {
            Object value = pop();
            Object index = pop();
            Object array = pop();
            if (array instanceof Classes classes && index instanceof Integer i && i >= 0 && i < classes.classNames().length
                    && value instanceof ClassRef ref) {
                classes.classNames()[i] = ref.className();
            }
        } else if (opcode == Opcodes.POP) {
            pop();
        } else {
            lose();
        }
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
        if (opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD) {
            stack.push(UNKNOWN);
        } else if (opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE) {
            pop();
        } else {
            lose();
        }
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (opcode == Opcodes.ANEWARRAY) {
            Object size = pop();
            stack.push(type.equals("java/lang/Class") && size instanceof Integer n && n >= 0 ? new Classes(new String[n])
                    : UNKNOWN);
        } else if (opcode == Opcodes.NEW) {
            stack.push(UNKNOWN);
        } else if (opcode == Opcodes.CHECKCAST) {
            // the value stays
        } else {
            lose();
        }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (opcode == Opcodes.GETSTATIC) {
            stack.push(UNKNOWN);
        } else {
            lose();
        }
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
            Object... bootstrapMethodArguments) {
        // a lambda (e.g. a desire condition): its captured values are consumed, the function is an unknown value
        pop(Type.getArgumentTypes(descriptor).length);
        stack.push(UNKNOWN);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        Type[] arguments = Type.getArgumentTypes(descriptor);
        Object[] values = new Object[arguments.length];
        for (int i = arguments.length - 1; i >= 0; i--) {
            values[i] = pop();
        }
        if (opcode != Opcodes.INVOKESTATIC) {
            pop(); // the receiver; for a constructor, the copy DUP made stays as the new object
        }
        if (owner.equals(DESIRE) && (name.equals("of") || name.equals("<init>")) && arguments.length == 5) {
            read(values);
        } else if (owner.equals("java/util/List") && name.equals("of") && opcode == Opcodes.INVOKESTATIC) {
            stack.push(classList(values));
            return;
        } else if (owner.equals("java/util/Arrays") && name.equals("asList") && values.length == 1
                && values[0] instanceof Classes classes) {
            stack.push(classes);
            return;
        }
        if (Type.getReturnType(descriptor).getSort() != Type.VOID) {
            stack.push(UNKNOWN);
        }
    }

    /** {@code List.of(a, b, ...)} of class literals, or of an array of them (varargs). */
    private static Object classList(Object[] values) {
        if (values.length == 1 && values[0] instanceof Classes classes) {
            return classes;
        }
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            if (!(values[i] instanceof ClassRef ref)) {
                return UNKNOWN;
            }
            names[i] = ref.className();
        }
        return new Classes(names);
    }

    /** Desire(name, priority, achievable, satisfied, agents): keys when the conditions are strings. */
    private void read(Object[] values) {
        List<String> agents = values[4] instanceof Classes classes ? classes.complete() : null;
        if (!(values[0] instanceof String name) || agents == null) {
            incomplete = true;
            return;
        }
        desires.add(new RawDesire(name, values[1] instanceof Integer priority ? priority : null,
                values[2] instanceof String key ? key : null, values[3] instanceof String key ? key : null, agents));
    }

    @Override
    public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
        lose();
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, org.objectweb.asm.Label dflt, org.objectweb.asm.Label... labels) {
        lose();
    }

    @Override
    public void visitLookupSwitchInsn(org.objectweb.asm.Label dflt, int[] keys, org.objectweb.asm.Label[] labels) {
        lose();
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        lose();
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        // no stack effect
    }
}
