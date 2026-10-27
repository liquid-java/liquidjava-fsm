package liquidjava.fsm;

/**
 * Represents an entry transition in a state machine, a method that puts an object in an unknown state into a state
 * These come from a StateRefinement without from beside StateRefinements with from in the same method
 */
public record StateMachineEntryTransition(String to, String label, String toCondition) {

    public StateMachineEntryTransition(String to, String label) {
        this(to, label, null);
    }
}
