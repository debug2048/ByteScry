package samples.java17;

public sealed class SealedClassDemo permits Circle, Square {

    public double area() {
        return 0.0;
    }
}

final class Circle extends SealedClassDemo {
    private final double radius;

    public Circle(double radius) {
        this.radius = radius;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }
}

final class Square extends SealedClassDemo {
    private final double side;

    public Square(double side) {
        this.side = side;
    }

    @Override
    public double area() {
        return side * side;
    }
}
