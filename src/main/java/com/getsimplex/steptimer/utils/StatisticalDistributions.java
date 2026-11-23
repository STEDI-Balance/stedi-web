package com.getsimplex.steptimer.utils;

/**
 * Quantiles of the Student t and chi-square distributions, used for MDC95 confidence intervals.
 * Implemented here (rather than adding a statistics library) with the standard
 * continued-fraction forms of the regularized incomplete beta and gamma functions
 * (Press et al., Numerical Recipes, 3rd ed., sections 6.1-6.4).
 */
public class StatisticalDistributions {

    private static final double EPSILON = 1e-15;
    private static final double TINY = 1e-300;
    private static final int MAX_ITERATIONS = 500;

    private static final double[] LANCZOS = {
            0.99999999999980993, 676.5203681218851, -1259.1392167224028,
            771.32342877765313, -176.61502916214059, 12.507343278686905,
            -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7
    };

    public static double logGamma(double x){
        if (x < 0.5){
            return Math.log(Math.PI / Math.sin(Math.PI * x)) - logGamma(1 - x);//reflection formula
        }
        x -= 1;
        double sum = LANCZOS[0];
        double t = x + 7.5;
        for (int i = 1; i < LANCZOS.length; i++){
            sum += LANCZOS[i] / (x + i);
        }
        return 0.5 * Math.log(2 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(sum);
    }

    // I_x(a, b)
    public static double regularizedIncompleteBeta(double x, double a, double b){
        if (x <= 0){
            return 0;
        }
        if (x >= 1){
            return 1;
        }
        double front = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b) + a * Math.log(x) + b * Math.log(1 - x));
        if (x < (a + 1) / (a + b + 2)){
            return front * betaContinuedFraction(x, a, b) / a;
        }
        return 1 - front * betaContinuedFraction(1 - x, b, a) / b;
    }

    private static double betaContinuedFraction(double x, double a, double b){
        double c = 1;
        double d = 1 - (a + b) * x / (a + 1);
        if (Math.abs(d) < TINY){
            d = TINY;
        }
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= MAX_ITERATIONS; m++){
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((a + m2 - 1) * (a + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < TINY){
                d = TINY;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < TINY){
                c = TINY;
            }
            d = 1 / d;
            h *= d * c;
            aa = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1));
            d = 1 + aa * d;
            if (Math.abs(d) < TINY){
                d = TINY;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < TINY){
                c = TINY;
            }
            d = 1 / d;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1) < EPSILON){
                break;
            }
        }
        return h;
    }

    // P(a, x), the regularized lower incomplete gamma function
    public static double regularizedGammaP(double a, double x){
        if (x <= 0){
            return 0;
        }
        if (x < a + 1){
            double term = 1 / a;
            double sum = term;
            for (int n = 1; n <= MAX_ITERATIONS; n++){
                term *= x / (a + n);
                sum += term;
                if (Math.abs(term) < Math.abs(sum) * EPSILON){
                    break;
                }
            }
            return sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
        }
        double b = x + 1 - a;
        double c = 1 / TINY;
        double d = 1 / b;
        double h = d;
        for (int i = 1; i <= MAX_ITERATIONS; i++){
            double an = -i * (i - a);
            b += 2;
            d = an * d + b;
            if (Math.abs(d) < TINY){
                d = TINY;
            }
            c = b + an / c;
            if (Math.abs(c) < TINY){
                c = TINY;
            }
            d = 1 / d;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1) < EPSILON){
                break;
            }
        }
        return 1 - Math.exp(-x + a * Math.log(x) - logGamma(a)) * h;
    }

    public static double studentTCdf(double t, double degreesOfFreedom){
        double tail = 0.5 * regularizedIncompleteBeta(degreesOfFreedom / (degreesOfFreedom + t * t), degreesOfFreedom / 2, 0.5);
        return t >= 0 ? 1 - tail : tail;
    }

    public static double chiSquareCdf(double x, double degreesOfFreedom){
        return regularizedGammaP(degreesOfFreedom / 2, x / 2);
    }

    public static double studentTQuantile(double probability, double degreesOfFreedom){
        checkProbability(probability);
        if (probability < 0.5){
            return -studentTQuantile(1 - probability, degreesOfFreedom);
        }
        double high = 1;
        while (studentTCdf(high, degreesOfFreedom) < probability){
            high *= 2;
        }
        return bisect(x -> studentTCdf(x, degreesOfFreedom), probability, 0, high);
    }

    public static double chiSquareQuantile(double probability, double degreesOfFreedom){
        checkProbability(probability);
        double high = Math.max(1, degreesOfFreedom);
        while (chiSquareCdf(high, degreesOfFreedom) < probability){
            high *= 2;
        }
        return bisect(x -> chiSquareCdf(x, degreesOfFreedom), probability, 0, high);
    }

    private static double bisect(java.util.function.DoubleUnaryOperator cdf, double probability, double low, double high){
        for (int i = 0; i < 200 && high - low > 1e-13 * Math.max(1, high); i++){
            double middle = (low + high) / 2;
            if (cdf.applyAsDouble(middle) < probability){
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) / 2;
    }

    private static void checkProbability(double probability){
        if (!(probability > 0 && probability < 1)){
            throw new IllegalArgumentException("Probability must be between 0 and 1: " + probability);
        }
    }
}
