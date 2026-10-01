package liquidjava.fsm;

/**
 * Represents an initial transition in a state machine
 */
public record StateMachineInitialTransition(String to, String toCondition, String constructorSignature) {

    public StateMachineInitialTransition(String to, String toCondition) {
        this(to, toCondition, null);
    }

    public StateMachineInitialTransition(String to) {
        this(to, null, null);
    }
}
