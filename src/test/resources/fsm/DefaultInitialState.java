package fsm;

import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@StateSet({"ready", "done"})
public class DefaultInitialState {

    public DefaultInitialState() {}

    public DefaultInitialState(int count) {}

    @StateRefinement(from="ready(this)", to="done(this)")
    public void finish() {}
}
