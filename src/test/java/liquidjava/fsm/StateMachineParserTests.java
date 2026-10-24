package liquidjava.fsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import liquidjava.api.CommandLineLauncher;
import liquidjava.diagnostics.Diagnostics;
import liquidjava.diagnostics.errors.StateRefinementError;
import liquidjava.processor.VCImplication;
import liquidjava.rj_language.Predicate;
import liquidjava.rj_language.parsing.RefinementsParser;
import spoon.Launcher;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertNull(sm.errorContext(), "Plain state machines should not contain diagnostic context");
    }

    @Test
    public void testStateMachineGroupsErrorContext() {
        StateMachineErrorContext errorContext = new StateMachineErrorContext(
                "read", List.of("closed"), "file", List.of("open"), "open(file)", "closed(file)", "closed",
                List.of("closed"));
        StateMachine sm = new StateMachine("Simple", List.of("open", "closed"), List.of(), List.of(), errorContext);

        assertEquals(errorContext, sm.errorContext());
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
    public void testErrorContextForRejectedCall() {
        // root.getFirstChild() after add and removeAllChildren
        StateRefinementError error = verifyStateError("errors/tree", "root");
        StateMachine sm = parseWithErrorContext(error);
        assertNotNull(sm);
        assertEquals(new StateMachineErrorContext("getFirstChild", List.of("childrenAllowed"), "root",
                List.of("maybeHasChildren"), "maybeHasChildren(root)", "childrenAllowed(root)", "childrenAllowed",
                List.of("childrenAllowed")),
                sm.errorContext());
    }

    @Test
    public void testErrorContextForPreconditionWithMultipleStates() {
        StateRefinementError error = verifyStateError("errors/door", "twice");
        assertEquals(new StateMachineErrorContext("lock", List.of("locked"), "twice", List.of("open", "closed"),
                "open(twice) || closed(twice)", "locked(twice)", "locked", List.of("locked")),
                parseWithErrorContext(error).errorContext());
    }

    @Test
    public void testErrorContextForNegatedPrecondition() {
        StateRefinementError error = verifyStateError("errors/door", "pushed");
        assertEquals(new StateMachineErrorContext("push", List.of("locked"), "pushed", List.of("open", "closed"),
                "!locked(pushed)", "locked(pushed)", "locked", List.of("locked")),
                parseWithErrorContext(error).errorContext());
    }

    @Test
    public void testErrorContextWithoutDefiniteFoundState() {
        // found flag ? open(unknown) : closed(unknown) does not establish one state
        StateRefinementError error = verifyStateError("errors/door", "unknown");
        assertEquals(new StateMachineErrorContext("unlock", List.of("open", "closed"), "unknown",
                List.of("locked"), "locked(unknown)", "flag ? open(unknown) : closed(unknown)", null,
                List.of("open", "closed")),
                parseWithErrorContext(error).errorContext());
    }

    @Test
    public void testErrorContextForStateFromBranches() {
        // socket.receive after closing the socket on one branch and sending on the other
        StateRefinementError error = verifyStateError("errors/udp", "socket");
        StateMachineErrorContext errorContext = parseWithErrorContext(error).errorContext();
        assertEquals("receive", errorContext.calledMethod());
        assertEquals("socket", errorContext.receiver());
        assertEquals(List.of("unbound", "bound", "connected"), errorContext.expectedStates());
        assertEquals(List.of("bound", "closed"), errorContext.possibleStates());
        assertNull(errorContext.definiteFoundState());
    }

    @Test
    public void testPossibleStates() {
        String doorUri = uri("errors/door/Door.java");
        assertEquals(List.of("open"), possibleStates(doorUri, "open(d)"));
        assertEquals(List.of("open"), possibleStates(doorUri, "open(d) && !locked(d)"));
        assertEquals(List.of("open"), possibleStates(doorUri, "open(d) && (closed(e) || locked(e))"));
        assertEquals(List.of("open", "closed"), possibleStates(doorUri, "closed(d) || open(d)"));
        assertEquals(List.of("open", "closed"), possibleStates(doorUri, "flag ? open(d) : closed(d)"));
        assertEquals(List.of("open", "locked"), possibleStates(doorUri, "!closed(d)"));
        assertEquals(List.of("closed"), possibleStates(doorUri, "!open(d) && !locked(d)"));
        assertEquals(List.of("closed"), possibleStates(doorUri, "!(open(d) || locked(d))"));
        assertEquals(List.of("open"), possibleStates(doorUri, "open(d) && (open(d) || closed(d))"));
        assertEquals(List.of(), possibleStates(doorUri, "open(d) && closed(d)"));
        assertEquals(List.of(), possibleStates(doorUri, "open(d) && !open(d)"));
        assertEquals(List.of("open", "closed", "locked"), possibleStates(doorUri, "open(e)"));
        assertEquals(List.of("open", "closed", "locked"), possibleStates(doorUri, "x > 0"));
        assertEquals(List.of("open", "closed", "locked"), possibleStates(doorUri, "true"));
    }

    @Test
    public void testNegatedFoundExcludesState() {
        CtTypeReference<?> doorType = new Launcher().getFactory().Type().createReference("fsm.errors.door.Door");
        VCImplication found = new VCImplication("#s_4", doorType, predicate("!closed(#s_4)"));
        StateMachineErrorContext errorContext = StateMachineParser.parseWithErrorContext(
                uri("errors/door/Door.java"), null, RefinementsParser.createAST("closed(#s_4)", ""), found)
                .errorContext();
        assertEquals(List.of("open", "locked"), errorContext.possibleStates());
    }

    @Test
    public void testPossibleStatesWithoutReceiver() {
        CtTypeReference<?> doorType = new Launcher().getFactory().Type().createReference("fsm.errors.door.Door");
        VCImplication found = new VCImplication("d", doorType, predicate("open(d)"));
        assertEquals(List.of(), StateMachineParser.parseWithErrorContext(uri("errors/door/Door.java"), null,
                RefinementsParser.createAST("x > 0", ""), found).errorContext().possibleStates());
    }

    @Test
    public void testPossibleStatesMatchDefiniteFoundState() {
        String doorUri = uri("errors/door/Door.java");
        for (String found : List.of("open(d)", "open(d) && !locked(d)", "open(d) && open(e)",
                "open(d) && (closed(e) || locked(e))", "open(d) || closed(d)", "flag ? open(d) : closed(d)",
                "!locked(d)", "!open(d) && !locked(d)", "!(open(d) || locked(d))", "open(d) && closed(d)",
                "open(d) && !open(d)", "open(d) && (open(d) || closed(d))", "open(e)", "true")) {
            StateMachineErrorContext errorContext = errorContext(doorUri, found);
            if (errorContext.definiteFoundState() != null) {
                assertEquals(List.of(errorContext.definiteFoundState()), errorContext.possibleStates(), found);
            }
        }
    }

    @Test
    public void testDefiniteFoundState() {
        String doorUri = uri("errors/door/Door.java");
        assertEquals("open", definiteFoundState(doorUri, "open(d)"));
        assertEquals("open", definiteFoundState(doorUri, "open(d) && !locked(d)"));
        assertEquals("open", definiteFoundState(doorUri, "open(d) && open(e)"));
        assertEquals("open", definiteFoundState(doorUri, "open(d) && (closed(e) || locked(e))"));
        assertNull(definiteFoundState(doorUri, "open(d) || closed(d)"));
        assertNull(definiteFoundState(doorUri, "flag ? open(d) : closed(d)"));
        assertNull(definiteFoundState(doorUri, "!locked(d)"));
        assertNull(definiteFoundState(doorUri, "!open(d) && !locked(d)"));
        assertNull(definiteFoundState(doorUri, "!(open(d) || locked(d))"));
        assertNull(definiteFoundState(doorUri, "open(d) && closed(d)"));
        assertNull(definiteFoundState(doorUri, "open(d) && !open(d)"));
        assertNull(definiteFoundState(doorUri, "open(d) && (open(d) || closed(d))"));
        assertNull(definiteFoundState(doorUri, "open(e)"));
        assertNull(definiteFoundState(doorUri, "true"));
    }

    @Test
    public void testFoundConditionIsRestrictedToReceiver() {
        CtTypeReference<?> doorType = new Launcher().getFactory().Type().createReference("fsm.errors.door.Door");
        VCImplication other = new VCImplication("#e_2", doorType, predicate("closed(#e_2)"));
        VCImplication receiver = new VCImplication("#d_3", doorType, predicate("open(#d_3) && x > 0"));
        other.setNext(receiver);
        StateMachineErrorContext errorContext = StateMachineParser.parseWithErrorContext(
                uri("errors/door/Door.java"), null, RefinementsParser.createAST("locked(#d_3)", ""), other)
                .errorContext();
        assertEquals(new StateMachineErrorContext(null, List.of("open"), "d", List.of("locked"), "locked(d)",
                "open(d) && x > 0", "open", List.of("open")), errorContext);
    }

    @Test
    public void testCalledConstructorAndStaticFactory() {
        CtClass<?> staticFactory = (CtClass<?>) buildType("StaticFactory.java");
        assertEquals("StaticFactory", calledMethod("StaticFactory.java",
                staticFactory.getConstructors().iterator().next().getPosition()));
        assertEquals("of", calledMethod("StaticFactory.java",
                staticFactory.getMethodsByName("of").get(0).getPosition()));
        assertEquals("close", calledMethod("StaticFactory.java",
                staticFactory.getMethodsByName("close").get(0).getPosition()));

        // interfaces declare constructors as methods named after the class
        CtType<?> refinements = buildType("errors/tree/DefaultMutableTreeNodeRefinements.java");
        assertEquals("DefaultMutableTreeNode", calledMethod("errors/tree/DefaultMutableTreeNodeRefinements.java",
                refinements.getMethodsByName("DefaultMutableTreeNode").get(0).getPosition()));

        // positions outside the state machine's type resolve to no method
        assertNull(calledMethod("errors/tree/DefaultMutableTreeNodeRefinements.java",
                staticFactory.getMethodsByName("close").get(0).getPosition()));
    }

    private static void assertStateMachineEquals(StateMachine expected, StateMachine actual) {
        assertNotNull(actual, "State machine should not be null");
        assertEquals(expected.className(), actual.className(), "Class names should match");
        assertEquals(expected.initialTransitions(), actual.initialTransitions().stream()
                .map(transition -> new StateMachineInitialTransition(transition.to(), transition.toCondition()))
                .toList(), "Initial transitions should match");
        assertEquals(expected.states(), actual.states(), "States should match");
        assertEquals(expected.transitions(), actual.transitions(), "State transitions should match");
        assertEquals(expected.errorContext(), actual.errorContext(), "Error contexts should match");
    }

    private static StateMachine stateMachine(String className, List<String> states,
            List<StateMachineTransition> transitions, List<StateMachineInitialTransition> initialTransitions) {
        String qualifiedName = className.contains(".") ? className : "fsm." + className;
        return new StateMachine(qualifiedName, states, transitions, initialTransitions, null);
    }

    private static StateRefinementError verifyStateError(String directory, String receiver) {
        CommandLineLauncher.launch(BASE_PATH.resolve(directory).toAbsolutePath().toString());
        return Diagnostics.getInstance().getErrors().stream()
                .filter(StateRefinementError.class::isInstance)
                .map(StateRefinementError.class::cast)
                .filter(error -> error.getFound().toString().contains("#" + receiver + "_"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No state refinement error found for " + receiver));
    }

    // same arguments as the vscode-liquidjava server
    private static StateMachine parseWithErrorContext(StateRefinementError error) {
        return StateMachineParser.parseWithErrorContext(
                new File(error.getDeclarationPosition().getFile().getPath()).toURI().toString(),
                error.getDeclarationPosition(), error.getExpected().getExpression(), error.getFound());
    }

    private static StateMachineErrorContext errorContext(String uri, String found) {
        CtTypeReference<?> doorType = new Launcher().getFactory().Type().createReference("fsm.errors.door.Door");
        VCImplication foundState = new VCImplication("d", doorType, predicate(found));
        return StateMachineParser.parseWithErrorContext(uri, null, RefinementsParser.createAST("locked(d)", ""),
                foundState).errorContext();
    }

    private static String definiteFoundState(String uri, String found) {
        return errorContext(uri, found).definiteFoundState();
    }

    private static List<String> possibleStates(String uri, String found) {
        return errorContext(uri, found).possibleStates();
    }

    private static Predicate predicate(String expr) {
        return new Predicate(RefinementsParser.createAST(expr, ""));
    }

    private static String calledMethod(String file, SourcePosition declarationPosition) {
        return StateMachineParser.parseWithErrorContext(uri(file), declarationPosition, null, null)
                .errorContext().calledMethod();
    }

    private static CtType<?> buildType(String file) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(BASE_PATH.resolve(file).toAbsolutePath().toString());
        launcher.buildModel();
        return launcher.getModel().getAllTypes().iterator().next();
    }
}
