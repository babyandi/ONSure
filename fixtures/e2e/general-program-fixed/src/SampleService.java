package sample;

import java.util.Set;

public final class SampleService {
    private static final Set<String> AUTHORIZED = Set.of("alice");

    public String authorize(String user) {
        return AUTHORIZED.contains(user) ? "ALLOW" : "DENY";
    }

    public static void main(String[] args) {
        String user = args.length == 0 ? "" : args[0];
        System.out.println(new SampleService().authorize(user));
    }
}
