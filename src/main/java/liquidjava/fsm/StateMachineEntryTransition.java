package liquidjava.fsm;

/**
 * Represents an entry transition in a state machine, a method that puts an object in an unknown state into a state
 * @deprecated no longer produced: a StateRefinement without from goes from the states the others do not cover, and only
 * constructors and factory methods enter the state machine; kept so that clients still compile
 */
@Deprecated
public record StateMachineEntryTransition(String to, String label, String toCondition) {

    public StateMachineEntryTransition(String to, String label) {
        this(to, label, null);
    }
}
