package liquidjava.fsm;

import java.util.List;

/**
 * Describes the verification error overlaid on a state machine diagram.
 * @param calledMethod the method, constructor or static factory declared at the error's declaration position
 * @param actualStates the states mentioned by the found condition for the receiver
 * @param receiver the receiver of the failing call (e.g. root)
 * @param expectedStates the states accepted by the expected condition, in state set order
 * @param expectedCondition the expected condition as text
 * @param foundCondition the found condition for the receiver as text
 * @param definiteFoundState the single state the found condition establishes, or null if there is none
 * @param possibleStates the states the found condition allows for the receiver, in state set order
 */
public record StateMachineErrorContext(
    String calledMethod,
    List<String> actualStates,
    String receiver,
    List<String> expectedStates,
    String expectedCondition,
    String foundCondition,
    String definiteFoundState,
    List<String> possibleStates
) {}
