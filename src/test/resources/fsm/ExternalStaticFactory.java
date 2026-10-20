package fsm;

import java.util.UUID;

import liquidjava.specification.ExternalRefinementsFor;
import liquidjava.specification.Refinement;
import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@ExternalRefinementsFor("java.util.UUID")
@StateSet({"dceSecurityNameRandom", "maybeTime", "timeBased"})
public interface ExternalStaticFactory {

    @StateRefinement(to="((mostSigBits/4096) % 16 == 1) ? timeBased(this) : dceSecurityNameRandom(this)")
    void UUID(long mostSigBits, long leastSigBits);

    @Refinement("maybeTime(return)")
    UUID fromString(String name);

    @Refinement("dceSecurityNameRandom(return)")
    UUID randomUUID();

    @StateRefinement(from="timeBased(this)")
    long timestamp();
}
