using System.Text.Json;
using System.Text.Json.Nodes;
using PhantomLauncher.Core.Models;

namespace PhantomLauncher.Core.Services;

/// <summary>
/// Prepares a launchable "Phantom Client" setup using only documented, standard mechanisms:
/// <list type="number">
/// <item>Fabric Loader version JSON from meta.fabricmc.net (what the official Fabric installer writes).</item>
/// <item>A separate game directory (instance) so the user's own mods are untouched.</item>
/// <item>The Phantom mod jar copied into the instance's mods folder.</item>
/// <item>A "Phantom Client" profile in the official launcher's launcher_profiles.json.</item>
/// </list>
/// </summary>
public sealed class ClientInstaller
{
    public const string ProfileKey = "phantom-client";
    private static readonly JsonSerializerOptions Pretty = new() { WriteIndented = true };

    private readonly MinecraftInstallation _mc;
    private readonly HttpClient _http;

    public ClientInstaller(MinecraftInstallation mc, HttpClient http)
    {
        _mc = mc;
        _http = http;
    }

    public static string FabricVersionId(CompatibilityEntry e) => $"fabric-loader-{e.FabricLoader}-{e.Minecraft}";

    public sealed record Options(string InstanceDir, CompatibilityEntry Target, string? JavaDir, int MemoryMb,
        string ExtraJvmArgs, string ProfileIconDataUri, string ModFileName);

    public async Task InstallFabricAsync(CompatibilityEntry target, IProgress<string>? progress, CancellationToken ct)
    {
        var id = FabricVersionId(target);
        if (_mc.HasVersion(id))
        {
            progress?.Report($"Fabric Loader {target.FabricLoader} already installed");
            return;
        }
        progress?.Report($"Downloading Fabric Loader {target.FabricLoader} profile...");
        var url = $"https://meta.fabricmc.net/v2/versions/loader/{Uri.EscapeDataString(target.Minecraft)}/{Uri.EscapeDataString(target.FabricLoader)}/profile/json";
        var json = await _http.GetStringAsync(url, ct).ConfigureAwait(false);
        var node = JsonNode.Parse(json) ?? throw new InvalidDataException("Empty Fabric profile");
        if (node["id"]?.GetValue<string>() != id) throw new InvalidDataException($"Unexpected Fabric profile id {node["id"]}");
        WriteVersion(id, json);
        Log.Info($"Installed version {id}");
    }

    /// <summary>Writes versions/&lt;id&gt;/&lt;id&gt;.json plus the empty jar the official launcher expects.</summary>
    public void WriteVersion(string id, string versionJson)
    {
        var dir = Path.Combine(_mc.VersionsDir, id);
        Directory.CreateDirectory(dir);
        File.WriteAllText(Path.Combine(dir, id + ".json"), versionJson);
        var jar = Path.Combine(dir, id + ".jar");
        if (!File.Exists(jar)) File.WriteAllBytes(jar, Array.Empty<byte>());
    }

    /// <summary>Copies the mod into the instance, replacing older Phantom builds only.</summary>
    public string InstallMod(string instanceDir, Stream modJar, string fileName)
    {
        var mods = Path.Combine(instanceDir, "mods");
        Directory.CreateDirectory(mods);
        foreach (var old in Directory.EnumerateFiles(mods, "phantom-client-*.jar"))
        {
            if (!string.Equals(Path.GetFileName(old), fileName, StringComparison.OrdinalIgnoreCase)) File.Delete(old);
        }
        var target = Path.Combine(mods, fileName);
        var tmp = target + ".tmp";
        using (var fs = File.Create(tmp)) modJar.CopyTo(fs);
        File.Move(tmp, target, true);
        return target;
    }

    /// <summary>Adds/updates the Phantom profile in every launcher_profiles*.json and marks it last used.</summary>
    public int WriteLauncherProfile(Options o, DateTimeOffset now)
    {
        var files = _mc.LauncherProfileFiles.ToList();
        if (files.Count == 0)
        {
            // The official launcher has never run here; create the classic file so it picks the profile up.
            var path = Path.Combine(_mc.Root, "launcher_profiles.json");
            File.WriteAllText(path, new JsonObject { ["profiles"] = new JsonObject(), ["version"] = 3 }.ToJsonString(Pretty));
            files.Add(path);
        }
        foreach (var file in files) MergeProfile(file, o, now);
        return files.Count;
    }

    public static void MergeProfile(string file, Options o, DateTimeOffset now)
    {
        var root = JsonNode.Parse(File.ReadAllText(file))?.AsObject() ?? new JsonObject();
        var profiles = root["profiles"] as JsonObject;
        if (profiles is null)
        {
            profiles = new JsonObject();
            root["profiles"] = profiles;
        }
        var existing = profiles[ProfileKey] as JsonObject;
        var created = existing?["created"]?.GetValue<string>() ?? now.ToString("yyyy-MM-ddTHH:mm:ss.fffZ");
        var jvm = $"-Xmx{o.MemoryMb}M -Xms{Math.Min(1024, o.MemoryMb)}M {o.ExtraJvmArgs}".Trim();
        var profile = new JsonObject
        {
            ["name"] = "Phantom Client",
            ["type"] = "custom",
            ["created"] = created,
            ["lastUsed"] = now.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ"),
            ["lastVersionId"] = FabricVersionId(o.Target),
            ["gameDir"] = o.InstanceDir,
            ["icon"] = o.ProfileIconDataUri,
            ["javaArgs"] = jvm
        };
        if (!string.IsNullOrWhiteSpace(o.JavaDir)) profile["javaDir"] = o.JavaDir;
        profiles[ProfileKey] = profile;

        var tmp = file + ".phantom.tmp";
        File.WriteAllText(tmp, root.ToJsonString(Pretty));
        File.Copy(file, file + ".phantom-backup", true);
        File.Move(tmp, file, true);
    }
}
