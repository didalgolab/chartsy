/* Copyright 2018 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.wavelets;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.DoubleStream;

/**
 * The class represents Laurent polynomials, which are used in the construction of biorthogonal spline wavelets.
 *
 * @author Mariusz Bernacki
 */
public class LaurentPolynomial {
    
    public static LaurentPolynomial.OfDouble fromCoeffs(double[] coeffs) {
        return fromCoeffs(coeffs, 0);
    }
    
    public static LaurentPolynomial.OfDouble fromCoeffs(double[] coeffs, int scale) {
        return new OfDouble(coeffs, scale);
    }
    
    public static LaurentPolynomial.OfDouble fromCoeffs(int scale, double... coeffs) {
        return new OfDouble(coeffs, scale);
    }
    
    public static class OfDouble {
        private double[] coeffs;
        private int scale; // (e.g. -2)
        
        
        public OfDouble() {
            this(new double[0], 0);
        }
        
        public OfDouble(double coeff0) {
            this(new double[] {coeff0}, 0);
        }
        
        public OfDouble(double[] coeffs, int scale) {
            this.coeffs = coeffs;
            this.scale = scale;
        }
        
        public OfDouble(LaurentPolynomial.OfDouble p) {
            this(p.coeffs.clone(), p.scale);
        }
        
        public double get(int index) {
            return coeffs[index - scale];
        }
        
        public void ensureCapacity(int capacity) {
            if (capacity > coeffs.length)
                coeffs = Arrays.copyOf(coeffs, capacity);
        }
        
        public LaurentPolynomial.OfDouble add(LaurentPolynomial.OfDouble b) {
            // Organize addends, smaller scale first
            OfDouble a = this;
            int scaleDelta = b.scale - a.scale;
            if (scaleDelta < 0) {
                OfDouble tmp = b;
                b = a;
                a = tmp;
                scaleDelta = -scaleDelta;
            }
            OfDouble result = new OfDouble();
            result.ensureCapacity(Math.max(a.coeffs.length, b.coeffs.length + scaleDelta));
            System.arraycopy(a.coeffs, 0, result.coeffs, 0, a.coeffs.length);
            result.scale = a.scale;
            for (int i = 0; i < b.coeffs.length; i++)
                result.coeffs[i + scaleDelta] += b.coeffs[i];
            return result;
        }
        
        public LaurentPolynomial.OfDouble mul(double y) {
            LaurentPolynomial.OfDouble result = new OfDouble(this);
            for (int i = 0; i < coeffs.length; i++)
                result.coeffs[i] = coeffs[i] * y;
            return result;
        }
        
        public LaurentPolynomial.OfDouble mul(LaurentPolynomial.OfDouble b) {
            if (coeffs.length == 0)
                return this;
            if (b.coeffs.length == 0)
                return b;
            
            OfDouble result = new OfDouble();
            multiplyByInto(b, result);
            return result;
        }
        
        protected void multiplyByInto(LaurentPolynomial.OfDouble b, LaurentPolynomial.OfDouble r) {
            OfDouble a = this;
            r.ensureCapacity(a.coeffs.length + b.coeffs.length - 1);
            Arrays.fill(r.coeffs, 0.0);
            r.scale = a.scale + b.scale;
            for (int i = 0; i < a.coeffs.length; i++)
                for (int j = 0; j < b.coeffs.length; j++)
                    r.coeffs[i + j] += (a.coeffs[i] * b.coeffs[j]);
            
        }
        
        public LaurentPolynomial.OfDouble pow(int exp) {
            if (exp < 0)
                throw new IllegalArgumentException("exp < 0");
            
            LaurentPolynomial.OfDouble base = this;
            LaurentPolynomial.OfDouble result = new OfDouble(new double[] {1}, 0); // 1
            while (exp != 0) {
                if ((exp & 1) != 0)
                    result = result.mul(base);
                exp >>= 1;
            base = base.mul(base);
            }
            return result;
        }
        
        public DoubleStream coefficients() {
            return DoubleStream.of(coeffs);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            int scale = this.scale;
            for (int i = 0; i < coeffs.length; i++, scale++) {
                double coeff = coeffs[i];
                if (coeff == 0.0)
                    continue;
                // Append plus or minus sign against the previous term
                if (!sb.isEmpty()) {
                    sb.append((coeff < 0)? " - " : " + ");
                    coeff = Math.abs(coeff);
                }
                // Append coefficient
                sb.append(coeff);
                // Append exponent term
                if (scale != 0) {
                    sb.append("*x");
                    if (scale != 1)
                        sb.append("^").append(scale);
                }
            }
            return sb.toString();
        }
        
