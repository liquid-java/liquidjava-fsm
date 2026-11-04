package fsm;

import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@StateSet({"a", "b", "c"})
public class InheritedOverride {

    @StateRefinement(from = "a(this)", to = "b(this)")
    public void next() {}

    @StateRefinement(from = "c(this)")
    public void useC() {}

    @StateRefinement(from = "a(this)", to = "c(this)")
    void inPackage() {}
}

// overrides next with a stronger transition and adds one to the states it inherits
class InheritedOverrideChild extends InheritedOverride {

    @StateRefinement(to = "a(this)")
    public InheritedOverrideChild() {}

    @Override
    @StateRefinement(from = "a(this)", to = "c(this)")
    public void next() {}

    @StateRefinement(from = "b(this)", to = "a(this)")
    public void reset() {}
}
