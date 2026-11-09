package fsm;

import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

// states written as values of their state sets, in either order, with == and !=, mixed with the shorthand
@StateSet(name = "phase", value = {"open", "closed", "broken"})
@StateSet({"unlocked", "locked"})
public class StateValues {

    @StateRefinement(to = "phase(this) == open && unlocked(this)")
    public StateValues() {}

    @StateRefinement(from = "open == this.phase()", to = "closed(this)")
    public void close() {}

    // the unnamed state set is named state2
    @StateRefinement(from = "phase() != broken && state2(this) == unlocked", to = "phase(this) == broken")
    public void breakDown() {}

    @StateRefinement(from = "phase(this) == closed", to = "phase(this) == open && state2(this) == locked")
    public void openLocked() {}
}
