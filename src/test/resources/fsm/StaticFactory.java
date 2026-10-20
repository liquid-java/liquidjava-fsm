package fsm;

import liquidjava.specification.Refinement;
import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@StateSet({"open", "closed"})
public class StaticFactory {

    @StateRefinement(to="open(this)")
    public StaticFactory() {}

    // static factory methods are initial transitions
    @Refinement("closed(return)")
    public static StaticFactory closedInstance() { return null; }

    @Refinement("flag ? open(return) : closed(return)")
    public static StaticFactory of(boolean flag) { return null; }

    // ignored: not static
    @Refinement("closed(return)")
    public StaticFactory copy() { return null; }

    // ignored: does not return the class type
    @Refinement("_ > 0")
    public static int count() { return 1; }

    @StateRefinement(from="open(this)", to="closed(this)")
    public void close() {}
}
