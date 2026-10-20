package fsm;

import java.util.UUID;

import liquidjava.specification.ExternalRefinementsFor;
import liquidjava.specification.Refinement;
import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@ExternalRefinementsFor("java.util.UUID")
@StateSet({"dceSecurityNameRandom", "timeBased"})
public interface ExternalFactoryOnly {

    @Refinement("timeBased(return)")
    UUID fromString(String name);

    @StateRefinement(from="timeBased(this)")
    long timestamp();
}
