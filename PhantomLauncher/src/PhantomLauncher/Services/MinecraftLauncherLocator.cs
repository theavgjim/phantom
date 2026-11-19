using System.IO;
using System.Diagnostics;
using PhantomLauncher.Core.Services;

namespace PhantomLauncher.Services;

/// <summary>Finds and starts the official Minecraft Launcher (classic installer, Xbox app or Microsoft Store).</summary>
public static class MinecraftLauncherLocator
{
    private const string StoreAppId = @"shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft";

    public sealed record LauncherInfo(string Description, string? Executable, bool IsStoreApp);

    public static LauncherInfo? Find(string? overridePath)
    {
        if (!string.IsNullOrWhiteSpace(overridePath) && File.Exists(overridePath))
            return new LauncherInfo("Custom launcher", overridePath, false);

        var candidates = new[]
        {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Minecraft Launcher", "MinecraftLauncher.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Minecraft Launcher", "MinecraftLauncher.exe"),
            @"C:\XboxGames\Minecraft Launcher\Content\Minecraft.exe",
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "Minecraft Launcher", "MinecraftLauncher.exe"),
        };
        foreach (var c in candidates)
            if (File.Exists(c)) return new LauncherInfo("Minecraft Launcher", c, false);

        var storePkg = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Packages", "Microsoft.4297127D64EC6_8wekyb3d8bbwe");
        if (Directory.Exists(storePkg)) return new LauncherInfo("Minecraft Launcher (Microsoft Store)", null, true);
        return null;
    }

    public static void Start(LauncherInfo info, string workingDirectory)
    {
        if (info.IsStoreApp)
        {
            Process.Start(new ProcessStartInfo("explorer.exe", StoreAppId) { UseShellExecute = true });
        }
        else
        {
            Process.Start(new ProcessStartInfo(info.Executable!)
            {
                UseShellExecute = true,
                WorkingDirectory = Path.GetDirectoryName(info.Executable!) ?? workingDirectory
            });
        }
        Log.Info($"Started {info.Description}");
    }
}
