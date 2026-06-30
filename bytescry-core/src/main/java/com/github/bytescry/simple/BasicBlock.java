package com.github.bytescry.simple;

import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * A basic block in a method control flow graph.
 */
public class BasicBlock {

    private final int id;
    private final List<AbstractInsnNode> instructions = new ArrayList<>();
    private final List<BasicBlock> successors = new ArrayList<>();
    private final List<BasicBlock> predecessors = new ArrayList<>();

    public BasicBlock(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public List<AbstractInsnNode> getInstructions() {
        return instructions;
    }

    public List<BasicBlock> getSuccessors() {
        return successors;
    }

    public List<BasicBlock> getPredecessors() {
        return predecessors;
    }

    public AbstractInsnNode getLastInstruction() {
        return instructions.isEmpty() ? null : instructions.get(instructions.size() - 1);
    }

    public boolean isEmpty() {
        return instructions.isEmpty();
    }

    void addSuccessor(BasicBlock successor) {
        this.successors.add(successor);
        successor.predecessors.add(this);
    }

    @Override
    public String toString() {
        return "BB" + id;
    }
}
