package fsm.errors.tree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

public class TreeNodeClient {

    public static void main(String[] args) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("outline");
        root.add(new DefaultMutableTreeNode("section"));
        root.removeAllChildren();
        TreeNode landing = root.getFirstChild(); // state error: found childrenAllowed, expected maybeHasChildren
    }
}
