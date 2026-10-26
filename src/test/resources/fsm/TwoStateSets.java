package fsm;

import liquidjava.specification.StateRefinement;
import liquidjava.specification.StateSet;

@StateSet({"startTiling", "tilingExplicit", "tilingSet"})
@StateSet({"startCompression", "compressionExplicit", "compressionSet"})
public class TwoStateSets {

    // sets the state of both state sets
    @StateRefinement(to="startTiling(this) && startCompression(this)")
    public TwoStateSets() {}

    // only sets the tiling state, the compression state starts in the first state
    @StateRefinement(to="tilingExplicit(this)")
    public TwoStateSets(int mode) {}

    @StateRefinement(to="mode == 2 ? tilingExplicit(this) : startTiling(this)")
    public void setTilingMode(int mode) {}

    @StateRefinement(from="tilingExplicit(this)", to="tilingSet(this)")
    public void setTiling(int width) {}

    @StateRefinement(to="mode == 2 ? compressionExplicit(this) : startCompression(this)")
    public void setCompressionMode(int mode) {}

    @StateRefinement(from="compressionExplicit(this)", to="compressionSet(this)")
    public void setCompressionType(String type) {}

    // requires a state of each state set
    @StateRefinement(from="tilingSet(this) && compressionSet(this)")
    public void write() {}

    // requires a compression state and changes the tiling state
    @StateRefinement(from="compressionSet(this)", to="startTiling(this)")
    public void resetTiling() {}

    // does not refer to any state
    public void setProgressiveMode(int mode) {}
}
