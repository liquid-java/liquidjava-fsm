package liquidjava.fsm;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import liquidjava.rj_language.ast.*;
import liquidjava.rj_language.parsing.RefinementsParser;
import liquidjava.utils.Utils;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

public class StateMachineParser {

    private static final String STATE_SET_ANNOTATION = "StateSet";
    private static final String STATE_REFINEMENT_ANNOTATION = "StateRefinement";
    private static final String REFINEMENT_ANNOTATION = "Refinement";
    private static final String EXTERNAL_REFINEMENTS_FOR_ANNOTATION = "ExternalRefinementsFor";

    private record TransitionSource(String from, String cond) {}

    // name is null when the state set has none
    private record StateSet(String name, List<String> states) {}

    /**
     * Parses a class or interface for the given uri and extracts the state machine information
     * @param uri the document URI
     * @return StateMachine or null if none found
     */
    public static StateMachine parse(String uri) {
        return parse(uri, null);
    }

    /**
     * Parses the class or interface with the given name in the given uri, for files that declare several
     * @param uri the document URI
     * @param className the qualified class name, or the name given by ExternalRefinementsFor
     * @return StateMachine of the first state set with transitions, or null if none found
     */
    public static StateMachine parse(String uri, String className) {
        List<StateMachine> stateMachines = parseAll(uri, className);
        return stateMachines.isEmpty() ? null : stateMachines.get(0);
    }

    /**
     * Parses a class or interface for the given uri and extracts one state machine per state set
     * @param uri the document URI
     * @return the state machines of the state sets with transitions, in declaration order
     */
    public static List<StateMachine> parseAll(String uri) {
        return parseAll(uri, null);
    }

    /**
     * Parses the class or interface with the given name in the given uri and extracts one state machine per state set
     * Each state set changes independently, so a state machine only has the transitions that mention its states
     * @param uri the document URI
     * @param className the qualified class name, or the name given by ExternalRefinementsFor
     * @return the state machines of the state sets with transitions, in declaration order
     */
    public static List<StateMachine> parseAll(String uri, String className) {
        return parseAll(List.of(uri), className);
    }

