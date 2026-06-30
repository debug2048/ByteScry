package com.github.bytescry.simple;

import com.github.bytescry.model.DecompilerOptions;
import com.github.bytescry.util.TypeUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Emits Java-like source code for a single method using a simple stack machine.
 *
 * Supports a limited subset of bytecode: local variables, constants, arithmetic,
 * simple method invocations, field access, basic if/else and while loops.
 */
public class MethodEmitter {

    private final MethodNode methodNode;
    private final List<String> argNames;
    private final boolean isStatic;
    private final Map<Integer, String> localNames = new HashMap<>();

    private final Set<BasicBlock> visited = new HashSet<>();
    private final StringBuilder output = new StringBuilder();
    private final Deque<String> stack = new ArrayDeque<>();

    public MethodEmitter(MethodNode methodNode, List<String> argNames, boolean isStatic) {
        this.methodNode = methodNode;
        this.argNames = argNames;
        this.isStatic = isStatic;
        buildLocalNames();
    }

    private void buildLocalNames() {
        int slot = isStatic ? 0 : 1;
        for (int i = 0; i < argNames.size(); i++) {
            localNames.put(slot, argNames.get(i));
            slot += Type.getArgumentTypes(methodNode.desc)[i].getSize();
        }
        if (methodNode.localVariables != null) {
            for (LocalVariableNode lv : methodNode.localVariables) {
                if (!localNames.containsKey(lv.index)) {
                    localNames.put(lv.index, lv.name);
                }
            }
        }
    }

    public String emit(ControlFlowGraph cfg, DecompilerOptions options) {
        try {
            emitBlock(cfg.getEntry(), 1);
        } catch (UnsupportedOperationException e) {
            return "        // Simple engine limitation: " + e.getMessage() + "\n" +
                    "        throw new RuntimeException(\"Method body omitted by simple engine\");\n";
        }
        return output.toString();
    }

    private void emitBlock(BasicBlock block, int indent) {
        if (visited.contains(block)) {
            return;
        }
        visited.add(block);

        for (AbstractInsnNode insn : block.getInstructions()) {
            if (insn.getType() == AbstractInsnNode.LABEL || insn.getType() == AbstractInsnNode.LINE || insn.getType() == AbstractInsnNode.FRAME) {
                continue;
            }
            emitInstruction(insn, indent);
        }

        // Handle successors
        List<BasicBlock> succ = block.getSuccessors();
        if (succ.isEmpty()) {
            return;
        }
        if (succ.size() == 1) {
            BasicBlock next = succ.get(0);
            // Detect back-edge (while loop)
            if (visited.contains(next) && next.getId() < block.getId()) {
                // This is a simplified detection; real while loop reconstruction needs more work
                return;
            }
            emitBlock(next, indent);
        } else {
            // Conditional: first successor is target, second is fallthrough
            BasicBlock target = succ.get(0);
            BasicBlock fallthrough = succ.get(1);
            emitConditional(block, target, fallthrough, indent);
        }
    }

    private void emitConditional(BasicBlock block, BasicBlock target, BasicBlock fallthrough, int indent) {
        AbstractInsnNode last = block.getLastInstruction();
        if (last == null || last.getType() != AbstractInsnNode.JUMP_INSN) {
            throw new UnsupportedOperationException("unexpected conditional structure");
        }
        JumpInsnNode jump = (JumpInsnNode) last;
        String condition = conditionForJump(jump.getOpcode());

        appendIndent(indent);
        output.append("if (").append(condition).append(") {\n");
        emitBlock(target, indent + 1);
        appendIndent(indent);
        output.append("}\n");

        if (!visited.contains(fallthrough)) {
            emitBlock(fallthrough, indent);
        }
    }

    private String conditionForJump(int opcode) {
        return switch (opcode) {
            case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
                    Opcodes.IFNULL, Opcodes.IFNONNULL -> conditionForUnaryJump(opcode, pop());
            case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE,
                    Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> {
                String right = pop();
                String left = pop();
                yield conditionForBinaryJump(opcode, left, right);
            }
            default -> throw new UnsupportedOperationException("unsupported jump opcode " + opcode);
        };
    }

