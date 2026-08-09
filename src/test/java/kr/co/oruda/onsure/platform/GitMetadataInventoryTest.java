package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kr.co.oruda.onsure.platform.GitMetadataInventory.Inventory;
import kr.co.oruda.onsure.platform.GitMetadataInventory.Provider;
import kr.co.oruda.onsure.platform.GitMetadataInventory.Remote;
import kr.co.oruda.onsure.platform.GitMetadataInventory.Submodule;
import kr.co.oruda.onsure.platform.GitMetadataInventory.SubmoduleStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitMetadataInventoryTest {
    @TempDir Path temp;

    @Test
    void nonRepositoryDirectoryDegradesToEmptyInventoryWithoutThrowing() throws Exception {
        Path notARepo = temp.resolve("plain-directory");
        Files.createDirectories(notARepo);
        Files.writeString(notARepo.resolve("README.md"), "# not a git repo\n");

        Inventory inventory = GitMetadataInventory.inspect(notARepo);

        assertFalse(inventory.repository());
        assertEquals(List.of(), inventory.submodules());
        assertEquals(List.of(), inventory.lfsPatterns());
        assertEquals(List.of(), inventory.remotes());
    }

    @Test
    void missingDirectoryDegradesToEmptyInventoryWithoutThrowing() {
        Path missing = temp.resolve("does-not-exist");

        Inventory inventory = GitMetadataInventory.inspect(missing);

        assertFalse(inventory.repository());
        assertEquals(List.of(), inventory.submodules());
        assertEquals(List.of(), inventory.lfsPatterns());
        assertEquals(List.of(), inventory.remotes());
    }

    @Test
    void bareRepositoryWithNoSubmodulesLfsOrRemotesReturnsEmptyListsNotNull() throws Exception {
        Path source = temp.resolve("bare-git-source");
        Files.createDirectories(source);
        git(source, "init");
        git(source, "config", "user.email", "test@example.invalid");
        git(source, "config", "user.name", "ONSure Test");
        Files.writeString(source.resolve("README.md"), "# Bare Sample\n");
        git(source, "add", ".");
        git(source, "commit", "-m", "baseline");

        Inventory inventory = GitMetadataInventory.inspect(source);

        assertTrue(inventory.repository());
        assertEquals(List.of(), inventory.submodules());
        assertEquals(List.of(), inventory.lfsPatterns());
        assertEquals(List.of(), inventory.remotes());
    }

    @Test
    void githubHttpsAndSshRemotesAreBothDetectedAsGithub() throws Exception {
        Path source = temp.resolve("github-remote-source");
        Files.createDirectories(source);
        git(source, "init");
        git(source, "config", "user.email", "test@example.invalid");
        git(source, "config", "user.name", "ONSure Test");
        Files.writeString(source.resolve("README.md"), "# Github Remote Sample\n");
        git(source, "add", ".");
        git(source, "commit", "-m", "baseline");
        git(source, "remote", "add", "origin", "https://github.com/example/repo.git");
        git(source, "remote", "add", "upstream", "git@github.com:example/upstream-repo.git");

        Inventory inventory = GitMetadataInventory.inspect(source);

        assertEquals(2, inventory.remotes().size());
        Remote origin = inventory.remotes().stream()
                .filter(remote -> remote.name().equals("origin")).findFirst().orElseThrow();
        assertEquals("https://github.com/example/repo.git", origin.url());
        assertEquals(Provider.GITHUB, origin.provider());
        Remote upstream = inventory.remotes().stream()
                .filter(remote -> remote.name().equals("upstream")).findFirst().orElseThrow();
        assertEquals("git@github.com:example/upstream-repo.git", upstream.url());
        assertEquals(Provider.GITHUB, upstream.provider());
    }

    @Test
    void nonGithubRemotesAreDetectedByProviderHostname() throws Exception {
        Path source = temp.resolve("multi-provider-remote-source");
        Files.createDirectories(source);
        git(source, "init");
        git(source, "config", "user.email", "test@example.invalid");
        git(source, "config", "user.name", "ONSure Test");
        Files.writeString(source.resolve("README.md"), "# Multi Provider Sample\n");
        git(source, "add", ".");
        git(source, "commit", "-m", "baseline");
        git(source, "remote", "add", "gitlab-remote", "https://GitLab.com/example/repo.git");
        git(source, "remote", "add", "bitbucket-remote", "git@bitbucket.org:example/repo.git");
        git(source, "remote", "add", "azure-remote", "https://dev.azure.com/example/project/_git/repo");
        git(source, "remote", "add", "unknown-remote", "https://git.internal.example.invalid/example/repo.git");

        Inventory inventory = GitMetadataInventory.inspect(source);

        assertEquals(4, inventory.remotes().size());
        assertEquals(Provider.GITLAB, providerOf(inventory, "gitlab-remote"));
        assertEquals(Provider.BITBUCKET, providerOf(inventory, "bitbucket-remote"));
        assertEquals(Provider.AZURE_DEVOPS, providerOf(inventory, "azure-remote"));
        assertEquals(Provider.UNKNOWN, providerOf(inventory, "unknown-remote"));
    }

    @Test
    void gitattributesLfsFilterPatternsAreParsedWithoutShellingOutToGitLfs() throws Exception {
        Path source = temp.resolve("lfs-pattern-source");
        Files.createDirectories(source);
        git(source, "init");
        git(source, "config", "user.email", "test@example.invalid");
        git(source, "config", "user.name", "ONSure Test");
        Files.writeString(source.resolve(".gitattributes"),
                "# comment line, ignored\n"
                        + "*.psd filter=lfs diff=lfs merge=lfs -text\n"
                        + "*.bin filter=lfs diff=lfs merge=lfs -text\n"
                        + "*.md text\n");
        Files.writeString(source.resolve("README.md"), "# LFS Pattern Sample\n");
        git(source, "add", ".");
        git(source, "commit", "-m", "baseline");

        Inventory inventory = GitMetadataInventory.inspect(source);

        assertEquals(List.of("*.psd", "*.bin"), inventory.lfsPatterns());
    }

    @Test
    void submoduleRegisteredButNeverCheckedOutIsReportedAsNotInitialized() throws Exception {
        Path source = temp.resolve("submodule-source");
        Files.createDirectories(source);
        git(source, "init");
        git(source, "config", "user.email", "test@example.invalid");
        git(source, "config", "user.name", "ONSure Test");
        Files.writeString(source.resolve("README.md"), "# Submodule Sample\n");
        git(source, "add", ".");
        git(source, "commit", "-m", "baseline");

        Files.writeString(source.resolve(".gitmodules"),
                "[submodule \"vendor/thing\"]\n"
                        + "\tpath = vendor/thing\n"
                        + "\turl = https://github.com/example/thing.git\n");
        git(source, "add", ".gitmodules");
        // Register a gitlink entry directly in the index without ever cloning/checking out the
        // submodule content, which is exactly the state `git submodule status` reports as "-"
        // (not initialized) -- this is the standard offline way to simulate an uninitialized
        // submodule without network access.
        String fakeCommit = "e".repeat(40);
        git(source, "update-index", "--add", "--cacheinfo", "160000," + fakeCommit + ",vendor/thing");
        git(source, "commit", "-m", "register submodule");

        Inventory inventory = GitMetadataInventory.inspect(source);

        assertEquals(1, inventory.submodules().size());
        Submodule submodule = inventory.submodules().get(0);
        assertEquals("vendor/thing", submodule.path());
        assertEquals(fakeCommit, submodule.commitSha());
        assertEquals(SubmoduleStatus.NOT_INITIALIZED, submodule.status());
    }

    @Test
    void parseSubmoduleLineHandlesAllFourStatusPrefixes() {
        assertEquals(new Submodule("vendor/a", "a".repeat(40), SubmoduleStatus.NOT_INITIALIZED),
                GitMetadataInventory.parseSubmoduleLine("-" + "a".repeat(40) + " vendor/a"));
        assertEquals(new Submodule("vendor/b", "b".repeat(40), SubmoduleStatus.CHECKED_OUT_DIFFERENT_COMMIT),
                GitMetadataInventory.parseSubmoduleLine(
                        "+" + "b".repeat(40) + " vendor/b (heads/main)"));
        assertEquals(new Submodule("vendor/c", "c".repeat(40), SubmoduleStatus.MERGE_CONFLICT),
                GitMetadataInventory.parseSubmoduleLine("U" + "c".repeat(40) + " vendor/c"));
        assertEquals(new Submodule("vendor/d", "d".repeat(40), SubmoduleStatus.UP_TO_DATE),
                GitMetadataInventory.parseSubmoduleLine(
                        " " + "d".repeat(40) + " vendor/d (heads/main)"));
        assertNull(GitMetadataInventory.parseSubmoduleLine(""));
        assertNull(GitMetadataInventory.parseSubmoduleLine("?" + "a".repeat(40) + " vendor/unknown"));
    }

    @Test
    void detectProviderMatchesKnownHostnamesCaseInsensitivelyAndOtherwiseUnknown() {
        assertEquals(Provider.GITHUB, GitMetadataInventory.detectProvider("https://GITHUB.com/a/b.git"));
        assertEquals(Provider.GITHUB, GitMetadataInventory.detectProvider("git@github.com:a/b.git"));
        assertEquals(Provider.GITLAB, GitMetadataInventory.detectProvider("git@gitlab.com:a/b.git"));
        assertEquals(Provider.BITBUCKET, GitMetadataInventory.detectProvider("https://bitbucket.org/a/b.git"));
        assertEquals(Provider.AZURE_DEVOPS,
                GitMetadataInventory.detectProvider("https://dev.azure.com/org/proj/_git/repo"));
        assertEquals(Provider.UNKNOWN, GitMetadataInventory.detectProvider("https://example.invalid/a/b.git"));
        assertEquals(Provider.UNKNOWN, GitMetadataInventory.detectProvider(null));
        assertEquals(Provider.UNKNOWN, GitMetadataInventory.detectProvider(""));
    }

    private static Provider providerOf(Inventory inventory, String remoteName) {
        return inventory.remotes().stream()
                .filter(remote -> remote.name().equals(remoteName))
                .findFirst().orElseThrow().provider();
    }

    private static void git(Path root, String... arguments) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git"); command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) throw new IllegalStateException(output);
    }
}
