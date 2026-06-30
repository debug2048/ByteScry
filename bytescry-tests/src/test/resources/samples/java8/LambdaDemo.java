package samples.java8;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LambdaDemo {

    public List<Integer> doubleEach(List<Integer> input) {
        return input.stream()
                .map(x -> x * 2)
                .collect(Collectors.toList());
    }

    public int sum(List<Integer> input) {
        return input.stream().reduce(0, Integer::sum);
    }
}
