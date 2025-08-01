package de.hpi.swa.coverage;

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;

// Each time an AST node is created, this factory also creates a wrapping node
// that tracks the coverage.
final class CoverageEventFactory implements ExecutionEventNodeFactory {

    private final CoverageInstrument instrument;

    CoverageEventFactory(CoverageInstrument instrument) {
        System.out.println("factory created.");
        this.instrument = instrument;
    }

    @Override
    public ExecutionEventNode create(final EventContext ec) {
        System.out.println("creating coverage tracking node.");
        return new CoverageNode(instrument, ec.getInstrumentedSourceSection());
    }
}
