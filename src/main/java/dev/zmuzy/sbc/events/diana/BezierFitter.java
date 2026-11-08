// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.diana;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// from skyhanni PolynomialFitter (PolynomialFitter, BezierFitter, ParticlePathBezierFitter, BezierCurve) + Matrix
// least squares fit of a degree n polynomial per axis over particle index, burrow guess from the curve's
// start derivative (LocationUtils.computePitchWeight)
public final class BezierFitter {
    // skyhanni Matrix, just what the fitter needs (transpose, multiply, gauss-jordan inverse)
    static final class Matrix {
        final double[][] data;
        final int width, height;

        Matrix(double[][] data) {
            if (data.length == 0) throw new IllegalArgumentException("Matrix can't be empty");
            this.data = data;
            this.height = data.length;
            this.width = data[0].length;
            for (double[] r : data) if (r.length != width) throw new IllegalArgumentException("All rows must have the same length");
        }

        static Matrix identity(int size) {
            double[][] d = new double[size][size];
            for (int i = 0; i < size; i++) d[i][i] = 1.0;
            return new Matrix(d);
        }

        Matrix transpose() {
            double[][] t = new double[width][height];
            for (int r = 0; r < height; r++) for (int c = 0; c < width; c++) t[c][r] = data[r][c];
            return new Matrix(t);
        }

        Matrix times(Matrix o) {
            if (width != o.height) throw new IllegalArgumentException("Invalid Matrix sizes");
            double[][] res = new double[height][o.width];
            for (int r = 0; r < height; r++) for (int c = 0; c < o.width; c++) {
                double s = 0;
                for (int k = 0; k < width; k++) s += data[r][k] * o.data[k][c];
                res[r][c] = s;
            }
            return new Matrix(res);
        }

        Matrix inverse() {
            if (width != height) throw new IllegalArgumentException("Matrix must be square");
            double[][] a = new double[height][];
            for (int i = 0; i < height; i++) a[i] = data[i].clone();
            double[][] b = identity(width).data;
            for (int c = 0; c < width; c++) {
                int rBig = c;
                for (int r = c; r < height; r++) if (Math.abs(a[r][c]) > Math.abs(a[rBig][c])) rBig = r;
                if (Math.abs(a[rBig][c]) == 0.0) throw new IllegalArgumentException("Cannot invert matrix");
                if (rBig != c) {
                    double[] t = a[c]; a[c] = a[rBig]; a[rBig] = t;
                    t = b[c]; b[c] = b[rBig]; b[rBig] = t;
                }
                double pivot = a[c][c];
                for (int s = c; s < width; s++) a[c][s] /= pivot;
                for (int s = 0; s < width; s++) b[c][s] /= pivot;
                for (int r2 = 0; r2 < height; r2++) {
                    if (r2 == c) continue;
                    double factor = -a[r2][c];
                    for (int s = c; s < width; s++) a[r2][s] += factor * a[c][s];
                    for (int s = 0; s < width; s++) b[r2][s] += factor * b[c][s];
                }
            }
            return new Matrix(b);
        }
    }

    // PolynomialFitter: y = sum(c_i * x^i), solved by (XᵀX)⁻¹XᵀY
    static final class PolynomialFitter {
        private final int degree;
        private final List<double[]> xPointMatrix = new ArrayList<>();
        private final List<double[]> yPoints = new ArrayList<>();

        PolynomialFitter(int degree) {
            this.degree = degree;
        }

        void addPoint(double x, double y) {
            yPoints.add(new double[]{y});
            double[] xArray = new double[degree + 1];
            for (int i = 0; i < xArray.length; i++) xArray[i] = Math.pow(x, i);
            xPointMatrix.add(xArray);
        }

        double[] fit() {
            Matrix xMatrix = new Matrix(xPointMatrix.toArray(new double[0][]));
            Matrix yMatrix = new Matrix(yPoints.toArray(new double[0][]));
            Matrix xT = xMatrix.transpose();
            return xT.times(xMatrix).inverse().times(xT).times(yMatrix).transpose().data[0];
        }

        void reset() {
            xPointMatrix.clear();
            yPoints.clear();
        }
    }

    // BezierCurve: 3 coefficient arrays (x, y, z), lowest power first
    public static final class Curve {
        private final List<double[]> coefficients;

        Curve(List<double[]> coefficients) {
            if (coefficients.size() != 3) throw new IllegalArgumentException("Coefficients must be for a 3d curve!");
            this.coefficients = coefficients;
        }

