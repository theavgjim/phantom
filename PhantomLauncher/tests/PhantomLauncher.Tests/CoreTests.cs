using System.Text.Json.Nodes;
using PhantomLauncher.Core.Models;
using PhantomLauncher.Core.Services;
using Xunit;

namespace PhantomLauncher.Tests;

public sealed class TempDir : IDisposable
{
    public string Path { get; } = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "phantom-test-" + Guid.NewGuid().ToString("N"));
    public TempDir() => Directory.CreateDirectory(Path);
    public string this[string rel] => System.IO.Path.Combine(Path, rel);
    public void Dispose() { try { Directory.Delete(Path, true); } catch { } }
}

public class VersionAndCompatTests
{
    [Theory]
    [InlineData("1.21.1", "1.21", 1)]
    [InlineData("v1.0.0", "1.0.0", 0)]
    [InlineData("1.0.0", "1.0.0-beta.2", 1)]
    [InlineData("1.2.0", "1.10.0", -1)]
    [InlineData("0.16.14", "0.16.9", 1)]
    public void SemVerOrders(string a, string b, int sign) =>
        Assert.Equal(sign, Math.Sign(SemVer.Parse(a).CompareTo(SemVer.Parse(b))));

    [Fact]
    public void SharedCompatibilityTableParses()
    {
        var table = CompatibilityTable.Parse(File.ReadAllText("compatibility.json"));
        Assert.Equal("1.21.1", table.Primary.Minecraft);
        Assert.Equal(21, table.Primary.Java);
        Assert.Equal(CompatState.Compatible, table.Check("1.21.1", 21).State);
        Assert.Equal(CompatState.Unsupported, table.Check("1.20.4", 21).State);
        Assert.Equal(CompatState.Warning, table.Check("1.21.1", 17).State);
        Assert.Equal(CompatState.Warning, table.Check(null, 0).State);
    }

    [Fact]
    public void JavaReleaseFileAndVersionOutput()
    {
        var r = JavaRuntime.ParseReleaseFile("IMPLEMENTOR=\"Eclipse Adoptium\"\nJAVA_VERSION=\"21.0.4\"\n");
        Assert.Equal(("21.0.4", 21, "Eclipse Adoptium"), r);
        Assert.Equal(("1.8.0_392", 8), JavaRuntime.ParseVersionOutput("java version \"1.8.0_392\"\nJava(TM) SE"));
        Assert.Equal(17, JavaRuntime.MajorOf("17.0.10+7"));
        Assert.Null(JavaRuntime.ParseReleaseFile("garbage"));
    }

    [Fact]
    public void DetectsMinecraftFromCommandLine()
    {
        var cmd = "\"C:\\Java\\javaw.exe\" -Xmx4G -cp x.jar net.fabricmc.loader.impl.launch.knot.KnotClient " +
                  "--username Steve --version fabric-loader-0.16.14-1.21.1 --gameDir \"C:\\Users\\a b\\.minecraft\\phantom-instance\" --accessToken X";
        var p = MinecraftProcess.FromCommandLine(42, cmd)!;
        Assert.Equal("fabric-loader-0.16.14-1.21.1", p.Version);
        Assert.Equal("C:\\Users\\a b\\.minecraft\\phantom-instance", p.GameDirectory);
        Assert.True(p.IsFabric);
        Assert.True(p.IsPhantom);
        Assert.Null(MinecraftProcess.FromCommandLine(1, "java -jar server.jar nogui"));
    }

    [Fact]
    public void UpdateEvaluation()
    {
        Assert.Equal("1.1.0", UpdateChecker.Evaluate(JsonNode.Parse("{\"tag_name\":\"v1.1.0\",\"html_url\":\"https://x\"}"), "1.0.0")!.Version);
        Assert.Null(UpdateChecker.Evaluate(JsonNode.Parse("{\"tag_name\":\"v1.0.0\"}"), "1.0.0"));
        Assert.Null(UpdateChecker.Evaluate(JsonNode.Parse("{\"tag_name\":\"v2.0.0\",\"prerelease\":true}"), "1.0.0"));
        Assert.False(UpdateChecker.IsConfigured("https://api.github.com/repos/OWNER/PhantomClient/releases/latest"));
        Assert.False(UpdateChecker.IsConfigured("http://insecure.example/feed"));
        Assert.True(UpdateChecker.IsConfigured("https://api.github.com/repos/acme/PhantomClient/releases/latest"));
    }
}

