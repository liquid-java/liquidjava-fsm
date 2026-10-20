package fsm.errors.door;

import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@StateSet({"open", "closed", "locked"})
public class Door {

    @StateRefinement(to="open(this)")
    public Door() {}

    @StateRefinement(to="flag ? open(this) : closed(this)")
    public Door(boolean flag) {}

    // accepts more than one state
    @StateRefinement(from="open(this) || closed(this)", to="locked(this)")
    public void lock() {}

    @StateRefinement(from="locked(this)", to="closed(this)")
    public void unlock() {}

    @StateRefinement(from="!locked(this)", to="open(this)")
    public void push() {}
}
