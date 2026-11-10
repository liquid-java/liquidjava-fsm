package liquidjava.fsm;

import java.util.List;

/**
 * Represents the state machine of one state set of a class
 *
 * @param stateSet the index of the state set among the class's StateSet annotations, in declaration order
 * @param stateSetName the name given to the state set, or null if it has none
 * @param entryTransitions always empty, see {@link StateMachineEntryTransition}
 */
public record StateMachine(
    String className,
    int stateSet,
    String stateSetName,
    List<String> states,
    List<StateMachineTransition> transitions,
    List<StateMachineInitialTransition> initialTransitions,
    @Deprecated List<StateMachineEntryTransition> entryTransitions
) {
    public StateMachine(String className, int stateSet, List<String> states, List<StateMachineTransition> transitions,
            List<StateMachineInitialTransition> initialTransitions, List<StateMachineEntryTransition> entryTransitions) {
        this(className, stateSet, null, states, transitions, initialTransitions, entryTransitions);
    }

    public StateMachine(String className, int stateSet, List<String> states, List<StateMachineTransition> transitions,
            List<StateMachineInitialTransition> initialTransitions) {
        this(className, stateSet, states, transitions, initialTransitions, List.of());
    }

    public StateMachine(String className, List<String> states, List<StateMachineTransition> transitions,
            List<StateMachineInitialTransition> initialTransitions) {
        this(className, 0, states, transitions, initialTransitions);
    }
}
