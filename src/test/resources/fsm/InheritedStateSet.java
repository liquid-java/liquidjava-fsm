package fsm;

import liquidjava.specification.ExternalRefinementsFor;
import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@StateSet({"displayable", "notDisplayable"})
@ExternalRefinementsFor("java.awt.Window")
interface WindowRefinements {
    @StateRefinement(to = "notDisplayable(this)")
    void Window(java.awt.Frame owner);

    @StateRefinement(to = "notDisplayable(this)")
    void dispose();

    @StateRefinement(to = "displayable(this)")
    void pack();

    // Frame redeclares addNotify, so on a Frame the verifier does not apply this refinement
    @StateRefinement(to = "displayable(this)")
    void addNotify();
}

// the state set is declared in the superclass's spec
@ExternalRefinementsFor("java.awt.Frame")
interface FrameRefinements {
    @StateRefinement(to = "notDisplayable(this)")
    void Frame(String title);

    @StateRefinement(from = "notDisplayable(this)")
    void setUndecorated(boolean undecorated);
}
