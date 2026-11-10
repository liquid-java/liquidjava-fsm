package fsm;

import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@StateSet({"start", "canEdit", "endDone", "endUndone"})
public class NoFromBesideFrom {

    @StateRefinement(to="start(this)")
    public NoFromBesideFrom() {}

    @StateRefinement(from="start(this) || canEdit(this)", to="canEdit(this)")
    public void addEdit() {}

    @StateRefinement(from="endDone(this)", to="endUndone(this)")
    public void undo() {}

    // the from alternatives cover every state, so the last one adds no transition
    @StateRefinement(from="start(this)", to="!return && start(this)")
    @StateRefinement(from="canEdit(this)")
    @StateRefinement(from="endDone(this)")
    @StateRefinement(from="endUndone(this)", to="!return && endUndone(this)")
    @StateRefinement(to="return ? endDone(this) : true")
    public boolean canUndo() { return false; }

    // the last alternative still goes from the states that the others do not cover
    @StateRefinement(from="endDone(this)")
    @StateRefinement(to="start(this)")
    public void reset() {}
}
