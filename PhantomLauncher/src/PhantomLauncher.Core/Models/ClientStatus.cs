using System.Text.Json;
using System.Text.Json.Serialization;

namespace PhantomLauncher.Core.Models;

/// <summary>Heartbeat written by the mod to config/phantom/status.json every 5 s.</summary>
public sealed class ClientStatus
{
    [JsonPropertyName("running")] public bool Running { get; set; }
    [JsonPropertyName("pid")] public long Pid { get; set; }
    [JsonPropertyName("clientVersion")] public string ClientVersion { get; set; } = "";
    [JsonPropertyName("minecraftVersion")] public string MinecraftVersion { get; set; } = "";
    [JsonPropertyName("profile")] public string Profile { get; set; } = "";
    [JsonPropertyName("context")] public string Context { get; set; } = "";
    [JsonPropertyName("fps")] public int Fps { get; set; }
    [JsonPropertyName("timestamp")] public long Timestamp { get; set; }
    [JsonPropertyName("enabledModules")] public List<string> EnabledModules { get; set; } = new();

    /// <summary>The heartbeat is considered live for 15 s.</summary>
    public bool IsLive(DateTimeOffset now) =>
        Running && now.ToUnixTimeMilliseconds() - Timestamp < 15_000;

    public static ClientStatus? TryRead(string path)
    {
        try
        {
            if (!File.Exists(path)) return null;
            using var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            return JsonSerializer.Deserialize<ClientStatus>(fs);
        }
        catch
        {
            return null;
        }
    }

    public string ContextDisplay => Context switch
    {
        "private-world" => "Private world",
        "remote-server" => "Multiplayer (training modules locked)",
        "menu" => "Main menu",
        _ => Context
    };
}
