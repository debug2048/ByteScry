package com.github.bytescry.simple;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

/**
 * Builds a control flow graph from an ASM MethodNode.
 */
public class ControlFlowGraph {

    private final List<BasicBlock> blocks = new ArrayList<>();
    private final BasicBlock entry;
    private final Map<LabelNode, BasicBlock> labelToBlock = new IdentityHashMap<>();

    public ControlFlowGraph(MethodNode methodNode) {
        this.entry = build(methodNode);
    }

    public BasicBlock getEntry() {
        return entry;
    }

    public List<BasicBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    private BasicBlock build(MethodNode methodNode) {
        if (methodNode.instructions == null || methodNode.instructions.size() == 0) {
            BasicBlock empty = newBlock();
            return empty;
        }

        // Identify leaders: first instruction, targets of jumps, instructions after jumps
        Set<AbstractInsnNode> leaders = new HashSet<>();
        AbstractInsnNode first = methodNode.instructions.getFirst();
        leaders.add(first);

        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn.getType() == AbstractInsnNode.JUMP_INSN) {
                JumpInsnNode jump = (JumpInsnNode) insn;
                leaders.add(jump.label);
                AbstractInsnNode next = insn.getNext();
                if (next != null) {
                    leaders.add(next);
                }
            } else if (insn.getType() == AbstractInsnNode.LOOKUPSWITCH_INSN ||
                    insn.getType() == AbstractInsnNode.TABLESWITCH_INSN) {
                // Complex control flow not supported by simple engine, but mark leaders.
                AbstractInsnNode next = insn.getNext();
                if (next != null) {
                    leaders.add(next);
                }
            }
        }

        // Create blocks and assign instructions
        BasicBlock current = null;
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (leaders.contains(insn)) {
                current = newBlock();
                if (insn instanceof LabelNode label) {
                    labelToBlock.put(label, current);
                }
            }
            if (current != null) {
                current.getInstructions().add(insn);
            }
        }

        // Wire successors
        for (BasicBlock block : blocks) {
            AbstractInsnNode last = block.getLastInstruction();
            if (last == null) {
                continue;
            }
            int opcode = last.getOpcode();
            if (opcode >= org.objectweb.asm.Opcodes.IRETURN && opcode <= org.objectweb.asm.Opcodes.RETURN) {
                // no successors
            } else if (last.getType() == AbstractInsnNode.JUMP_INSN) {
                JumpInsnNode jump = (JumpInsnNode) last;
                BasicBlock target = labelToBlock.get(jump.label);
                if (target != null) {
                    block.addSuccessor(target);
                }
                // Conditional jumps also fall through
                if (opcode != org.objectweb.asm.Opcodes.GOTO) {
                    BasicBlock fallthrough = findBlockContaining(insnAfter(last));
                    if (fallthrough != null) {
                        block.addSuccessor(fallthrough);
                    }
                }
            } else {
                BasicBlock fallthrough = findBlockContaining(insnAfter(last));
                if (fallthrough != null) {
                    block.addSuccessor(fallthrough);
                }
            }
        }

        return blocks.isEmpty() ? newBlock() : blocks.get(0);
    }

    private BasicBlock newBlock() {
        BasicBlock block = new BasicBlock(blocks.size());
        blocks.add(block);
        return block;
    }

    private AbstractInsnNode insnAfter(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        // Skip labels and line numbers to find real instruction, but return the next node anyway
        return next;
    }

    private BasicBlock findBlockContaining(AbstractInsnNode insn) {
        if (insn == null) {
            return null;
        }
        for (BasicBlock block : blocks) {
            if (block.getInstructions().contains(insn)) {
                return block;
            }
        }
        return null;
    }
}
