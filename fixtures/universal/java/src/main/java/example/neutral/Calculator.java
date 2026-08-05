package example.neutral;

public final class Calculator {
    private Calculator() {}

    public static int divide(int left, int right) {
        if (right == 0) throw new IllegalArgumentException("DIVISOR_REQUIRED");
        return left / right;
    }
}
