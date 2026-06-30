package samples.java17;

public record RecordDemo(String name, int age) {

    public String greeting() {
        return "Hello, " + name;
    }
}
