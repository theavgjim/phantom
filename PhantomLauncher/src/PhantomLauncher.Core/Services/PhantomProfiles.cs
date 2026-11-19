using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace PhantomLauncher.Core.Services;

/// <summary>
/// Launcher-side view of the client's config directory (&lt;instance&gt;/config/phantom).
/// Mirrors dev.phantom.config.ConfigManager's file layout. The launcher never edits a profile
/// while the game runs; it requests one via launcher.json, which the client consumes at start.
/// </summary>
public sealed partial class PhantomProfiles
{
    public string Root { get; }
    public string ProfilesDir => Path.Combine(Root, "profiles");
    public string BackupsDir => Path.Combine(Root, "backups");
    public string ExportsDir => Path.Combine(Root, "exports");
    public string StatusFile => Path.Combine(Root, "status.json");

    public PhantomProfiles(string configRoot) => Root = configRoot;

    [GeneratedRegex(@"^[A-Za-z0-9 _\-]{1,32}$")]
    private static partial Regex ValidName();

    public static bool IsValidName(string? name) =>
        name is not null && ValidName().IsMatch(name) && name.Trim() == name;

    public IReadOnlyList<string> List()
    {
        var names = new List<string>();
        if (Directory.Exists(ProfilesDir))
            names.AddRange(Directory.EnumerateFiles(ProfilesDir, "*.json").Select(Path.GetFileNameWithoutExtension)!);
        if (!names.Contains("default")) names.Add("default");
        return names.OrderBy(n => n, StringComparer.OrdinalIgnoreCase).ToList();
    }

    /// <summary>Last profile the client used (phantom.json).</summary>
    public string? ActiveProfile()
    {
        try
        {
            var meta = Path.Combine(Root, "phantom.json");
            if (!File.Exists(meta)) return null;
            return JsonNode.Parse(File.ReadAllText(meta))?["activeProfile"]?.GetValue<string>();
        }
        catch { return null; }
    }

    /// <summary>Asks the client to load <paramref name="profile"/> on next start.</summary>
    public void RequestProfile(string profile)
    {
        if (!IsValidName(profile)) throw new ArgumentException("Invalid profile name");
        Directory.CreateDirectory(Root);
        var json = new JsonObject
        {
            ["activeProfile"] = profile,
            ["requestedAt"] = DateTimeOffset.UtcNow.ToString("O"),
            ["requestedBy"] = "PhantomClient.exe"
        };
        File.WriteAllText(Path.Combine(Root, "launcher.json"), json.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
    }

    public string PathOf(string profile) => Path.Combine(ProfilesDir, profile + ".json");

    public sealed record ProfileInfo(string Name, int EnabledModules, DateTime? Modified, long Size);

    public ProfileInfo Describe(string name)
    {
        var path = PathOf(name);
        if (!File.Exists(path)) return new ProfileInfo(name, 0, null, 0);
        var count = 0;
        try
        {
            var modules = JsonNode.Parse(File.ReadAllText(path))?["modules"]?.AsObject();
            if (modules is not null)
                count = modules.Count(kv => kv.Value?["enabled"]?.GetValue<bool>() == true);
        }
        catch { /* corrupt profile: show 0 */ }
        var fi = new FileInfo(path);
        return new ProfileInfo(name, count, fi.LastWriteTime, fi.Length);
    }

    public void Duplicate(string source, string target)
    {
        Require(target);
        if (File.Exists(PathOf(target))) throw new IOException($"Profile '{target}' already exists");
        Directory.CreateDirectory(ProfilesDir);
        if (File.Exists(PathOf(source))) File.Copy(PathOf(source), PathOf(target));
        else File.WriteAllText(PathOf(target), "{}");
    }

    public void Delete(string name, string? activeProfile)
    {
        Require(name);
        if (string.Equals(name, activeProfile, StringComparison.Ordinal)) throw new InvalidOperationException("Cannot delete the active profile");
        var path = PathOf(name);
        if (!File.Exists(path)) return;
        Directory.CreateDirectory(BackupsDir);
        File.Copy(path, Path.Combine(BackupsDir, $"{name}_{DateTime.Now:yyyyMMdd-HHmmss-fff}.json"), true);
        File.Delete(path);
    }

    /// <summary>Validates and copies an external profile file in. Returns the new profile name.</summary>
    public string Import(string file)
    {
        JsonObject root;
        try { root = JsonNode.Parse(File.ReadAllText(file))?.AsObject() ?? throw new InvalidDataException(); }
        catch (Exception e) when (e is not IOException) { throw new InvalidDataException("Not a valid JSON object", e); }
        if (root["modules"] is null && root["hud"] is null) throw new InvalidDataException("Not a Phantom profile");

        var baseName = Regex.Replace(Path.GetFileNameWithoutExtension(file), @"_\d{8}-\d{6}$", "");
        baseName = Regex.Replace(baseName, @"[^A-Za-z0-9 _\-]", "_").Trim();
        if (baseName.Length > 32) baseName = baseName[..32].Trim();
        if (baseName.Length == 0) baseName = "imported";
        var name = baseName;
        for (var i = 2; File.Exists(PathOf(name)); i++)
        {
            var suffix = " " + i;
            name = (baseName.Length + suffix.Length > 32 ? baseName[..(32 - suffix.Length)] : baseName) + suffix;
        }
        Directory.CreateDirectory(ProfilesDir);
        File.Copy(file, PathOf(name));
        return name;
    }

    public void Export(string name, string destination)
    {
        if (!File.Exists(PathOf(name))) throw new FileNotFoundException("Profile has not been saved by the client yet", PathOf(name));
        File.Copy(PathOf(name), destination, true);
    }

    private static void Require(string name)
    {
        if (!IsValidName(name)) throw new ArgumentException("Profile names use 1-32 letters, digits, spaces, '-' or '_'");
    }
}
