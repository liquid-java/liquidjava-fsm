package liquidjava.fsm;

/**
 * Represents an initial transition in a state machine
 */
public record StateMachineInitialTransition(String to, String toCondition) {

    public StateMachineInitialTransition(String to) {
        this(to, null);
    }
}