        public double[] toArray() {
            return coeffs.clone();
        }
        
        public LaurentPolynomial.OfDouble compose(LaurentPolynomial.OfDouble b) {
            if (coeffs.length == 0)
                return this;
            
            LaurentPolynomial.OfDouble result = new OfDouble();
            for (int i = coeffs.length - 1; i >= 0; i--)
                result = b.mul(result).add(new OfDouble(coeffs[i]));
            return result;
        }
    }
    
    public static class OfBigDecimal {
        private BigDecimal[] coeffs;
        private int scale; // (e.g. -2)
        
        
        public OfBigDecimal() {
            this(new BigDecimal[0], 0);
        }
        
        public OfBigDecimal(BigDecimal[] coeffs, int scale) {
            this.coeffs = coeffs;
            this.scale = scale;
        }
        
        public OfBigDecimal(LaurentPolynomial.OfBigDecimal p) {
            this(p.coeffs.clone(), p.scale);
        }
        
        public BigDecimal get(int index) {
            return coeffs[index - scale];
        }
        
        public void ensureCapacity(int capacity) {
            if (capacity > coeffs.length)
                coeffs = Arrays.copyOf(coeffs, capacity);
        }
        
        public LaurentPolynomial.OfBigDecimal add(LaurentPolynomial.OfBigDecimal b) {
            // Organize addends, smaller scale first
            OfBigDecimal a = this;
            int scaleDelta = b.scale - a.scale;
            if (scaleDelta < 0) {
                OfBigDecimal tmp = b;
                b = a;
                a = tmp;
                scaleDelta = -scaleDelta;
            }
            OfBigDecimal result = new OfBigDecimal();
            result.ensureCapacity(Math.max(a.coeffs.length, b.coeffs.length + scaleDelta));
            System.arraycopy(a.coeffs, 0, result.coeffs, 0, a.coeffs.length);
            result.scale = a.scale;
            for (int i = 0; i < b.coeffs.length; i++)
                result.coeffs[i + scaleDelta] = result.coeffs[i + scaleDelta].add(b.coeffs[i]);
            return result;
        }
        
        public LaurentPolynomial.OfBigDecimal mul(BigDecimal y) {
            LaurentPolynomial.OfBigDecimal result = new OfBigDecimal(this);
            for (int i = 0; i < coeffs.length; i++)
                result.coeffs[i] = coeffs[i].multiply(y);
            return result;
        }
        
        public LaurentPolynomial.OfBigDecimal mul(LaurentPolynomial.OfBigDecimal b) {
            OfBigDecimal result = new OfBigDecimal();
            multiplyByInto(b, result);
            return result;
        }
        
        protected void multiplyByInto(LaurentPolynomial.OfBigDecimal b, LaurentPolynomial.OfBigDecimal r) {
            OfBigDecimal a = this;
            r.ensureCapacity(a.coeffs.length + b.coeffs.length - 1);
            Arrays.fill(r.coeffs, BigDecimal.valueOf(0));
            r.scale = a.scale + b.scale;
            for (int i = 0; i < a.coeffs.length; i++)
                for (int j = 0; j < b.coeffs.length; j++)
                    r.coeffs[i + j] = r.coeffs[i + j].add(a.coeffs[i].multiply(b.coeffs[j]));
            
        }
        
        public LaurentPolynomial.OfBigDecimal pow(int exp) {
            if (exp < 0)
                throw new IllegalArgumentException("exp < 0");
            
            LaurentPolynomial.OfBigDecimal base = this;
            LaurentPolynomial.OfBigDecimal result = new OfBigDecimal(new BigDecimal[] {BigDecimal.valueOf(1)}, 0); // 1
            while (exp != 0) {
                if ((exp & 1) != 0)
                    result = result.mul(base);
                exp >>= 1;
            base = base.mul(base);
            }
            return result;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            int scale = this.scale;
            for (int i = 0; i < coeffs.length; i++, scale++) {
                BigDecimal coeff = coeffs[i];
                if (coeff.signum() == 0)
                    continue;
                // Append plus or minus sign against the previous term
                if (!sb.isEmpty()) {
                    sb.append((coeff.signum() < 0)? " - " : " + ");
                    coeff = (coeff.abs());
                }
                // Append coefficient
                sb.append(coeff);
                // Append exponent term
                if (scale != 0) {
                    sb.append("*x");
                    if (scale != 1)
                        sb.append("^").append(scale);
                }
            }
            return sb.toString();
        }
        
        public BigDecimal[] toArray() {
            return coeffs.clone();
        }
    }
}