    private String conditionForUnaryJump(int opcode, String expr) {
        return switch (opcode) {
            case Opcodes.IFEQ -> "!" + parenthesize(expr);
            case Opcodes.IFNE -> parenthesize(expr);
            case Opcodes.IFLT -> expr + " < 0";
            case Opcodes.IFGE -> expr + " >= 0";
            case Opcodes.IFGT -> expr + " > 0";
            case Opcodes.IFLE -> expr + " <= 0";
            case Opcodes.IFNULL -> expr + " == null";
            case Opcodes.IFNONNULL -> expr + " != null";
            default -> throw new UnsupportedOperationException("unsupported jump opcode " + opcode);
        };
    }

    private String conditionForBinaryJump(int opcode, String left, String right) {
        String rhs = parenthesize(right);
        return switch (opcode) {
            case Opcodes.IF_ICMPEQ, Opcodes.IF_ACMPEQ -> left + " == " + rhs;
            case Opcodes.IF_ICMPNE, Opcodes.IF_ACMPNE -> left + " != " + rhs;
            case Opcodes.IF_ICMPLT -> left + " < " + rhs;
            case Opcodes.IF_ICMPGE -> left + " >= " + rhs;
            case Opcodes.IF_ICMPGT -> left + " > " + rhs;
            case Opcodes.IF_ICMPLE -> left + " <= " + rhs;
            default -> throw new UnsupportedOperationException("unsupported jump opcode " + opcode);
        };
    }

    private String parenthesize(String expr) {
        if (expr.contains(" ")) {
            return "(" + expr + ")";
        }
        return expr;
    }

