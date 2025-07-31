package de.hpi.swa.coverage;

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.source.SourceSection;

final class CoverageEventFactory implements ExecutionEventNodeFactory {

    private CoverageInstrument instrument;

    CoverageEventFactory(CoverageInstrument instrument) {
        this.instrument = instrument;
    }

    @Override
    public ExecutionEventNode create(final EventContext ec) {
        return new CoverageNode(instrument, ec.getInstrumentedSourceSection());
    }
}
