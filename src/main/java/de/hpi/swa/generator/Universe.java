package de.hpi.swa.generator;

import java.util.Random;

public class Universe {

    public Universe(java.util.Random random, DecisionTree tree) {
        this.random = random;
        this.tree = tree;
        this.currentNode = tree.getRoot();
    }

    public final Random random;
    private int nextId = 0;
    public final DecisionTree tree;
    public DecisionTree.Node currentNode;

    public int reserveId() {
        var id = nextId;
        nextId++;
        return id;
    }

    public void reset() {
        this.currentNode = tree.getRoot();
    }

    public void crash(String message) {
        this.currentNode = this.currentNode.crash(message);
    }
}
