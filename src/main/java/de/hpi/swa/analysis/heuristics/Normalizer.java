package de.hpi.swa.analysis.heuristics;

import java.util.Collections;
import java.util.List;

public class Normalizer {

    public interface NormalizationStrategy {
        void prepare(List<Double> values);

        double normalize(double value);
    }

    public static class MinMaxNormalization implements NormalizationStrategy {
        /*
         * Problems:
         * - If max == min, all values are the same. We can return 1.0 in this case so
         *   it is still multipliable by a factor.
         * 
         * - If new values outside the prepared range are given, normalization will
         *   produce values < 0 or > 1.
         *   For now we will not pass values after preparation that are outside the range.
         */
        private double min;
        private double max;

        @Override
        public void prepare(List<Double> values) {
            this.min = Collections.min(values);
            this.max = Collections.max(values);
        }

        @Override
        public double normalize(double value) {
            if (max == min) {
                return 1.0;
            }
            return (value - min) / (max - min);
        }
    }
}