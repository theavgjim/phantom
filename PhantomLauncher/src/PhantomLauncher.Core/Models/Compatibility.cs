using System.Text.Json;
using System.Text.Json.Serialization;
using PhantomLauncher.Core.Services;

namespace PhantomLauncher.Core.Models;

public sealed class CompatibilityEntry
{
    [JsonPropertyName("minecraft")] public string Minecraft { get; set; } = "";
    [JsonPropertyName("fabricLoader")] public string FabricLoader { get; set; } = "";
    [JsonPropertyName("java")] public int Java { get; set; }
    [JsonPropertyName("status")] public string Status { get; set; } = "supported";
}

/// <summary>Same compatibility.json the mod ships, embedded in the launcher.</summary>
public sealed class CompatibilityTable
{
    [JsonPropertyName("clientVersion")] public string ClientVersion { get; set; } = "0.0.0";
    [JsonPropertyName("updateUrl")] public string UpdateUrl { get; set; } = "";
    [JsonPropertyName("supported")] public List<CompatibilityEntry> Supported { get; set; } = new();
    [JsonPropertyName("planned")] public List<string> Planned { get; set; } = new();

    public static CompatibilityTable Parse(string json) =>
        JsonSerializer.Deserialize<CompatibilityTable>(json) ?? throw new InvalidDataException("Empty compatibility table");

    public CompatibilityEntry? Find(string minecraftVersion) =>
        Supported.FirstOrDefault(e => e.Minecraft == minecraftVersion);

    public CompatibilityEntry Primary => Supported.First();

    public CompatibilityResult Check(string? minecraftVersion, int javaMajor)
    {
        if (minecraftVersion is null)
            return new(CompatState.Warning, $"Minecraft {Primary.Minecraft} will be downloaded by the Minecraft Launcher on first start");
        var entry = Find(minecraftVersion);
        if (entry is null)
            return new(CompatState.Unsupported, $"Minecraft {minecraftVersion} is not supported (supported: {string.Join(", ", Supported.Select(s => s.Minecraft))})");
        if (javaMajor > 0 && javaMajor < entry.Java)
            return new(CompatState.Warning, $"Java {entry.Java}+ recommended (selected Java {javaMajor}); the Minecraft Launcher's bundled runtime will be used");
        return new(CompatState.Compatible, $"Minecraft {minecraftVersion} with Fabric {entry.FabricLoader} is {entry.Status}");
    }
}

public enum CompatState { Compatible, Warning, Unsupported }

public sealed record CompatibilityResult(CompatState State, string Message);
