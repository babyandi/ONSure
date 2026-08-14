package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginQualificationLedgerTest {
    @TempDir Path temp;

    @Test
    void unqualifiedPluginHasNoLastRecord() throws Exception {
        PluginQualificationLedger ledger = new PluginQualificationLedger(temp);
        assertNull(ledger.last("plugin-never-qualified"));
    }

    @Test
    void savedRecordIsReadableAsLast() throws Exception {
        PluginQualificationLedger ledger = new PluginQualificationLedger(temp);
        ledger.save(new PluginQualificationLedger.Record(
                "plugin-1", "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1",
                "QUALIFIED", "2026-08-14T00:00:00Z"));
        var last = ledger.last("plugin-1");
        assertEquals("QUALIFIED", last.qualificationState());
        assertEquals("43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1", last.artifactDigest());
    }

    @Test
    void savingAgainOverwritesTheLastRecord() throws Exception {
        PluginQualificationLedger ledger = new PluginQualificationLedger(temp);
        ledger.save(new PluginQualificationLedger.Record(
                "plugin-2", "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1",
                "QUALIFIED", "2026-08-14T00:00:00Z"));
        ledger.save(new PluginQualificationLedger.Record(
                "plugin-2", "70213192283560990cc7315457795d1af358aafdb8d1e97c06cbf21dd03d889b",
                "QUALIFICATION_PENDING", "2026-08-15T00:00:00Z"));
        var last = ledger.last("plugin-2");
        assertEquals("QUALIFICATION_PENDING", last.qualificationState());
        assertEquals("70213192283560990cc7315457795d1af358aafdb8d1e97c06cbf21dd03d889b", last.artifactDigest());
    }
}