public class InstallerTests
{
    private static readonly CompatibilityEntry Target = new() { Minecraft = "1.21.1", FabricLoader = "0.16.14", Java = 21 };

    [Fact]
    public void MergesLauncherProfileWithoutTouchingOthers()
    {
        using var t = new TempDir();
        File.WriteAllText(t["launcher_profiles.json"], """
            {"profiles":{"vanilla":{"name":"Latest","type":"latest-release"}},"settings":{"keepLauncherOpen":true},"version":3}
            """);
        var mc = new MinecraftInstallation(t.Path);
        var installer = new ClientInstaller(mc, new HttpClient());
        var opts = new ClientInstaller.Options(t["phantom-instance"], Target, null, 6144, "-XX:+UseG1GC", "data:image/png;base64,AA==", "phantom-client-1.0.0.jar");
        Assert.Equal(1, installer.WriteLauncherProfile(opts, DateTimeOffset.Parse("2026-01-02T03:04:05Z")));
        // Second write keeps "created" and updates lastUsed.
        installer.WriteLauncherProfile(opts, DateTimeOffset.Parse("2026-02-02T03:04:05Z"));

        var root = JsonNode.Parse(File.ReadAllText(t["launcher_profiles.json"]))!;
        Assert.Equal("Latest", root["profiles"]!["vanilla"]!["name"]!.GetValue<string>());
        Assert.True(root["settings"]!["keepLauncherOpen"]!.GetValue<bool>());
        var p = root["profiles"]![ClientInstaller.ProfileKey]!;
        Assert.Equal("fabric-loader-0.16.14-1.21.1", p["lastVersionId"]!.GetValue<string>());
        Assert.Equal(t["phantom-instance"], p["gameDir"]!.GetValue<string>());
        Assert.StartsWith("-Xmx6144M -Xms1024M -XX:+UseG1GC", p["javaArgs"]!.GetValue<string>());
        Assert.Equal("2026-01-02T03:04:05.000Z", p["created"]!.GetValue<string>());
        Assert.Equal("2026-02-02T03:04:05.000Z", p["lastUsed"]!.GetValue<string>());
        Assert.Null(p["javaDir"]);
        Assert.True(File.Exists(t["launcher_profiles.json.phantom-backup"]));
    }

    [Fact]
    public void CreatesProfilesFileWhenLauncherNeverRan()
    {
        using var t = new TempDir();
        var installer = new ClientInstaller(new MinecraftInstallation(t.Path), new HttpClient());
        installer.WriteLauncherProfile(new ClientInstaller.Options(t["inst"], Target, "C:\\jdk\\bin\\javaw.exe", 4096, "", "", "m.jar"), DateTimeOffset.UtcNow);
        var p = JsonNode.Parse(File.ReadAllText(t["launcher_profiles.json"]))!["profiles"]![ClientInstaller.ProfileKey]!;
        Assert.Equal("C:\\jdk\\bin\\javaw.exe", p["javaDir"]!.GetValue<string>());
    }

    [Fact]
    public void InstallsModReplacingOnlyOlderPhantomBuilds()
    {
        using var t = new TempDir();
        var mods = Directory.CreateDirectory(t["inst/mods"]).FullName;
        File.WriteAllText(Path.Combine(mods, "phantom-client-0.9.0.jar"), "old");
        File.WriteAllText(Path.Combine(mods, "sodium.jar"), "keep");
        var installer = new ClientInstaller(new MinecraftInstallation(t.Path), new HttpClient());
        using var jar = new MemoryStream(new byte[] { 0x50, 0x4B, 3, 4 });
        installer.InstallMod(t["inst"], jar, "phantom-client-1.0.0.jar");
        var files = Directory.GetFiles(mods).Select(Path.GetFileName).OrderBy(x => x).ToArray();
        Assert.Equal(new[] { "phantom-client-1.0.0.jar", "sodium.jar" }, files);
    }

