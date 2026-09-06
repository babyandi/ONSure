package kr.co.oruda.onsure.web;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import kr.co.oruda.onsure.platform.SessionLedger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only projection of the existing ONSure SessionLedger for one explicitly bound user. */
@RestController
@RequestMapping("/api/v1/workbench")
public final class CoreSessionReadModelController {
    public static final String CONTRACT = "ONSURE_WEB_SESSION_READ_MODEL_V1";
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private final String configuredSessionLedgerRoot;
    private final String configuredUserId;
    private final Clock clock;

    public CoreSessionReadModelController(
            @Value("${onsure.session-ledger-root:}") String configuredSessionLedgerRoot,
            @Value("${onsure.session-user-id:}") String configuredUserId) {
        this(configuredSessionLedgerRoot, configuredUserId, Clock.systemUTC());
    }

    CoreSessionReadModelController(String configuredSessionLedgerRoot, String configuredUserId, Clock clock) {
        this.configuredSessionLedgerRoot = configuredSessionLedgerRoot == null
                ? "" : configuredSessionLedgerRoot.strip();
        this.configuredUserId = configuredUserId == null ? "" : configuredUserId.strip();
        this.clock = clock;
    }

    @GetMapping("/sessions")
    public SessionSnapshot sessions() {
        if (configuredSessionLedgerRoot.isBlank() || configuredUserId.isBlank()) {
            return unavailable("SESSION_AUTHORITY_NOT_CONFIGURED", "SESSION_AUTHORITY_UNBOUND_NONFINAL");
        }
        if (!USER_ID_PATTERN.matcher(configuredUserId).matches()) {
            return unavailable("SESSION_USER_ID_INVALID", "SESSION_READ_MODEL_BLOCKED_NONFINAL");
        }

        final Path ledgerRoot;
        try {
            ledgerRoot = Path.of(configuredSessionLedgerRoot).toAbsolutePath().normalize();
        } catch (RuntimeException invalidPath) {
            return unavailable("SESSION_LEDGER_ROOT_INVALID", "SESSION_READ_MODEL_BLOCKED_NONFINAL");
        }

        if (!Files.isDirectory(ledgerRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(ledgerRoot)) {
            return unavailable("SESSION_LEDGER_ROOT_UNAVAILABLE", "SESSION_READ_MODEL_BLOCKED_NONFINAL");
        }

        try {
            SessionLedger ledger = new SessionLedger(ledgerRoot);
            Instant readAt = clock.instant();
            List<SessionSummary> active = ledger.activeSessionsFor(configuredUserId, readAt).stream()
                    .map(session -> new SessionSummary(
                            session.sessionId(), session.issuedAt(), session.expiresAt(), session.status()))
                    .toList();
            return new SessionSnapshot(
                    CONTRACT,
                    "CORE_SESSION_READ_MODEL_NONFINAL",
                    true,
                    null,
                    readAt.toString(),
                    active,
                    false,
                    false,
                    false);
        } catch (Exception unreadable) {
            return unavailable("SESSION_LEDGER_READ_FAILED", "SESSION_READ_MODEL_BLOCKED_NONFINAL");
        }
    }

    private static SessionSnapshot unavailable(String reason, String state) {
        return new SessionSnapshot(
                CONTRACT, state, false, reason, null, List.of(), false, false, false);
    }

    /** User id and any filesystem path are deliberately omitted from the browser DTO. */
    public record SessionSummary(String sessionId, String issuedAt, String expiresAt, String status) {}

    public record SessionSnapshot(
            String contract,
            String state,
            boolean available,
            String blockedReason,
            String readAt,
            List<SessionSummary> activeSessions,
            boolean independentVerificationComplete,
            boolean finalClaimAllowed,
            boolean productionGo) {}
}
