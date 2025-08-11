package de.hpi.swa.coverage;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.source.SourceSection;

// A node that wraps AST nodes of interest and informs the `CoverageInstrument`
// that we covered its source section.
//
// We use `onReturnValue`, which is executed after the node successfully
// evaluated.
//
// Truffle interprets the nodes, tracking the coverage in the `covered` field.
// If Truffle decides to JIT compile the code, we only keep the state that is
// absolutely necessary:
//
// - If the node has already been covered, covered is true. The
//   `CompilationFinal` annotation tells Truffle that this won't change,
//   so the node is optimized out – we don't have any runtime overhead for
//   tracking coverage.
// - If the node has not yet been covered, the
//   `transferToInterpreterAndInvalidate` call tells Truffle to go back to
//   interpreting. If the code is JITted later on, the coverage tracking is
//   optimized away.
final class CoverageNode extends ExecutionEventNode {

    private final CoverageInstrument instrument;
    @CompilerDirectives.CompilationFinal
    private boolean covered;

    private final SourceSection section;

    CoverageNode(CoverageInstrument instrument, SourceSection section) {
        this.instrument = instrument;
        this.section = section;
    }

    @Override
    public void onEnter(VirtualFrame frame) {
        // if (!covered) {
        // instrument.coverage.addCovered(section);
        // CompilerDirectives.transferToInterpreterAndInvalidate();
        // covered = true;
        // }
    }

    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {
        // if (!covered) {
        // CompilerDirectives.transferToInterpreterAndInvalidate();
        // covered = true;
        instrument.coverage.addCovered(section);
        // }
    }
}