    private void emitInstruction(AbstractInsnNode insn, int indent) {
        int opcode = insn.getOpcode();
        switch (insn.getType()) {
            case AbstractInsnNode.INSN -> emitInsn((InsnNode) insn, indent);
            case AbstractInsnNode.INT_INSN -> emitIntInsn((IntInsnNode) insn, indent);
            case AbstractInsnNode.VAR_INSN -> emitVarInsn((VarInsnNode) insn, indent);
            case AbstractInsnNode.TYPE_INSN -> emitTypeInsn((TypeInsnNode) insn, indent);
            case AbstractInsnNode.FIELD_INSN -> emitFieldInsn((FieldInsnNode) insn, indent);
            case AbstractInsnNode.METHOD_INSN -> emitMethodInsn((MethodInsnNode) insn, indent);
            case AbstractInsnNode.JUMP_INSN -> {
                // handled at block level for conditionals
                if (opcode == Opcodes.GOTO) {
                    // fallthrough handled by CFG; nothing to emit
                }
            }
            case AbstractInsnNode.LDC_INSN -> {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                push(constantToString(ldc.cst));
            }
            case AbstractInsnNode.IINC_INSN -> {
                IincInsnNode iinc = (IincInsnNode) insn;
                appendIndent(indent);
                output.append(localName(iinc.var)).append(" += ").append(iinc.incr).append(";\n");
            }
            case AbstractInsnNode.TABLESWITCH_INSN, AbstractInsnNode.LOOKUPSWITCH_INSN ->
                    throw new UnsupportedOperationException("switch not supported by simple engine");
            default -> {
                if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                    appendIndent(indent);
                    if (opcode == Opcodes.RETURN) {
                        output.append("return;\n");
                    } else {
                        output.append("return ").append(pop()).append(";\n");
                    }
                } else {
                    throw new UnsupportedOperationException("unsupported instruction type: " + insn.getClass().getSimpleName());
                }
            }
        }
    }

    private void emitInsn(InsnNode insn, int indent) {
        switch (insn.getOpcode()) {
            case Opcodes.ICONST_M1 -> push("-1");
            case Opcodes.ICONST_0 -> push("0");
            case Opcodes.ICONST_1 -> push("1");
            case Opcodes.ICONST_2 -> push("2");
            case Opcodes.ICONST_3 -> push("3");
            case Opcodes.ICONST_4 -> push("4");
            case Opcodes.ICONST_5 -> push("5");
            case Opcodes.LCONST_0 -> push("0L");
            case Opcodes.LCONST_1 -> push("1L");
            case Opcodes.FCONST_0 -> push("0.0f");
            case Opcodes.FCONST_1 -> push("1.0f");
            case Opcodes.FCONST_2 -> push("2.0f");
            case Opcodes.DCONST_0 -> push("0.0");
            case Opcodes.DCONST_1 -> push("1.0");
            case Opcodes.ACONST_NULL -> push("null");
            case Opcodes.IADD -> push("(" + pop() + " + " + pop() + ")");
            case Opcodes.ISUB -> {
                String b = pop();
                String a = pop();
                push("(" + a + " - " + b + ")");
            }
            case Opcodes.IMUL -> push("(" + pop() + " * " + pop() + ")");
            case Opcodes.IDIV -> {
                String b = pop();
                String a = pop();
                push("(" + a + " / " + b + ")");
            }
            case Opcodes.IREM -> {
                String b = pop();
                String a = pop();
                push("(" + a + " % " + b + ")");
            }
            case Opcodes.INEG -> push("(-" + pop() + ")");
            case Opcodes.LADD, Opcodes.FADD, Opcodes.DADD -> push("(" + pop() + " + " + pop() + ")");
            case Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB -> {
                String b = pop();
                String a = pop();
                push("(" + a + " - " + b + ")");
            }
            case Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL -> push("(" + pop() + " * " + pop() + ")");
            case Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV -> {
                String b = pop();
                String a = pop();
                push("(" + a + " / " + b + ")");
            }
            case Opcodes.IAND -> push("(" + pop() + " & " + pop() + ")");
            case Opcodes.IOR -> push("(" + pop() + " | " + pop() + ")");
            case Opcodes.IXOR -> push("(" + pop() + " ^ " + pop() + ")");
            case Opcodes.ISHL -> {
                String b = pop();
                String a = pop();
                push("(" + a + " << " + b + ")");
            }
            case Opcodes.ISHR -> {
                String b = pop();
                String a = pop();
                push("(" + a + " >> " + b + ")");
            }
            case Opcodes.IUSHR -> {
                String b = pop();
                String a = pop();
                push("(" + a + " >>> " + b + ")");
            }
            case Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG ->
                    push("(" + pop() + " <compare> " + pop() + ")");
            case Opcodes.POP -> pop();
            case Opcodes.DUP -> {
                String v = peek();
                push(v);
            }
            case Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> {
                appendIndent(indent);
                output.append("return ").append(pop()).append(";\n");
            }
            case Opcodes.RETURN -> {
                appendIndent(indent);
                output.append("return;\n");
            }
            default -> throw new UnsupportedOperationException("unsupported opcode " + insn.getOpcode());
        }
    }

    private void emitIntInsn(IntInsnNode insn, int indent) {
        switch (insn.getOpcode()) {
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> push(String.valueOf(insn.operand));
            case Opcodes.NEWARRAY -> {
                String size = pop();
                push("new " + arrayTypeName(insn.operand) + "[" + size + "]");
            }
            default -> throw new UnsupportedOperationException("unsupported int insn " + insn.getOpcode());
        }
    }

    private void emitVarInsn(VarInsnNode insn, int indent) {
        int opcode = insn.getOpcode();
        if (opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD) {
            push(localName(insn.var));
        } else if (opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE) {
            String value = pop();
            appendIndent(indent);
            output.append(localName(insn.var)).append(" = ").append(value).append(";\n");
        } else {
            throw new UnsupportedOperationException("unsupported var insn " + opcode);
        }
    }

    private void emitTypeInsn(TypeInsnNode insn, int indent) {
        switch (insn.getOpcode()) {
            case Opcodes.NEW -> push("new " + TypeUtils.internalToQualified(insn.desc));
            case Opcodes.ANEWARRAY -> {
                String size = pop();
                push("new " + TypeUtils.internalToQualified(insn.desc) + "[" + size + "]");
            }
            case Opcodes.CHECKCAST -> push("((" + TypeUtils.internalToQualified(insn.desc) + ") " + pop() + ")");
            case Opcodes.INSTANCEOF -> push("(" + pop() + " instanceof " + TypeUtils.internalToQualified(insn.desc) + ")");
            default -> throw new UnsupportedOperationException("unsupported type insn " + insn.getOpcode());
        }
    }

    private void emitFieldInsn(FieldInsnNode insn, int indent) {
        String owner = TypeUtils.internalToQualified(insn.owner);
        String field = insn.name;
        if (insn.getOpcode() == Opcodes.GETFIELD) {
            String obj = pop();
            push(obj + "." + field);
        } else if (insn.getOpcode() == Opcodes.GETSTATIC) {
            push(owner + "." + field);
        } else if (insn.getOpcode() == Opcodes.PUTFIELD) {
            String value = pop();
            String obj = pop();
            appendIndent(indent);
            output.append(obj).append(".").append(field).append(" = ").append(value).append(";\n");
        } else if (insn.getOpcode() == Opcodes.PUTSTATIC) {
            String value = pop();
            appendIndent(indent);
            output.append(owner).append(".").append(field).append(" = ").append(value).append(";\n");
        } else {
            throw new UnsupportedOperationException("unsupported field insn " + insn.getOpcode());
        }
    }

    private void emitMethodInsn(MethodInsnNode insn, int indent) {
        Type methodType = Type.getMethodType(insn.desc);
        Type[] argTypes = methodType.getArgumentTypes();
        String[] args = new String[argTypes.length];
        for (int i = argTypes.length - 1; i >= 0; i--) {
            args[i] = pop();
        }
        String call;
        String owner = TypeUtils.internalToQualified(insn.owner);
        if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
            call = owner + "." + insn.name + "(" + String.join(", ", args) + ")";
        } else {
            String obj = pop();
            call = obj + "." + insn.name + "(" + String.join(", ", args) + ")";
        }
        if (methodType.getReturnType().getSort() == Type.VOID) {
            appendIndent(indent);
            output.append(call).append(";\n");
        } else {
            push(call);
        }
    }

    private String localName(int index) {
        return localNames.getOrDefault(index, "var" + index);
    }

    private String arrayTypeName(int operand) {
        return switch (operand) {
            case Opcodes.T_BOOLEAN -> "boolean";
            case Opcodes.T_CHAR -> "char";
            case Opcodes.T_BYTE -> "byte";
            case Opcodes.T_SHORT -> "short";
            case Opcodes.T_INT -> "int";
            case Opcodes.T_LONG -> "long";
            case Opcodes.T_FLOAT -> "float";
            case Opcodes.T_DOUBLE -> "double";
            default -> "Object";
        };
    }

    private String constantToString(Object cst) {
        if (cst instanceof String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        if (cst instanceof Long) {
            return cst + "L";
        }
        if (cst instanceof Float) {
            return cst + "f";
        }
        if (cst instanceof Double) {
            return cst.toString();
        }
        if (cst instanceof Type t) {
            return TypeUtils.internalToQualified(t.getInternalName()) + ".class";
        }
        return String.valueOf(cst);
    }

    private void push(String value) {
        stack.push(value);
    }

    private String pop() {
        if (stack.isEmpty()) {
            throw new UnsupportedOperationException("stack underflow");
        }
        return stack.pop();
    }

    private String peek() {
        if (stack.isEmpty()) {
            throw new UnsupportedOperationException("stack underflow");
        }
        return stack.peek();
    }

    private void appendIndent(int indent) {
        for (int i = 0; i < indent; i++) {
            output.append("    ");
        }
    }
}
