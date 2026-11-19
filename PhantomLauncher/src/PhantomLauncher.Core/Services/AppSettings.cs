using System.Text.Json;
using System.Text.Json.Serialization;

namespace PhantomLauncher.Core.Services;

/// <summary>Launcher preferences, stored in %LOCALAPPDATA%\PhantomClient\settings.json.</summary>
public sealed class AppSettings
{
    private static readonly JsonSerializerOptions Json = new() { WriteIndented = true };

    [JsonPropertyName("minecraftDirectory")] public string? MinecraftDirectory { get; set; }
    [JsonPropertyName("instanceDirectory")] public string? InstanceDirectory { get; set; }
    [JsonPropertyName("javaPath")] public string? JavaPath { get; set; }
    [JsonPropertyName("memoryMb")] public int MemoryMb { get; set; } = 4096;
    [JsonPropertyName("extraJvmArgs")] public string ExtraJvmArgs { get; set; } = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200";
    [JsonPropertyName("minecraftLauncherPath")] public string? MinecraftLauncherPath { get; set; }
    [JsonPropertyName("selectedProfile")] public string SelectedProfile { get; set; } = "default";
    [JsonPropertyName("closeOnLaunch")] public bool CloseOnLaunch { get; set; }
    [JsonPropertyName("checkUpdates")] public bool CheckUpdates { get; set; } = true;
    [JsonPropertyName("updateUrl")] public string? UpdateUrl { get; set; }
    [JsonPropertyName("consoleFilter")] public string ConsoleFilter { get; set; } = "All";

    public static AppSettings Load(string path)
    {
        try
        {
            if (File.Exists(path))
                return JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(path)) ?? new AppSettings();
        }
        catch (Exception e)
        {
            Log.Warn($"Settings file unreadable, using defaults ({e.Message})");
            try { File.Copy(path, path + ".broken", true); } catch { }
        }
        return new AppSettings();
    }

    public void Save(string path)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var tmp = path + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(this, Json));
        File.Move(tmp, path, true);
    }

    public int ClampedMemoryMb => Math.Clamp(MemoryMb, 1024, 32768);
}