    /**
     * Parses the class or interface with the given name in the given uris, with the state sets and methods it inherits
     * from the specs of its supertypes in them, and extracts one state machine per state set
     * As in the verifier, the state sets of the class come first, then those of its supertypes, nearest first, and a
     * call uses the class's own refinement of a method, otherwise that of the nearest type that declares the method
     * @param uris the document URIs
     * @param className the qualified class name, or the name given by ExternalRefinementsFor
     * @return the state machines of the state sets with transitions, in declaration order
     */
    public static List<StateMachine> parseAll(List<String> uris, String className) {
        try {
            CtModel model = buildModel(uris);

            // get class or interface
            CtType<?> ctType = getType(model, className);
            if (ctType == null)
                return List.of(); // no class or interface found

            // extract class name, state sets and methods, with those inherited from the specs of the supertypes
            List<CtTypeReference<?>> supertypes = getSupertypes(model, ctType);
            List<CtType<?>> specs = getSpecs(model, ctType, supertypes);
            List<StateSet> stateSets = specs.stream().flatMap(spec -> getStateSets(spec).stream()).toList();
            List<CtMethod<?>> methods = getMethods(specs, supertypes);
            String qualifiedName = getClassName(ctType);
            List<StateMachine> stateMachines = new ArrayList<>();
            for (int i = 0; i < stateSets.size(); i++) {
                List<String> otherStates = new ArrayList<>();
                for (int j = 0; j < stateSets.size(); j++) {
                    if (j != i)
                        otherStates.addAll(stateSets.get(j).states());
                }
                StateMachine sm = parseStateSet(ctType, methods, qualifiedName, i, stateSets.get(i), otherStates);
                if (sm != null)
                    stateMachines.add(sm);
            }
            return stateMachines;

        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    // a file that no longer exists is skipped, so that it does not discard the specs in the others
    private static CtModel buildModel(List<String> uris) throws URISyntaxException {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        for (String uri : uris.stream().distinct().toList()) {
            Path path = Paths.get(new URI(uri));
            if (Files.isRegularFile(path))
                launcher.addInputResource(path.toString());
        }
        launcher.buildModel();
        return launcher.getModel();
    }

    /**
     * Extracts the state machine of one state set
     * @param ctType the CtType (class or interface)
     * @param methods the methods of the class, with those it inherits
     * @param qualifiedName the class name
     * @param stateSet the index of the state set
     * @param set the state set
     * @param otherStates the states of the other state sets
     * @return StateMachine or null if the state set has no transitions
     */
    private static StateMachine parseStateSet(CtType<?> ctType, List<CtMethod<?>> methods, String qualifiedName,
            int stateSet, StateSet set, List<String> otherStates) {
        List<String> states = set.states();
        if (states.isEmpty())
            return null; // no states found
        String simpleClassName = Utils.getSimpleName(qualifiedName);

        // get initial transitions and method transitions
        List<StateMachineInitialTransition> factoryTransitions = getFactoryTransitions(ctType, simpleClassName, states, otherStates);
        List<StateMachineInitialTransition> initialTransitions = getInitialTransitions(ctType, simpleClassName, states, otherStates);
        // interfaces only have an implicit initial state when there are no factory methods
        if (initialTransitions.isEmpty() && (ctType instanceof CtClass<?> || factoryTransitions.isEmpty())) {
            initialTransitions = getDefaultInitialTransitions(ctType, simpleClassName, states.get(0));
        }
        initialTransitions = new ArrayList<>(initialTransitions);
        initialTransitions.addAll(factoryTransitions);
        List<StateMachineEntryTransition> entryTransitions = new ArrayList<>();
        List<StateMachineTransition> transitions = getTransitions(methods, states, otherStates, entryTransitions);
        if (transitions.isEmpty() && entryTransitions.isEmpty())
            return null; // no transitions found

        return new StateMachine(qualifiedName, stateSet, set.name(), states, transitions, initialTransitions,
                entryTransitions);
    }

    /**
     * Finds the class or interface in the model with the given name, or the first one if the name is null
     * @param model the CtModel
     * @param className the qualified class name, or null
     * @return CtType or null if none found
     */
    private static CtType<?> getType(CtModel model, String className) {
        for (CtType<?> type : model.getAllTypes()) {
            if ((type.isClass() || type.isInterface()) && (className == null || className.equals(getClassName(type))))
                return type;
        }
        return null;
    }

    /**
     * Gets the supertypes of a class whose specs it may inherit, nearest first: its superclasses, then the interfaces
     * they implement, as in the verifier
     * @param model the CtModel
     * @param ctType the CtType (class or interface)
     * @return the supertypes, or none if the model has no other type with a spec
     */
    private static List<CtTypeReference<?>> getSupertypes(CtModel model, CtType<?> ctType) {
        if (model.getAllTypes().size() == 1)
            return List.of();
        CtTypeReference<?> type = getClassReference(ctType);
        List<CtTypeReference<?>> superclasses = new ArrayList<>();
        Deque<CtTypeReference<?>> interfaces = new ArrayDeque<>();
        Set<String> seen = new HashSet<>(Set.of(type.getQualifiedName()));
        for (CtTypeReference<?> t = type; t != null; t = getSuperclass(t)) {
            if (t != type && seen.add(t.getQualifiedName()))
                superclasses.add(t);
            interfaces.addAll(getSuperInterfaces(t));
        }
        List<CtTypeReference<?>> supertypes = new ArrayList<>(superclasses);
        while (!interfaces.isEmpty()) {
            CtTypeReference<?> t = interfaces.poll();
            if (seen.add(t.getQualifiedName())) {
                supertypes.add(t);
                interfaces.addAll(getSuperInterfaces(t));
            }
        }
        return supertypes;
    }

    // a supertype that cannot be resolved (no source, not on the classpath) ends the walk on that branch
    private static CtTypeReference<?> getSuperclass(CtTypeReference<?> type) {
        try {
            return type.getSuperclass();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Set<CtTypeReference<?>> getSuperInterfaces(CtTypeReference<?> type) {
        try {
            return type.getSuperInterfaces();
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    /**
     * Gets the reference to the class a class or interface specifies
     * @param ctType the CtType (class or interface)
     * @return the reference to the class named by ExternalRefinementsFor, or to the class itself
     */
    private static CtTypeReference<?> getClassReference(CtType<?> ctType) {
        return ctType.getFactory().Type().createReference(getClassName(ctType));
    }

    /**
     * Gets the specs of a class and of its supertypes in the model
     * @param model the CtModel
     * @param ctType the CtType (class or interface) of the class
     * @param supertypes the supertypes of the class, nearest first
     * @return the CtType of the class, followed by those of its supertypes that have one
     */
    private static List<CtType<?>> getSpecs(CtModel model, CtType<?> ctType, List<CtTypeReference<?>> supertypes) {
        List<CtType<?>> specs = new ArrayList<>(List.of(ctType));
        for (CtTypeReference<?> supertype : supertypes) {
            CtType<?> spec = getType(model, supertype.getQualifiedName());
            if (spec != null)
                specs.add(spec);
        }
        return specs;
    }

    /**
     * Gets the methods of a class and those it inherits from the specs of its supertypes, as the verifier applies them
     * An inherited method is left out if a nearer spec refines it, if the nearest type that declares it is not the one
     * it is specified for, or if not every caller can call it
     * @param specs the CtType of the class, followed by those of its supertypes
     * @param supertypes the supertypes of the class, nearest first
     * @return the methods, without the constructors of interfaces
     */
    private static List<CtMethod<?>> getMethods(List<CtType<?>> specs, List<CtTypeReference<?>> supertypes) {
        CtType<?> ctType = specs.get(0);
        List<CtTypeReference<?>> hierarchy = new ArrayList<>(List.of(getClassReference(ctType)));
        hierarchy.addAll(supertypes);
        List<CtMethod<?>> methods = new ArrayList<>(getDeclaredMethods(ctType));
        for (CtType<?> spec : specs.subList(1, specs.size())) {
            for (CtMethod<?> method : getDeclaredMethods(spec)) {
                if (!isRefinedIn(method, methods) && resolvesTo(method, spec, hierarchy) && isPublic(method, spec))
                    methods.add(method);
            }
        }
        return methods;
    }

    /**
     * Gets the methods of a class or interface, without the methods of interfaces that stand for constructors
     * @param ctType the CtType (class or interface)
     * @return the methods
     */
    private static List<CtMethod<?>> getDeclaredMethods(CtType<?> ctType) {
        String className = Utils.getSimpleName(getClassName(ctType));
        return ctType.getMethods().stream()
                .filter(method -> !(ctType.isInterface() && method.getSimpleName().equals(className))).toList();
    }

    private static boolean isRefinedIn(CtMethod<?> method, List<CtMethod<?>> methods) {
        return methods.stream().anyMatch(m -> m.getSimpleName().equals(method.getSimpleName())
                && getQualifiedParameterTypes(m).equals(getQualifiedParameterTypes(method)));
    }

    private static List<String> getQualifiedParameterTypes(CtMethod<?> method) {
        return method.getParameters().stream()
                .map(parameter -> parameter.getType() == null ? "" : parameter.getType().getQualifiedName()).toList();
    }

    /**
     * Checks whether the nearest type in the hierarchy that declares a method is the one a spec is for, which is where
     * the verifier looks up the refinement of a call
     * @param method the method of the spec
     * @param spec the CtType (class or interface) of the spec
     * @param hierarchy the class, followed by its supertypes, nearest first
     * @return whether a call on the class uses the spec's refinement of the method
     */
    private static boolean resolvesTo(CtMethod<?> method, CtType<?> spec, List<CtTypeReference<?>> hierarchy) {
        for (CtTypeReference<?> type : hierarchy) {
            CtType<?> declaration = type.getTypeDeclaration();
            if (declaration == null)
                return false; // cannot tell which type declares it
            if (getDeclarations(method, declaration).findAny().isPresent())
                return type.getQualifiedName().equals(getClassName(spec));
        }
        return false;
    }

    /**
     * Checks whether every caller can call a method on a subtype of the class a spec is for, since the diagram is
     * shared by every caller
     * @param method the method of the spec
     * @param spec the CtType (class or interface) of the spec
     * @return whether the method is public in the class
     */
    private static boolean isPublic(CtMethod<?> method, CtType<?> spec) {
        CtType<?> declaring = getClassReference(spec).getTypeDeclaration();
        if (declaring == null)
            return false; // cannot tell whether it is public
        // interface methods are public unless declared private
        return getDeclarations(method, declaring).allMatch(m -> declaring.isInterface() ? !m.isPrivate() : m.isPublic());
    }

    /**
     * Gets the methods of a type that a method of a spec may refer to: those with its name and number of parameters
     */
    private static Stream<CtMethod<?>> getDeclarations(CtMethod<?> method, CtType<?> type) {
        return type.getMethods().stream().filter(m -> m.getSimpleName().equals(method.getSimpleName())
                && m.getParameters().size() == method.getParameters().size());
    }

    /**
     * Gets the qualified name from a class or interface
     * Uses name from ExternalRefinementsFor if present, otherwise uses class or interface name
     * @param ctType the CtType (class or interface)
     * @return class name
     */
    private static String getClassName(CtType<?> ctType) {
        for (CtAnnotation<?> annotation : ctType.getAnnotations()) {
            if (annotation.getAnnotationType().getSimpleName().equals(EXTERNAL_REFINEMENTS_FOR_ANNOTATION)) {
                String qualifiedName = (String) annotation.getValueAsObject("value");
                return qualifiedName;
            }
        }
        return ctType.getQualifiedName();
    }

    /**
     * Gets the state sets from a class or interface
     * @param ctType the CtType (class or interface)
     * @return list of state sets, in declaration order
     */
    private static List<StateSet> getStateSets(CtType<?> ctType) {
        List<StateSet> stateSets = new ArrayList<>();
        for (CtAnnotation<?> annotation : ctType.getAnnotations()) {
            if (annotation.getAnnotationType().getSimpleName().equals(STATE_SET_ANNOTATION)) {
                String[] stateArray = (String[]) annotation.getValueAsObject("value");
                stateSets.add(new StateSet(getStateSetName(annotation), List.of(stateArray)));
            }
        }
        return stateSets;
    }

    // an empty name is no name, as in the verifier
    private static String getStateSetName(CtAnnotation<?> annotation) {
        return annotation.getValues().get("name") instanceof CtLiteral<?> literal
                && literal.getValue() instanceof String name && !name.isEmpty() ? name : null;
    }

    /**
     * Gets the elements that represent constructors (actual constructors for classes, methods named after the class for interfaces)
     * @param ctType the CtType (class or interface)
     * @param className the class name
     * @return collection of constructor elements
     */
    private static Collection<? extends CtExecutable<?>> getConstructorElements(CtType<?> ctType, String className) {
        if (ctType instanceof CtClass<?> ctClass) {
            return ctClass.getConstructors();
        }
        // for interfaces the constructors are methods with the same name as the class
        return ctType.getMethods().stream().filter(m -> m.getSimpleName().equals(className)).toList();
    }

    /**
     * Gets the initial transitions from a class or interface
     * If not explicitly defined, uses the first state in the state set
     * @param ctType the CtType (class or interface)
     * @param className the class name
     * @param states the list of states
     * @param otherStates the states of the other state sets
     * @return initial transitions
     */
    private static List<StateMachineInitialTransition> getInitialTransitions(CtType<?> ctType, String className,
            List<String> states, List<String> otherStates) {
        List<StateMachineInitialTransition> initialTransitions = new ArrayList<>();
        for (CtExecutable<?> element : getConstructorElements(ctType, className)) {
            String signature = getConstructorSignature(element, className);
            boolean hasExplicitInitialState = false;
            for (CtAnnotation<?> annotation : element.getAnnotations()) {
                if (annotation.getAnnotationType().getSimpleName().equals(STATE_REFINEMENT_ANNOTATION)) {
                    String to = annotation.getValueAsString("to");
                    List<StateMachineInitialTransition> transitions = parseInitialTransitions(to, states, otherStates, signature);
                    initialTransitions.addAll(transitions);
                    hasExplicitInitialState |= !transitions.isEmpty();
                }
            }
            if (!hasExplicitInitialState) {
                initialTransitions.add(new StateMachineInitialTransition(states.get(0), null, signature));
            }
        }
        return initialTransitions;
    }

    private static List<StateMachineInitialTransition> getDefaultInitialTransitions(CtType<?> ctType,
            String className, String firstState) {
        Collection<? extends CtExecutable<?>> constructors = getConstructorElements(ctType, className);
        if (constructors.isEmpty()) {
            String implicitConstructor = ctType instanceof CtClass<?> ? "new " + className + "()" : null;
            return List.of(new StateMachineInitialTransition(firstState, null, implicitConstructor));
        }
        return constructors.stream()
                .map(element -> new StateMachineInitialTransition(firstState, null,
                        getConstructorSignature(element, className)))
                .toList();
    }

    private static String getConstructorSignature(CtExecutable<?> element, String className) {
        return "new " + className + "(" + getParameterTypes(element) + ")";
    }

    /**
     * Gets the initial transitions from static factory methods (methods that return a new object in a given state)
     * For classes these must be static, for interfaces any method returning the class type is considered
     * @param ctType the CtType (class or interface)
     * @param className the class name
     * @param states the list of states
     * @param otherStates the states of the other state sets
     * @return initial transitions
     */
    private static List<StateMachineInitialTransition> getFactoryTransitions(CtType<?> ctType, String className,
            List<String> states, List<String> otherStates) {
        List<StateMachineInitialTransition> initialTransitions = new ArrayList<>();
        for (CtMethod<?> method : ctType.getMethods()) {
            if (ctType.isClass() && !method.isStatic())
                continue;
            if (method.getType() == null || !method.getType().getSimpleName().equals(className))
                continue;
            String signature = className + "." + method.getSimpleName() + "(" + getParameterTypes(method) + ")";
            for (CtAnnotation<?> annotation : method.getAnnotations()) {
                if (annotation.getAnnotationType().getSimpleName().equals(REFINEMENT_ANNOTATION)) {
                    String refinement = annotation.getValueAsString("value");
                    initialTransitions.addAll(parseInitialTransitions(refinement, states, otherStates, signature));
                }
            }
        }
        return initialTransitions;
    }

    private static String getParameterTypes(CtExecutable<?> element) {
        return String.join(", ", element.getParameters().stream()
                .map(parameter -> parameter.getType() == null ? "?" : parameter.getType().getSimpleName())
                .toList());
    }

    /**
     * Gets transitions from the methods of a class or interface
     * @param methods the methods, without the constructors of interfaces
     * @param states the list of states
     * @param otherStates the states of the other state sets
     * @param entryTransitions the list where the entry transitions are added
     * @return list of StateMachineTransition
     */
    private static List<StateMachineTransition> getTransitions(List<CtMethod<?>> methods, List<String> states,
            List<String> otherStates, List<StateMachineEntryTransition> entryTransitions) {
        List<StateMachineTransition> transitions = new ArrayList<>();
        for (CtMethod<?> method : methods) {
            // the verifier uses the first StateRefinement whose from matches, so the states that an earlier from
            // always matches are not reached by the later ones
            List<String> coveredStates = new ArrayList<>();
            for (CtAnnotation<?> annotation : method.getAnnotations()) {
                if (annotation.getAnnotationType().getSimpleName().equals(STATE_REFINEMENT_ANNOTATION)) {
                    transitions.addAll(getTransitions(annotation, method.getSimpleName(), states, otherStates,
                            coveredStates, entryTransitions));
                }
            }
        }
        return transitions;
    }

    /**
     * Gets transitions from the given annotation
     * A StateRefinement without from goes from every state that an earlier StateRefinement of the method does not
     * cover, and when some are covered it is also an entry transition for objects in an unknown state
     * @param ann the CtAnnotation
     * @param method the method name
     * @param states the list of states
     * @param otherStates the states of the other state sets
     * @param coveredStates the states covered by the earlier StateRefinements of the method, updated with this one
     * @param entryTransitions the list where the entry transitions are added
     * @return list of StateMachineTransition
     */
    private static List<StateMachineTransition> getTransitions(CtAnnotation<?> ann, String method, List<String> states,
            List<String> otherStates, List<String> coveredStates, List<StateMachineEntryTransition> entryTransitions) {
        List<StateMachineTransition> transitions = new ArrayList<>();
        String from = ann.getValueAsString("from");
        String to = ann.getValueAsString("to");
        boolean fromMentionsStates = mentionsStates(from, states);
        boolean toMentionsStates = mentionsStates(to, states);
        // the states of this state set do not change
        if (!fromMentionsStates && !toMentionsStates)
            return transitions;

        // parse from and to expressions, the states of the other state sets in from are kept as conditions
        List<TransitionSource> fromSources;
        if (from.isEmpty()) {
            fromSources = allStateSources(states).stream()
                    .filter(source -> !coveredStates.contains(source.from())).toList();
        } else {
            fromSources = parsePrecondition(from, states);
            fromSources.stream().filter(source -> source.cond() == null).map(TransitionSource::from)
                    .forEach(coveredStates::add);
        }
        List<TransitionSource> toSources = fromMentionsStates && !toMentionsStates
                ? parseStateSources(from, states)
                : parsePostcondition(to, states, otherStates);

        if (from.isEmpty() && !coveredStates.isEmpty()) {
            for (TransitionSource toSource : toSources) {
                entryTransitions.add(new StateMachineEntryTransition(toSource.from(), method, toSource.cond()));
            }
        }

        // create transitions for each combination of from and to states
        for (TransitionSource fromSource : fromSources) {
            for (TransitionSource toSource : toSources) {
                transitions.add(new StateMachineTransition(fromSource.from(), toSource.from(), method, fromSource.cond(), toSource.cond()));
            }
        }
        return transitions;
    }

    private static List<TransitionSource> allStateSources(List<String> states) {
        List<TransitionSource> sources = new ArrayList<>();
        for (String state : states) {
            sources.add(new TransitionSource(state, null));
        }
        return sources;
    }

    private static List<TransitionSource> parsePrecondition(String expr, List<String> states) {
        Expression ast = RefinementsParser.createAST(expr, "");
        List<TransitionSource> sources = getTransitionSources(ast, states, false);
        if (sources.isEmpty()) {
            sources = addCondition(allStateSources(states), ast.toString());
        }
        return sources;
    }

    private static List<TransitionSource> parsePostcondition(String expr, List<String> states, List<String> otherStates) {
        if (expr == null || expr.isEmpty()) return new ArrayList<>();
        Expression ast = withoutStates(RefinementsParser.createAST(expr, ""), otherStates);
        if (ast == null) return new ArrayList<>();
        return getTransitionSources(ast, states, true);
    }

    /**
     * Removes the conjuncts that set a state of another state set, which change at the same time and are not conditions
     * @param expr the postcondition
     * @param otherStates the states of the other state sets
     * @return the postcondition without them, or null if nothing is left
     */
    private static Expression withoutStates(Expression expr, List<String> otherStates) {
        if (getStateName(expr, otherStates) != null) {
            return null;
        }
        if (expr instanceof BinaryExpression bin && bin.getOperator().equals("&&")) {
            Expression left = withoutStates(bin.getFirstOperand(), otherStates);
            Expression right = withoutStates(bin.getSecondOperand(), otherStates);
            if (left == null) return right;
            if (right == null) return left;
            return new BinaryExpression(left, "&&", right);
        } else if (expr instanceof Ite ite) {
            Expression then = withoutStates(ite.getThen(), otherStates);
            Expression otherwise = withoutStates(ite.getElse(), otherStates);
            return new Ite(ite.getCondition(), then == null ? ite.getThen() : then, otherwise == null ? ite.getElse() : otherwise);
        }
        return expr;
    }

    private static boolean mentionsStates(String expr, List<String> states) {
        if (expr == null || expr.isEmpty()) return false;
        return !getStateExpressions(RefinementsParser.createAST(expr, ""), states).isEmpty();
    }

    private static List<TransitionSource> getTransitionSources(Expression expr, List<String> states, boolean stateOnlyDisjunctions) {
        String state = getStateName(expr, states);
        if (state != null) {
            return List.of(new TransitionSource(state, null));
        }

        Expression negated = getNegated(expr);
        if (negated != null) {
            List<String> negatedStates = getStateExpressions(negated, states);
            if (!negatedStates.isEmpty()) {
                List<TransitionSource> sources = new ArrayList<>();
                for (String possibleState : states) {
                    if (!negatedStates.contains(possibleState)) {
                        sources.add(new TransitionSource(possibleState, null));
                    }
                }
                return sources;
            }
        }

        if (expr instanceof BinaryExpression bin) {
            String op = bin.getOperator();
            if (op.equals("&&")) {
                return getConjunctionSources(bin, states, stateOnlyDisjunctions);
            } else if (op.equals("||")) {
                return stateOnlyDisjunctions ? getStateSources(bin, states) : getDisjunctionSources(bin, states);
            }
        } else if (expr instanceof Ite ite) {
            List<TransitionSource> sources = new ArrayList<>();
            sources.addAll(addCondition(getTransitionSources(ite.getThen(), states, stateOnlyDisjunctions), ite.getCondition().toString()));
            sources.addAll(addCondition(getTransitionSources(ite.getElse(), states, stateOnlyDisjunctions), negateCondition(ite.getCondition())));
            return sources;
        }
        return new ArrayList<>();
    }

    private static List<TransitionSource> getConjunctionSources(BinaryExpression bin, List<String> states, boolean stateOnlyDisjunctions) {
        List<TransitionSource> leftSources = getTransitionSources(bin.getFirstOperand(), states, stateOnlyDisjunctions);
        List<TransitionSource> rightSources = getTransitionSources(bin.getSecondOperand(), states, stateOnlyDisjunctions);

        if (leftSources.isEmpty() && rightSources.isEmpty()) {
            return new ArrayList<>();
        } else if (leftSources.isEmpty()) {
            return addCondition(rightSources, bin.getFirstOperand().toString());
        } else if (rightSources.isEmpty()) {
            return addCondition(leftSources, bin.getSecondOperand().toString());
        }

        List<TransitionSource> sources = new ArrayList<>(leftSources);
        sources.addAll(rightSources);
        return sources;
    }

    private static List<TransitionSource> getDisjunctionSources(BinaryExpression bin, List<String> states) {
        List<TransitionSource> sources = new ArrayList<>();
        addDisjunctionBranch(sources, bin.getFirstOperand(), states);
        addDisjunctionBranch(sources, bin.getSecondOperand(), states);
        return removeRedundantGuardedSources(sources);
    }

    private static void addDisjunctionBranch(List<TransitionSource> sources, Expression expr, List<String> states) {
        List<TransitionSource> parsedSources = getTransitionSources(expr, states, false);
        if (parsedSources.isEmpty()) {
            sources.addAll(addCondition(allStateSources(states), expr.toString()));
        } else {
            sources.addAll(parsedSources);
        }
    }

    private static List<TransitionSource> removeRedundantGuardedSources(List<TransitionSource> sources) {
        List<TransitionSource> filteredSources = new ArrayList<>();
        for (TransitionSource source : sources) {
            if (source.cond() == null || !hasUnguardedSource(sources, source.from())) {
                filteredSources.add(source);
            }
        }
        return filteredSources;
    }

    private static boolean hasUnguardedSource(List<TransitionSource> sources, String from) {
        for (TransitionSource source : sources) {
            if (source.from().equals(from) && source.cond() == null) {
                return true;
            }
        }
        return false;
    }

    private static List<TransitionSource> addCondition(List<TransitionSource> sources, String cond) {
        List<TransitionSource> guardedSources = new ArrayList<>();
        for (TransitionSource source : sources) {
            guardedSources.add(new TransitionSource(source.from(), combineConditions(source.cond(), cond)));
        }
        return guardedSources;
    }

    private static String combineConditions(String first, String second) {
        if (first == null || first.isEmpty()) {
            return second;
        }
        if (second == null || second.isEmpty()) {
            return first;
        }
        return first + " && " + second;
    }

    private static String negateCondition(Expression expr) {
        String condition = expr.toString();
        if (expr instanceof Var || expr instanceof FunctionInvocation) {
            return "!" + condition;
        }
        return "!(" + condition + ")";
    }

    private static List<StateMachineInitialTransition> parseInitialTransitions(String expr, List<String> states,
            List<String> otherStates, String constructorSignature) {
        if (expr == null || expr.isEmpty()) return new ArrayList<>();
        List<StateMachineInitialTransition> initialTransitions = new ArrayList<>();
        for (TransitionSource source : parsePostcondition(expr, states, otherStates)) {
            initialTransitions.add(new StateMachineInitialTransition(source.from(), source.cond(), constructorSignature));
        }
        return initialTransitions;
    }

    private static List<TransitionSource> parseStateSources(String expr, List<String> states) {
        if (expr == null || expr.isEmpty()) return new ArrayList<>();
        Expression ast = RefinementsParser.createAST(expr, "");
        return getStateSources(ast, states);
    }

    private static List<TransitionSource> getStateSources(Expression expr, List<String> states) {
        List<TransitionSource> sources = new ArrayList<>();
        for (String state : getStateExpressions(expr, states)) {
            sources.add(new TransitionSource(state, null));
        }
        return sources;
    }

    /**
     * Gets state names from an expression AST recursively
     * @param expr the expression
     * @param states the list of possible states
     * @return list of states
     */
    private static List<String> getStateExpressions(Expression expr, List<String> states) {
        List<String> stateExpressions = new ArrayList<>();
        String state = getStateName(expr, states);
        Expression negated = getNegated(expr);
        if (state != null) {
            stateExpressions.add(state);
        } else if (negated != null) {
            // all except those in the expression
            List<String> negatedStates = getStateExpressions(negated, states);
            if (!negatedStates.isEmpty()) {
                for (String possibleState : states) {
                    if (!negatedStates.contains(possibleState)) {
                        stateExpressions.add(possibleState);
                    }
                }
            }
        } else if (expr instanceof BinaryExpression bin) {
            stateExpressions.addAll(getStateExpressions(bin.getFirstOperand(), states));
            stateExpressions.addAll(getStateExpressions(bin.getSecondOperand(), states));
        } else if (expr instanceof Ite ite) {
            // combine states from then and else branches
            stateExpressions.addAll(getStateExpressions(ite.getThen(), states));
            stateExpressions.addAll(getStateExpressions(ite.getElse(), states));
        }
        return stateExpressions;
    }

    /**
     * Gets the state an expression is in: open(this), or open written as a value of its state set, as in
     * phase(this) == open or open == phase(this), which the verifier reads as open(this)
     */
    private static String getStateName(Expression expr, List<String> states) {
        if (expr instanceof Var var) {
            return findState(var.getName(), states);
        } else if (expr instanceof FunctionInvocation func) {
            return findState(func.getName(), states);
        } else if (expr instanceof BinaryExpression bin && bin.getOperator().equals("==")) {
            String state = getStateValue(bin.getFirstOperand(), bin.getSecondOperand(), states);
            return state != null ? state : getStateValue(bin.getSecondOperand(), bin.getFirstOperand(), states);
        }
        return null;
    }

    private static String getStateValue(Expression stateSet, Expression value, List<String> states) {
        return stateSet instanceof FunctionInvocation func && func.getArgs().size() == 1 && value instanceof Var var
                ? findState(var.getName(), states)
                : null;
    }

    /**
     * Gets what a negation negates: e in !e, or a == b in a != b, so that phase(this) != open is read as !open(this)
     * as in the verifier
     * @return the negated expression, or null if expr is not a negation
     */
    private static Expression getNegated(Expression expr) {
        if (expr instanceof UnaryExpression unary && unary.getOp().equals("!")) {
            return unary.getExpression();
        } else if (expr instanceof BinaryExpression bin && bin.getOperator().equals("!=")) {
            return new BinaryExpression(bin.getFirstOperand(), "==", bin.getSecondOperand());
        }
        return null;
    }

    private static String findState(String name, List<String> states) {
        if (states.contains(name)) {
            return name;
        }
        String simpleName = Utils.getSimpleName(name);
        for (String state : states) {
            if (state.equals(simpleName) || Utils.getSimpleName(state).equals(simpleName)) {
                return state;
            }
        }
        return null;
    }
}
