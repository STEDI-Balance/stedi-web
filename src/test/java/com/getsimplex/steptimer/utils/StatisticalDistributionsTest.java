package com.getsimplex.steptimer.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Expected values from scipy 1.18.1: stats.t.ppf(0.975, df), stats.chi2.ppf(0.025, df), stats.chi2.ppf(0.975, df)
class StatisticalDistributionsTest {

    @ParameterizedTest
    @CsvSource({
            "1, 12.706204736174694, 0.0009820691171752555, 5.02388618731489",
            "2, 4.302652729749462, 0.05063561596857975, 7.377758908227872",
            "5, 2.5705818356363146, 0.8312116134866626, 12.832501994030025",
            "9, 2.262157162798205, 2.7003894999803584, 19.02276779864163",
            "29, 2.045229642132704, 16.04707169536489, 45.722285804174525",
            "100, 1.983971518523552, 74.22192747492373, 129.5611971858366"
    })
    void quantilesMatchScipy(int df, double t975, double chiSquare025, double chiSquare975){
        assertEquals(t975, StatisticalDistributions.studentTQuantile(0.975, df), 1e-9);
        assertEquals(-t975, StatisticalDistributions.studentTQuantile(0.025, df), 1e-9);
        assertEquals(chiSquare025, StatisticalDistributions.chiSquareQuantile(0.025, df), 1e-9 * Math.max(1, chiSquare025));
        assertEquals(chiSquare975, StatisticalDistributions.chiSquareQuantile(0.975, df), 1e-9 * chiSquare975);
    }
}
