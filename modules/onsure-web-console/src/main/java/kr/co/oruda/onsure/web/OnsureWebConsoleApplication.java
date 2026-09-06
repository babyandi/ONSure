package kr.co.oruda.onsure.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Development-only ONSure Web Console.
 *
 * <p>This surface is intentionally separated from assurance decision authority. It may expose
 * candidate/read-only state, but it must not synthesize PASS, FinalLock, Production GO, or
 * Commercial GO.</p>
 */
@SpringBootApplication
public class OnsureWebConsoleApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnsureWebConsoleApplication.class, args);
    }
}
