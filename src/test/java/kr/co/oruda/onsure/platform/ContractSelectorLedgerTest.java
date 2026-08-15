package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 137 SS27 Batch 8 v1->v2 cutover/rollback, contract-active-selector.candidate.v2.schema.json. */
class ContractSelectorLedgerTest {
    @TempDir Path temp;
    private static final String DIGEST = "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1";

    @Test
    void cutoverIsBlockedWhenDivergenceIsUnresolved() {
        ContractSelectorLedger ledger = new ContractSelectorLedger(temp);
        SecurityException denied = assertThrows(SecurityException.class,
                () -> ledger.cutover("family-1", "v2", DIGEST, DIGEST, false));
        assertEquals("CUTOVER_BLOCKED_UNRESOLVED_DIVERGENCE", denied.getMessage());
    }

    @Test
    void cutoverSucceedsOnceDivergenceIsResolved() throws Exception {
        ContractSelectorLedger ledger = new ContractSelectorLedger(temp);
        var entry = ledger.cutover("family-2", "v2", DIGEST, DIGEST, true);
        assertEquals("v2", entry.activeVersion());
        assertEquals("v2", ledger.active("family-2").activeVersion());
    }

    @Test
    void rollbackWithNoPriorSelectorFails() {
        ContractSelectorLedger ledger = new ContractSelectorLedger(temp);
        assertThrows(IllegalArgumentException.class, () -> ledger.rollback("family-never-cut-over"));
    }

    @Test
    void rollbackReactivatesThePrecedingVersion() throws Exception {
        ContractSelectorLedger ledger = new ContractSelectorLedger(temp);
        ledger.cutover("family-3", "v1", DIGEST, DIGEST, true);
        ledger.cutover("family-3", "v2", "70213192283560990cc7315457795d1af358aafdb8d1e97c06cbf21dd03d889b", DIGEST, true);
        assertEquals("v2", ledger.active("family-3").activeVersion());
        var rolledBack = ledger.rollback("family-3");
        assertEquals("v1", rolledBack.activeVersion());
        assertEquals("v1", ledger.active("family-3").activeVersion());
    }

    @Test
    void everySelectorEntryHasARealVerifiableSignature() throws Exception {
        ContractSelectorLedger ledger = new ContractSelectorLedger(temp);
        var entry = ledger.cutover("family-4", "v2", DIGEST, DIGEST, true);
        assertEquals("EPHEMERAL_SELF_VALIDATION_KEY", entry.issuerKeyId());
        assertEquals(true, entry.signature() != null && !entry.signature().isBlank());
    }

    @Test
    void cuttingOverToTheAlreadyActiveVersionIsRejected() throws Exception {
        ContractSelectorLedger ledger = new ContractSelectorLedger(temp);
        ledger.cutover("family-5", "v2", DIGEST, DIGEST, true);
        assertThrows(IllegalArgumentException.class, () -> ledger.cutover("family-5", "v2", DIGEST, DIGEST, true));
    }
}
