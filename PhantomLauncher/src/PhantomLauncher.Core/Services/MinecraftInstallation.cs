using System.Text.Json.Nodes;

namespace PhantomLauncher.Core.Services;

/// <summary>Inspects a .minecraft directory: installed versions, launcher profile files, runtimes.</summary>
public sealed class MinecraftInstallation
{
    public string Root { get; }

    public MinecraftInstallation(string root) => Root = root;

    public static string DefaultRoot(string appData) => Path.Combine(appData, ".minecraft");

    public bool Exists => Directory.Exists(Root);
    public string VersionsDir => Path.Combine(Root, "versions");

    /// <summary>The official launcher's profile files (classic and Microsoft Store / Xbox variants).</summary>
    public IEnumerable<string> LauncherProfileFiles
    {
        get
        {
            foreach (var name in new[] { "launcher_profiles.json", "launcher_profiles_microsoft_store.json" })
            {
                var p = Path.Combine(Root, name);
                if (File.Exists(p)) yield return p;
            }
        }
    }

    public sealed record InstalledVersion(string Id, string? InheritsFrom, string Type)
    {
        public string BaseVersion => InheritsFrom ?? Id;
        public bool IsFabric => Id.StartsWith("fabric-loader-", StringComparison.Ordinal);
    }

    public IReadOnlyList<InstalledVersion> Versions()
    {
        var list = new List<InstalledVersion>();
        if (!Directory.Exists(VersionsDir)) return list;
        foreach (var dir in Directory.EnumerateDirectories(VersionsDir))
        {
            var id = Path.GetFileName(dir);
            var json = Path.Combine(dir, id + ".json");
            if (!File.Exists(json)) continue;
            try
            {
                var node = JsonNode.Parse(File.ReadAllText(json));
                list.Add(new InstalledVersion(id, node?["inheritsFrom"]?.GetValue<string>(), node?["type"]?.GetValue<string>() ?? "release"));
            }
            catch
            {
                list.Add(new InstalledVersion(id, null, "unknown"));
            }
        }
        return list.OrderByDescending(v => SemVer.Parse(v.BaseVersion)).ToList();
    }

    public bool HasVersion(string id) => File.Exists(Path.Combine(VersionsDir, id, id + ".json"));

    /// <summary>Java runtimes the official launcher downloaded (java-runtime-delta etc.).</summary>
    public IEnumerable<string> BundledJavaHomes()
    {
        var runtime = Path.Combine(Root, "runtime");
        if (!Directory.Exists(runtime)) yield break;
        foreach (var component in Directory.EnumerateDirectories(runtime))
        {
            foreach (var platform in Directory.EnumerateDirectories(component))
            {
                var home = Path.Combine(platform, Path.GetFileName(component));
                if (Directory.Exists(home)) yield return home;
            }
        }
    }
}