    [Fact]
    public void WritesFabricVersionAndListsIt()
    {
        using var t = new TempDir();
        var mc = new MinecraftInstallation(t.Path);
        var installer = new ClientInstaller(mc, new HttpClient());
        var id = ClientInstaller.FabricVersionId(Target);
        installer.WriteVersion(id, $"{{\"id\":\"{id}\",\"inheritsFrom\":\"1.21.1\",\"type\":\"release\"}}");
        Directory.CreateDirectory(t["versions/1.21.1"]);
        File.WriteAllText(t["versions/1.21.1/1.21.1.json"], "{\"id\":\"1.21.1\",\"type\":\"release\"}");
        Assert.True(mc.HasVersion(id));
        Assert.True(File.Exists(t[$"versions/{id}/{id}.jar"]));
        var versions = mc.Versions();
        Assert.Contains(versions, v => v.IsFabric && v.BaseVersion == "1.21.1");
        Assert.Contains(versions, v => v.Id == "1.21.1" && !v.IsFabric);
    }
}

public class ProfileTests
{
    [Fact]
    public void RequestImportExportDelete()
    {
        using var t = new TempDir();
        var store = new PhantomProfiles(t.Path);
        Assert.Equal(new[] { "default" }, store.List());

        store.RequestProfile("Creative Tests");
        Assert.Equal("Creative Tests", JsonNode.Parse(File.ReadAllText(t["launcher.json"]))!["activeProfile"]!.GetValue<string>());
        Assert.Throws<ArgumentException>(() => store.RequestProfile("../evil"));

        var external = t["My PvE_20260101-120000.json"];
        File.WriteAllText(external, "{\"modules\":{\"ESP\":{\"enabled\":true},\"Fly\":{\"enabled\":false}}}");
        Assert.Equal("My PvE", store.Import(external));
        Assert.Equal("My PvE 2", store.Import(external));
        Assert.Equal(1, store.Describe("My PvE").EnabledModules);

        var bad = t["bad.json"];
        File.WriteAllText(bad, "{\"hello\":1}");
        Assert.Throws<InvalidDataException>(() => store.Import(bad));

        store.Duplicate("My PvE", "Copy");
        store.Export("Copy", t["out.json"]);
        Assert.True(File.Exists(t["out.json"]));

        Assert.Throws<InvalidOperationException>(() => store.Delete("Copy", "Copy"));
        store.Delete("Copy", "default");
        Assert.DoesNotContain("Copy", store.List());
        Assert.Single(Directory.GetFiles(store.BackupsDir));
    }

    [Fact]
    public void ReadsClientStatusHeartbeat()
    {
        using var t = new TempDir();
        var now = DateTimeOffset.UtcNow;
        File.WriteAllText(t["status.json"], $$"""
            {"running":true,"pid":7,"profile":"default","context":"private-world","fps":144,"timestamp":{{now.ToUnixTimeMilliseconds()}},"enabledModules":["ESP"]}
            """);
        var s = ClientStatus.TryRead(t["status.json"])!;
        Assert.True(s.IsLive(now));
        Assert.False(s.IsLive(now.AddSeconds(30)));
        Assert.Equal("Private world", s.ContextDisplay);
        Assert.Null(ClientStatus.TryRead(t["missing.json"]));
    }
}

public class LogTailerTests
{
    [Fact]
    public void FollowsAppendsAndRotation()
    {
        using var t = new TempDir();
        var file = t["latest.log"];
        File.WriteAllText(file, "old line\n");
        var lines = new List<string>();
        using var tailer = new LogTailer(file, l => { lock (lines) lines.Add(l); }, fromEnd: true, TimeSpan.FromHours(1));
        File.AppendAllText(file, "first\nsecond\npart");
        tailer.Poll();
        File.AppendAllText(file, "ial\n");
        tailer.Poll();
        Assert.Equal(new[] { "first", "second", "partial" }, lines);

        // New game session truncates the file.
        File.WriteAllText(file, "new session\n");
        tailer.Poll();
        Assert.Equal("new session", lines[^1]);
    }
}
