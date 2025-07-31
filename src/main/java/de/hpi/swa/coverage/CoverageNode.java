package de.hpi.swa.coverage;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.source.SourceSection;

final class CoverageNode extends ExecutionEventNode {

    private final CoverageInstrument instrument;
    @CompilerDirectives.CompilationFinal
    private boolean covered;

    private final SourceSection instrumentedSourceSection;

    CoverageNode(CoverageInstrument instrument, SourceSection instrumentedSourceSection) {
        this.instrument = instrument;
        this.instrumentedSourceSection = instrumentedSourceSection;
    }

    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {
        if (!covered) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            covered = true;
            instrument.addCovered(instrumentedSourceSection);
        }
    }
}
