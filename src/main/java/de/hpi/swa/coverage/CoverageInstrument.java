package de.hpi.swa.coverage;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

@Registration(id = CoverageInstrument.ID, name = " Code Coverage", version = "0.1", services = CoverageInstrument.class)
public final class CoverageInstrument extends TruffleInstrument {

    @Option(name = "", help = "Enable  Coverage (default: false).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);

    public static final String ID = "code-coverage";

    public Coverage coverage = new Coverage();

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new CoverageInstrumentOptionDescriptors();
    }

    @Override
    protected void onCreate(Env env) {
        var options = env.getOptions();
        if (ENABLED.getValue(options)) {
            enable(env);
            env.registerService(this);
        }
    }

    private void enable(Env env) {
        var filter = SourceSectionFilter.newBuilder()
                .includeInternal(true)
                .build();
        var instrumenter = env.getInstrumenter();

        // Each time an AST node is created, this factory also creates a
        // wrapping node that tracks the coverage.
        instrumenter.attachExecutionEventFactory(filter, (ec) -> {
            var source = ec.getInstrumentedSourceSection();
            if (source != null) {
                return new CoverageNode(this, source);
            }
            return null;
        });
    }
}
