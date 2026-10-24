package fsm;

import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@StateSet({"open", "closed"})
public class TwoStateClasses {

    @StateRefinement(from="open(this)", to="closed(this)")
    public void close() {}
}

@StateSet({"idle", "busy"})
class Second {

    @StateRefinement(from="idle(this)", to="busy(this)")
    public void start() {}
}
