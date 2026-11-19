using System.IO;
using System.Net.Http;
using System.Reflection;
using PhantomLauncher.Core.Models;
using PhantomLauncher.Core.Services;

namespace PhantomLauncher.Services;

/// <summary>Install-and-launch pipeline behind the big Launch button.</summary>
public sealed class LaunchService
{
    private readonly HttpClient _http;

    public LaunchService(HttpClient http) => _http = http;

    public static Stream? OpenEmbedded(string name) =>
        Assembly.GetExecutingAssembly().GetManifestResourceStream(name);

    /// <summary>The embedded mod jar, or a phantom-client-*.jar placed next to the exe.</summary>
    public static (Stream stream, string fileName)? OpenModJar(string clientVersion)
    {
        var embedded = OpenEmbedded("phantom-client.jar");
        if (embedded is not null) return (embedded, $"phantom-client-{clientVersion}.jar");
        var dir = AppContext.BaseDirectory;
        var local = Directory.EnumerateFiles(dir, "phantom-client-*.jar").OrderByDescending(f => f).FirstOrDefault()
                    ?? Directory.EnumerateFiles(Path.Combine(dir, "mods"), "phantom-client-*.jar").FirstOrDefault();
        return local is null ? null : (File.OpenRead(local), Path.GetFileName(local));
    }

    public sealed record Request(MinecraftInstallation Minecraft, string InstanceDir, CompatibilityEntry Target,
        JavaRuntime? Java, AppSettings Settings, string Profile, string ClientVersion);

    public async Task RunAsync(Request r, IProgress<string> progress, CancellationToken ct)
    {
        if (!r.Minecraft.Exists)
            throw new InvalidOperationException($"No Minecraft installation at {r.Minecraft.Root}. Install the Minecraft Launcher and start it once.");

        var launcher = MinecraftLauncherLocator.Find(r.Settings.MinecraftLauncherPath)
                       ?? throw new InvalidOperationException("Minecraft Launcher not found. Install it from minecraft.net or set its path in Settings.");

        var installer = new ClientInstaller(r.Minecraft, _http);
        await installer.InstallFabricAsync(r.Target, progress, ct);

        progress.Report("Installing Phantom Client...");
        var mod = OpenModJar(r.ClientVersion)
                  ?? throw new FileNotFoundException("The Phantom mod jar is missing from this build (Payload/phantom-client.jar).");
        string installed;
        using (mod.stream) installed = installer.InstallMod(r.InstanceDir, mod.stream, mod.fileName);
        Log.Info($"Mod installed: {installed}");

        progress.Report($"Selecting profile '{r.Profile}'...");
        new PhantomProfiles(Path.Combine(r.InstanceDir, "config", "phantom")).RequestProfile(r.Profile);

        progress.Report("Updating Minecraft Launcher profile...");
        string icon;
        using (var iconStream = OpenEmbedded("profile-icon.png"))
        {
            using var ms = new MemoryStream();
            iconStream?.CopyTo(ms);
            icon = "data:image/png;base64," + Convert.ToBase64String(ms.ToArray());
        }
        var javaDir = r.Java is { Major: >= 21 } && !string.IsNullOrWhiteSpace(r.Settings.JavaPath) ? r.Java.Executable : null;
        var count = installer.WriteLauncherProfile(new ClientInstaller.Options(r.InstanceDir, r.Target, javaDir,
            r.Settings.ClampedMemoryMb, r.Settings.ExtraJvmArgs, icon, mod.fileName), DateTimeOffset.UtcNow);
        Log.Info($"'Phantom Client' profile written to {count} launcher profile file(s)");

        progress.Report($"Opening {launcher.Description}...");
        MinecraftLauncherLocator.Start(launcher, r.Minecraft.Root);
        progress.Report("Select \"Phantom Client\" and press Play in the Minecraft Launcher");
    }
}
