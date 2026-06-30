package samples.java11;

import java.util.List;
import java.util.function.Predicate;

public class VarDemo {

    public int countEven(List<Integer> values) {
        Predicate<Integer> isEven = (var v) -> v % 2 == 0;
        return (int) values.stream().filter(isEven).count();
    }

    public String repeat(String s) {
        return "abc".repeat(2);
    }
}
