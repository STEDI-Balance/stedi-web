package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.ConfidenceInterval;
import com.getsimplex.steptimer.model.PairedDifferenceStatistics;
import com.getsimplex.steptimer.utils.StatisticalDistributions;

import java.util.Optional;

/**
 * Test-retest reliability for independent score pairs (one pair per participant).
 *
 * MDC95 = 1.96 x sqrt(2) x SEM, with SEM = SD(differences) / sqrt(2), which is the
 * two-occasion design used by Goldberg and Talley (2015). The SD is taken around the mean
 * difference, so a systematic shift (such as learning) is reported as the mean difference and
 * does not inflate SEM or MDC95.
 */
public class MdcStatistics {

    public static final double Z_95 = 1.96;

    // Returns empty when fewer than 2 pairs make variability inestimable
    public static Optional<PairedDifferenceStatistics> compute(double[] first, double[] second){
        if (first.length != second.length){
            throw new IllegalArgumentException("Score arrays must be the same length");
        }
        int n = first.length;
        if (n < 2){
            return Optional.empty();
        }
        for (int i = 0; i < n; i++){
            if (!Double.isFinite(first[i]) || !Double.isFinite(second[i])){
                throw new IllegalArgumentException("Scores must be finite numbers");
            }
        }

        double meanDifference = 0;
        for (int i = 0; i < n; i++){
            meanDifference += second[i] - first[i];
        }
        meanDifference /= n;

        double sumSquares = 0;
        for (int i = 0; i < n; i++){
            double deviation = (second[i] - first[i]) - meanDifference;
            sumSquares += deviation * deviation;
        }
        double sd = Math.sqrt(sumSquares / (n - 1));
        int degreesOfFreedom = n - 1;
        double t = StatisticalDistributions.studentTQuantile(0.975, degreesOfFreedom);

        PairedDifferenceStatistics stats = new PairedDifferenceStatistics();
        stats.setPairs(n);
        stats.setZeroVariability(sd == 0);

        stats.setMeanPairedDifferenceSeconds(meanDifference);
        double meanMargin = t * sd / Math.sqrt(n);
        ConfidenceInterval meanCi = new ConfidenceInterval(meanDifference - meanMargin, meanDifference + meanMargin);
        stats.setMeanPairedDifferenceCi(meanCi);
        stats.setSystematicDifferenceDetected(!meanCi.contains(0));

        // Exact interval for a normal SD: sd * sqrt(df / chi-square quantile)
        ConfidenceInterval sdCi = new ConfidenceInterval(
                sd * Math.sqrt(degreesOfFreedom / StatisticalDistributions.chiSquareQuantile(0.975, degreesOfFreedom)),
                sd * Math.sqrt(degreesOfFreedom / StatisticalDistributions.chiSquareQuantile(0.025, degreesOfFreedom)));
        stats.setSdPairedDifferenceSeconds(sd);
        stats.setSdPairedDifferenceCi(sdCi);
        stats.setSemSeconds(sd / Math.sqrt(2));
        stats.setSemCi(sdCi.scale(1 / Math.sqrt(2)));
        stats.setMdc95Seconds(Z_95 * sd);
        stats.setMdc95Ci(sdCi.scale(Z_95));

        // Bland and Altman (1986): SE of each limit is approximately sqrt(3 sd^2 / n)
        double lower = meanDifference - Z_95 * sd;
        double upper = meanDifference + Z_95 * sd;
        double limitMargin = t * Math.sqrt(3 * sd * sd / n);
        stats.setLimitsOfAgreementLowerSeconds(lower);
        stats.setLimitsOfAgreementUpperSeconds(upper);
        stats.setLimitsOfAgreementLowerCi(new ConfidenceInterval(lower - limitMargin, lower + limitMargin));
        stats.setLimitsOfAgreementUpperCi(new ConfidenceInterval(upper - limitMargin, upper + limitMargin));

        stats.setIccAbsoluteAgreement(iccAbsoluteAgreement(first, second));
        return Optional.of(stats);
    }

    // ICC(A,1), two-way random effects, absolute agreement, single measurement (McGraw and Wong 1996)
    static Double iccAbsoluteAgreement(double[] first, double[] second){
        int n = first.length;
        int k = 2;
        double grandMean = 0;
        for (int i = 0; i < n; i++){
            grandMean += first[i] + second[i];
        }
        grandMean /= n * k;

        double firstMean = 0;
        double secondMean = 0;
        double subjectSumSquares = 0;
        double totalSumSquares = 0;
        for (int i = 0; i < n; i++){
            firstMean += first[i] / n;
            secondMean += second[i] / n;
            double subjectMean = (first[i] + second[i]) / k;
            subjectSumSquares += k * (subjectMean - grandMean) * (subjectMean - grandMean);
            totalSumSquares += (first[i] - grandMean) * (first[i] - grandMean) + (second[i] - grandMean) * (second[i] - grandMean);
        }
        double occasionSumSquares = n * ((firstMean - grandMean) * (firstMean - grandMean) + (secondMean - grandMean) * (secondMean - grandMean));
        double errorSumSquares = totalSumSquares - subjectSumSquares - occasionSumSquares;

        double msRows = subjectSumSquares / (n - 1);
        double msColumns = occasionSumSquares / (k - 1);
        double msError = Math.max(0, errorSumSquares) / ((n - 1) * (k - 1));
        double denominator = msRows + (k - 1) * msError + k * (msColumns - msError) / n;
        if (denominator <= 0){
            return null;
        }
        return (msRows - msError) / denominator;
    }
}