        public Vec3 derivativeAt(double t) {
            double[] out = new double[3];
            for (int a = 0; a < 3; a++) {
                double[] c = coefficients.get(a);
                // reversed, drop the constant, horner with the power as factor
                double result = 0.0;
                int n = c.length - 1; // number of derivative terms
                for (int i = 0; i < n; i++) {
                    double coefficient = c[c.length - 1 - i];
                    result = result * t + coefficient * (n - i);
                }
                out[a] = result;
            }
            return new Vec3(out[0], out[1], out[2]);
        }

        public Vec3 at(double t) {
            double[] out = new double[3];
            for (int a = 0; a < 3; a++) {
                double[] c = coefficients.get(a);
                double result = 0.0;
                for (int i = c.length - 1; i >= 0; i--) result = result * t + c[i];
                out[a] = result;
            }
            return new Vec3(out[0], out[1], out[2]);
        }
    }

    // 0.01 blocks squared. hypixel repeats particle positions and a repeat would stall the curve param
    private static final double REPEAT_TOLERANCE_SQ = 0.0001;

    private final int degree;
    public final List<Vec3> points = new ArrayList<>();
    private final PolynomialFitter[] fitters;
    private Curve lastCurve;

    public BezierFitter(int degree) {
        this.degree = degree;
        fitters = new PolynomialFitter[]{new PolynomialFitter(degree), new PolynomialFitter(degree), new PolynomialFitter(degree)};
    }

    public void addPoint(Vec3 point) {
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) throw new IllegalArgumentException("Points may not contain NaN!");
        double[] arr = {point.x, point.y, point.z};
        for (int i = 0; i < 3; i++) fitters[i].addPoint(points.size(), arr[i]);
        points.add(point);
        lastCurve = null;
    }

    public Vec3 getLastPoint() {
        return points.isEmpty() ? null : points.get(points.size() - 1);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public int count() {
        return points.size();
    }

    // degree n needs n+1 points
    public Curve fit() {
        if (points.size() <= degree) return null;
        if (lastCurve != null) return lastCurve;
        List<double[]> coefficients = new ArrayList<>(3);
        for (PolynomialFitter f : fitters) coefficients.add(f.fit());
        lastCurve = new Curve(coefficients);
        return lastCurve;
    }

    public void reset() {
        points.clear();
        for (PolynomialFitter f : fitters) f.reset();
        lastCurve = null;
    }

    // BezierFitter.tryAdd minus the optional conditions: true if added to a non-empty curve
    public boolean tryAdd(Vec3 location, double maxDistanceToLast) {
        if (isEmpty()) {
            addPoint(location);
            return false;
        }
        Vec3 last = getLastPoint();
        if (last == null) return false;
        if (last.distanceTo(location) > maxDistanceToLast) return false;
        if (isAlreadyUsed(location)) return false;
        addPoint(location);
        return true;
    }

    private boolean isAlreadyUsed(Vec3 location) {
        for (Vec3 p : points) if (p.distanceToSqr(location) < REPEAT_TOLERANCE_SQ) return true;
        return false;
    }

    // ParticlePathBezierFitter.solve: where the spade's particle curve lands
    public Vec3 solve() {
        Curve curve = fit();
        if (curve == null) return null;
        Vec3 startPointDerivative = curve.derivativeAt(0.0);
        // how far from the first point the control point is
        double controlPointDistance = computePitchWeight(startPointDerivative);
        double len = Math.sqrt(startPointDerivative.lengthSqr());
        double t = 3 * controlPointDistance / len;
        return curve.at(t);
    }

    // LocationUtils.computePitchWeight
    static double computePitchWeight(Vec3 derivative) {
        return Math.sqrt(24 * Math.sin(getPitchFromDerivative(derivative) - Math.PI) + 25);
    }

    // LocationUtils.getPitchFromDerivative: solve y = atan2(sin(x) - 0.75, cos(x)) for x by bisection
    static double getPitchFromDerivative(Vec3 derivative) {
        double xzLength = Math.sqrt(derivative.x * derivative.x + derivative.z * derivative.z);
        double pitchRadians = -Math.atan2(derivative.y, xzLength);
        double guessPitch = pitchRadians;
        double resultPitch = Math.atan2(Math.sin(guessPitch) - 0.75, Math.cos(guessPitch));
        double windowMax = Math.PI / 2;
        double windowMin = -Math.PI / 2;
        for (int i = 0; i < 100; i++) {
            if (resultPitch < pitchRadians) {
                windowMin = guessPitch;
                guessPitch = (windowMin + windowMax) / 2;
            } else {
                windowMax = guessPitch;
                guessPitch = (windowMin + windowMax) / 2;
            }
            resultPitch = Math.atan2(Math.sin(guessPitch) - 0.75, Math.cos(guessPitch));
            if (resultPitch == pitchRadians) return guessPitch;
        }
        return guessPitch;
    }
}
