package liquidjava.fsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import liquidjava.api.CommandLineLauncher;
import liquidjava.diagnostics.Diagnostics;
import liquidjava.diagnostics.errors.StateErrorContext;
import liquidjava.diagnostics.errors.StateRefinementError;

public class StateMachineParserTests {

    private static final Path BASE_PATH = Path.of("src", "test", "resources", "fsm");

    private static String uri(String file) {
        return BASE_PATH.resolve(file).toAbsolutePath().toUri().toString();
    }

    @Test
    public void testSimpleStateMachine() {
        StateMachine sm = StateMachineParser.parse(uri("Simple.java"));
        StateMachine expectedSm = stateMachine("Simple", List.of("open", "closed"),
                List.of(new StateMachineTransition("open", "closed", "close"),
                        new StateMachineTransition("open", "open", "read")),
                List.of(new StateMachineInitialTransition("open")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testOrTransition() {
        // state1 || state2 => separate transitions from both state1 and state2
        StateMachine sm = StateMachineParser.parse(uri("OrTransition.java"));
        StateMachine expectedSm = stateMachine("OrTransition", List.of("a", "b", "c"), List
                .of(new StateMachineTransition("a", "c", "action"), new StateMachineTransition("b", "c", "action")),
                List.of(new StateMachineInitialTransition("a")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testNegationTransition() {
        // !state => all states except state
        StateMachine sm = StateMachineParser.parse(uri("NegationTransition.java"));
        StateMachine expectedSm = stateMachine("NegationTransition", List.of("open", "closed", "locked"),
                List.of(new StateMachineTransition("open", "locked", "lock"),
                        new StateMachineTransition("closed", "locked", "lock")),
                List.of(new StateMachineInitialTransition("open")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testSelfLoop() {
        // from=state, to=state or from=state => self-loop
        StateMachine sm = StateMachineParser.parse(uri("SelfLoop.java"));
        StateMachine expectedSm = stateMachine("SelfLoop", List.of("idle", "running"),
                List.of(new StateMachineTransition("idle", "idle", "noop"),
                        new StateMachineTransition("idle", "running", "start"),
                        new StateMachineTransition("running", "running", "tick")),
                List.of(new StateMachineInitialTransition("idle")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testToOnlyTransition() {
        // no from => all states contain a transition to state
        StateMachine sm = StateMachineParser.parse(uri("ToOnlyTransition.java"));
        StateMachine expectedSm = stateMachine("ToOnlyTransition", List.of("a", "b", "c"),
                List.of(new StateMachineTransition("a", "c", "action"), new StateMachineTransition("b", "c", "action"),
                        new StateMachineTransition("c", "c", "action")),
                List.of(new StateMachineInitialTransition("a")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testEntryTransition() {
        // no from beside alternatives with from => entry transition, and edges only from the states they do not cover
        StateMachine sm = StateMachineParser.parse(uri("EntryTransition.java"));
        StateMachine expectedSm = stateMachine("EntryTransition", List.of("start", "canEdit", "endDone", "endUndone"),
                List.of(new StateMachineTransition("start", "canEdit", "addEdit"),
                        new StateMachineTransition("canEdit", "canEdit", "addEdit"),
                        new StateMachineTransition("start", "start", "canUndo", null, "!return"),
                        new StateMachineTransition("canEdit", "canEdit", "canUndo"),
                        new StateMachineTransition("endDone", "endDone", "canUndo"),
                        new StateMachineTransition("endUndone", "endUndone", "canUndo", null, "!return"),
                        new StateMachineTransition("endDone", "endDone", "reset"),
                        new StateMachineTransition("start", "start", "reset"),
                        new StateMachineTransition("canEdit", "start", "reset"),
                        new StateMachineTransition("endUndone", "start", "reset"),
                        new StateMachineTransition("endDone", "endUndone", "undo")),
                List.of(new StateMachineInitialTransition("start")));
        assertStateMachineEquals(expectedSm, sm);
        assertEquals(List.of(new StateMachineEntryTransition("endDone", "canUndo", "return"),
                        new StateMachineEntryTransition("start", "reset")),
                sm.entryTransitions());
    }

    @Test
    public void testToOnlyTransitionHasNoEntryTransition() {
        // no from without alternatives goes from every state, also in a known state
        StateMachine sm = StateMachineParser.parse(uri("ToOnlyTransition.java"));
        assertEquals(List.of(), sm.entryTransitions());
    }

    @Test
    public void testMultipleInitialStates() {
        // overloading constructors with different initial states
        StateMachine sm = StateMachineParser.parse(uri("MultipleInitialStates.java"));
        StateMachine expectedSm = stateMachine("MultipleInitialStates", List.of("initialized", "uninitialized", "error"),
                List.of(new StateMachineTransition("uninitialized", "initialized", "init")),
                List.of(new StateMachineInitialTransition("uninitialized"),
                        new StateMachineInitialTransition("initialized")));
        assertStateMachineEquals(expectedSm, sm);
        assertEquals(List.of("new MultipleInitialStates()", "new MultipleInitialStates(int)"),
                sm.initialTransitions().stream().map(StateMachineInitialTransition::constructorSignature).toList());
    }

    @Test
    public void testOverloadedConstructorsLeadingToSameState() {
        StateMachine sm = StateMachineParser.parse(uri("SameInitialState.java"));
        assertNotNull(sm);
        assertEquals(List.of("ready", "ready", "ready"),
                sm.initialTransitions().stream().map(StateMachineInitialTransition::to).toList());
        assertEquals(List.of("new SameInitialState()", "new SameInitialState(int, boolean)",
                        "new SameInitialState(String)"),
                sm.initialTransitions().stream().map(StateMachineInitialTransition::constructorSignature).toList());
    }

    @Test
    public void testUnannotatedConstructorsUseDefaultState() {
        StateMachine sm = StateMachineParser.parse(uri("DefaultInitialState.java"));
        assertNotNull(sm);
        assertEquals(List.of("ready", "ready"),
                sm.initialTransitions().stream().map(StateMachineInitialTransition::to).toList());
        assertEquals(List.of("new DefaultInitialState()", "new DefaultInitialState(int)"),
                sm.initialTransitions().stream().map(StateMachineInitialTransition::constructorSignature).toList());
    }

    @Test
    public void testExternalRefinementsInterface() {
        // class name from @ExternalStateRefinements
        StateMachine sm = StateMachineParser.parse(uri("ExternalRefinements.java"));
        StateMachine expectedSm = stateMachine("com.example.Connection", List.of("connected", "disconnected"),
                List.of(new StateMachineTransition("disconnected", "connected", "connect")),
                List.of(new StateMachineInitialTransition("disconnected")));
        assertStateMachineEquals(expectedSm, sm);
        assertEquals("new Connection()", sm.initialTransitions().get(0).constructorSignature());
    }

    @Test
    public void testConditionalTransition() {
        // transitions for both branches of condition
        StateMachine sm = StateMachineParser.parse(uri("ConditionalTransition.java"));
        StateMachine expectedSm = stateMachine("ConditionalTransition", List.of("on", "off"),
                List.of(new StateMachineTransition("on", "off", "turnOff"),
                        new StateMachineTransition("off", "on", "turnOn")),
                List.of(new StateMachineInitialTransition("on", "flag"),
                        new StateMachineInitialTransition("off", "!flag")));
        assertStateMachineEquals(expectedSm, sm);
        assertEquals(List.of("new ConditionalTransition(boolean)", "new ConditionalTransition(boolean)"),
                sm.initialTransitions().stream().map(StateMachineInitialTransition::constructorSignature).toList());
    }

    @Test
    public void testConjunctionInitialTransition() {
        StateMachine sm = StateMachineParser.parse(uri("ConjunctionInitialTransition.java"));
        StateMachine expectedSm = stateMachine("ConjunctionInitialTransition", List.of("on", "off"),
                List.of(new StateMachineTransition("on", "off", "turnOff")),
                List.of(new StateMachineInitialTransition("on", "flag")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testConjunctionPrecondition() {
        StateMachine sm = StateMachineParser.parse(uri("ConjunctionPrecondition.java"));
        StateMachine expectedSm = stateMachine("ConjunctionPrecondition", List.of("open", "closed"),
                List.of(new StateMachineTransition("open", "closed", "close", "flag", null)),
                List.of(new StateMachineInitialTransition("open")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testDisjunctionPrecondition() {
        StateMachine sm = StateMachineParser.parse(uri("DisjunctionPrecondition.java"));
        StateMachine expectedSm = stateMachine("DisjunctionPrecondition", List.of("ready", "waiting", "done"),
                List.of(new StateMachineTransition("waiting", "done", "action", "flag", null),
                        new StateMachineTransition("done", "done", "action", "flag", null),
                        new StateMachineTransition("ready", "done", "action")),
                List.of(new StateMachineInitialTransition("ready")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testConditionalPrecondition() {
        StateMachine sm = StateMachineParser.parse(uri("ConditionalPrecondition.java"));
        StateMachine expectedSm = stateMachine("ConditionalPrecondition", List.of("left", "right", "done"),
                List.of(new StateMachineTransition("left", "done", "finish", "flag", null),
                        new StateMachineTransition("right", "done", "finish", "!flag", null)),
                List.of(new StateMachineInitialTransition("left")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testPostConditionFiltering() {
        StateMachine sm = StateMachineParser.parse(uri("PostConditionFiltering.java"));
        StateMachine expectedSm = stateMachine("PostConditionFiltering", List.of("ready", "done"),
                List.of(new StateMachineTransition("ready", "done", "finish", null, "flag")),
                List.of(new StateMachineInitialTransition("ready")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testCombinedPostCondition() {
        StateMachine sm = StateMachineParser.parse(uri("CombinedPostCondition.java"));
        StateMachine expectedSm = stateMachine("CombinedPostCondition", List.of("ready", "done"),
                List.of(new StateMachineTransition("ready", "done", "finish", "x", "flag")),
                List.of(new StateMachineInitialTransition("ready")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testConditionalPostCondition() {
        StateMachine sm = StateMachineParser.parse(uri("ConditionalPostCondition.java"));
        StateMachine expectedSm = stateMachine("ConditionalPostCondition", List.of("ready", "done", "error"),
                List.of(new StateMachineTransition("ready", "done", "finish", null, "flag"),
                        new StateMachineTransition("ready", "error", "finish", null, "!flag")),
                List.of(new StateMachineInitialTransition("ready")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testDisjunctionPostCondition() {
        StateMachine sm = StateMachineParser.parse(uri("DisjunctionPostCondition.java"));
        StateMachine expectedSm = stateMachine("DisjunctionPostCondition", List.of("ready", "done"),
                List.of(new StateMachineTransition("ready", "done", "finish")),
                List.of(new StateMachineInitialTransition("ready")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testGuardedSelfLoopDoesNotDuplicateCondition() {
        StateMachine sm = StateMachineParser.parse(uri("GuardedSelfLoop.java"));
        StateMachine expectedSm = stateMachine("GuardedSelfLoop", List.of("ready", "done"),
                List.of(new StateMachineTransition("ready", "ready", "poll", "flag", null)),
                List.of(new StateMachineInitialTransition("ready")));
        assertStateMachineEquals(expectedSm, sm);
    }

    @Test
    public void testStaticFactoryMethods() {
        // static methods returning the class are initial transitions
        StateMachine sm = StateMachineParser.parse(uri("StaticFactory.java"));
        StateMachine expectedSm = stateMachine("StaticFactory", List.of("open", "closed"),
                List.of(new StateMachineTransition("open", "closed", "close")),
                List.of(new StateMachineInitialTransition("open"),
                        new StateMachineInitialTransition("closed"),
                        new StateMachineInitialTransition("open", "flag"),
                        new StateMachineInitialTransition("closed", "!flag")));
        assertStateMachineEquals(expectedSm, sm);
        assertEquals(List.of("new StaticFactory()", "StaticFactory.closedInstance()",
                        "StaticFactory.of(boolean)", "StaticFactory.of(boolean)"),
                sm.initialTransitions().stream().map(StateMachineInitialTransition::constructorSignature).toList());
    }

    @Test
    public void testExternalStaticFactoryMethods() {
        // in interfaces any method returning the class is a factory method
        StateMachine sm = StateMachineParser.parse(uri("ExternalStaticFactory.java"));
        StateMachine expectedSm = stateMachine("java.util.UUID",
                List.of("dceSecurityNameRandom", "maybeTime", "timeBased"),
                List.of(new StateMachineTransition("timeBased", "timeBased", "timestamp")),
                List.of(new StateMachineInitialTransition("timeBased", "mostSigBits / 4096 % 16 == 1"),
                        new StateMachineInitialTransition("dceSecurityNameRandom", "!(mostSigBits / 4096 % 16 == 1)"),
                        new StateMachineInitialTransition("maybeTime"),
                        new StateMachineInitialTransition("dceSecurityNameRandom")));
        assertStateMachineEquals(expectedSm, sm);
        assertEquals(List.of("new UUID(long, long)", "new UUID(long, long)", "UUID.fromString(String)",
                        "UUID.randomUUID()"),
                sm.initialTransitions().stream().map(StateMachineInitialTransition::constructorSignature).toList());
    }

    @Test
    public void testExternalFactoryWithoutConstructorHasNoDefaultInitialState() {
        StateMachine sm = StateMachineParser.parse(uri("ExternalFactoryOnly.java"));
        assertNotNull(sm);
        assertEquals(List.of(new StateMachineInitialTransition("timeBased", null, "UUID.fromString(String)")),
                sm.initialTransitions());
    }

    @Test
    public void testClassSelectedByName() {
        StateMachine sm = StateMachineParser.parse(uri("TwoStateClasses.java"), "fsm.Second");
        assertStateMachineEquals(stateMachine("Second", List.of("idle", "busy"),
                List.of(new StateMachineTransition("idle", "busy", "start")),
                List.of(new StateMachineInitialTransition("idle"))), sm);
        assertEquals("fsm.TwoStateClasses", StateMachineParser.parse(uri("TwoStateClasses.java")).className());
        assertNull(StateMachineParser.parse(uri("TwoStateClasses.java"), "fsm.Missing"));
    }

    @Test
    public void testStateMachinePerStateSet() {
        // each state set only has the transitions that mention its states, the other state sets stay as conditions
        List<StateMachine> sms = StateMachineParser.parseAll(uri("TwoStateSets.java"));
        assertEquals(2, sms.size());
        assertStateMachineEquals(stateMachine("TwoStateSets", 0, List.of("startTiling", "tilingExplicit", "tilingSet"),
                List.of(new StateMachineTransition("startTiling", "startTiling", "resetTiling", "compressionSet(this)", null),
                        new StateMachineTransition("tilingExplicit", "startTiling", "resetTiling", "compressionSet(this)", null),
                        new StateMachineTransition("tilingSet", "startTiling", "resetTiling", "compressionSet(this)", null),
                        new StateMachineTransition("tilingExplicit", "tilingSet", "setTiling"),
                        new StateMachineTransition("startTiling", "tilingExplicit", "setTilingMode", null, "mode == 2"),
                        new StateMachineTransition("startTiling", "startTiling", "setTilingMode", null, "!(mode == 2)"),
                        new StateMachineTransition("tilingExplicit", "tilingExplicit", "setTilingMode", null, "mode == 2"),
                        new StateMachineTransition("tilingExplicit", "startTiling", "setTilingMode", null, "!(mode == 2)"),
                        new StateMachineTransition("tilingSet", "tilingExplicit", "setTilingMode", null, "mode == 2"),
                        new StateMachineTransition("tilingSet", "startTiling", "setTilingMode", null, "!(mode == 2)"),
                        new StateMachineTransition("tilingSet", "tilingSet", "write", "compressionSet(this)", null)),
                List.of(new StateMachineInitialTransition("startTiling"),
                        new StateMachineInitialTransition("tilingExplicit"))), sms.get(0));
        // the second constructor does not set a compression state, so it starts in the first one
        assertStateMachineEquals(stateMachine("TwoStateSets", 1,
                List.of("startCompression", "compressionExplicit", "compressionSet"),
                List.of(new StateMachineTransition("compressionSet", "compressionSet", "resetTiling"),
                        new StateMachineTransition("startCompression", "compressionExplicit", "setCompressionMode", null, "mode == 2"),
                        new StateMachineTransition("startCompression", "startCompression", "setCompressionMode", null, "!(mode == 2)"),
                        new StateMachineTransition("compressionExplicit", "compressionExplicit", "setCompressionMode", null, "mode == 2"),
                        new StateMachineTransition("compressionExplicit", "startCompression", "setCompressionMode", null, "!(mode == 2)"),
                        new StateMachineTransition("compressionSet", "compressionExplicit", "setCompressionMode", null, "mode == 2"),
                        new StateMachineTransition("compressionSet", "startCompression", "setCompressionMode", null, "!(mode == 2)"),
                        new StateMachineTransition("compressionExplicit", "compressionSet", "setCompressionType"),
                        new StateMachineTransition("compressionSet", "compressionSet", "write", "tilingSet(this)", null)),
                List.of(new StateMachineInitialTransition("startCompression"),
                        new StateMachineInitialTransition("startCompression"))), sms.get(1));
        // parse keeps returning the first state set
        assertEquals(sms.get(0), StateMachineParser.parse(uri("TwoStateSets.java")));
        assertEquals(List.of(), StateMachineParser.parseAll(uri("TwoStateClasses.java"), "fsm.Missing"));
    }

    @Test
    public void testVerifierStatesAreStatesOfTheDiagram() {
        for (String directory : List.of("errors/door", "errors/tree", "errors/udp", "errors/image")) {
            List<StateRefinementError> errors = stateErrors(directory);
            assertFalse(errors.isEmpty(), "Expected state refinement errors in " + directory);
            for (StateRefinementError error : errors) {
                StateErrorContext context = error.getContext();
                assertNotNull(context, "Expected the verifier's states for " + error.getMessage());
                List<StateMachine> sms = StateMachineParser.parseAll(
                        error.getDeclarationPosition().getFile().toURI().toString(), context.receiverClass());
                assertFalse(sms.isEmpty(), "Expected a diagram of " + context.receiverClass());
                List<List<String>> verifierStates = new ArrayList<>(context.possibleStates());
                if (context.acceptedStates() != null)
                    verifierStates.addAll(context.acceptedStates());
                // the verifier names a state in each state set in the same order as the diagrams
                for (StateMachine sm : sms)
                    for (List<String> state : verifierStates)
                        assertTrue(sm.states().contains(state.get(sm.stateSet())),
                                state.get(sm.stateSet()) + " is not in " + sm.states());
            }
        }
    }

    private static void assertStateMachineEquals(StateMachine expected, StateMachine actual) {
        assertNotNull(actual, "State machine should not be null");
        assertEquals(expected.className(), actual.className(), "Class names should match");
        assertEquals(expected.stateSet(), actual.stateSet(), "State sets should match");
        assertEquals(expected.initialTransitions(), actual.initialTransitions().stream()
                .map(transition -> new StateMachineInitialTransition(transition.to(), transition.toCondition()))
                .toList(), "Initial transitions should match");
        assertEquals(expected.states(), actual.states(), "States should match");
        assertEquals(expected.transitions(), actual.transitions(), "State transitions should match");
    }

    private static StateMachine stateMachine(String className, List<String> states,
            List<StateMachineTransition> transitions, List<StateMachineInitialTransition> initialTransitions) {
        return stateMachine(className, 0, states, transitions, initialTransitions);
    }

    private static StateMachine stateMachine(String className, int stateSet, List<String> states,
            List<StateMachineTransition> transitions, List<StateMachineInitialTransition> initialTransitions) {
        String qualifiedName = className.contains(".") ? className : "fsm." + className;
        return new StateMachine(qualifiedName, stateSet, states, transitions, initialTransitions);
    }

    private static List<StateRefinementError> stateErrors(String directory) {
        CommandLineLauncher.launch(BASE_PATH.resolve(directory).toAbsolutePath().toString());
        return Diagnostics.getInstance().getErrors().stream()
                .filter(StateRefinementError.class::isInstance)
                .map(StateRefinementError.class::cast)
                .toList();
    }
}
