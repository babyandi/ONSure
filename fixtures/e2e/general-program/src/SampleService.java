package sample;

public final class SampleService {
    private static final String apiKey = "SECRET_DEMO_DO_NOT_USE";

    public String authorize(String user) {
        // TODO_BUG: all users are currently allowed.
        return "ALLOW";
    }

    public static void main(String[] args) {
        String user = args.length == 0 ? "" : args[0];
        System.out.println(new SampleService().authorize(user));
    }
}
