package fsm;

import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@StateSet({"ready", "done"})
public class SameInitialState {

    @StateRefinement(to="ready(this)")
    public SameInitialState() {}

    @StateRefinement(to="ready(this)")
    public SameInitialState(int count, boolean enabled) {}

    public SameInitialState(String label) {}

    @StateRefinement(from="ready(this)", to="done(this)")
    public void finish() {}
}
