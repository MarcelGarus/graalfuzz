package de.hpi.swa.coverage;

import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.source.SourceSection;

final class GatherSourceSectionsListener implements LoadSourceSectionListener {

    private final CoverageInstrument instrument;

    GatherSourceSectionsListener(CoverageInstrument instrument) {
        this.instrument = instrument;
    }

    @Override
    public void onLoad(LoadSourceSectionEvent event) {
        final SourceSection sourceSection = event.getSourceSection();
        instrument.addLoaded(sourceSection);
    }
}
