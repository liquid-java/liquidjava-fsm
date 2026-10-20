package fsm.errors.tree;

import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;

import liquidjava.specification.ExternalRefinementsFor;
import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

// example from vscode-liquidjava#117
@ExternalRefinementsFor("javax.swing.tree.DefaultMutableTreeNode")
@StateSet({"childrenAllowed", "maybeHasChildren"})
public interface DefaultMutableTreeNodeRefinements {

    @StateRefinement(to="childrenAllowed(this)")
    void DefaultMutableTreeNode(Object value);

    @StateRefinement(from="childrenAllowed(this)", to="maybeHasChildren(this)")
    void add(MutableTreeNode child);

    @StateRefinement(from="maybeHasChildren(this)", to="childrenAllowed(this)")
    void removeAllChildren();

    @StateRefinement(from="maybeHasChildren(this)")
    TreeNode getFirstChild();
}
