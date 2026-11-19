namespace PhantomLauncher.Core.Models;

public sealed record MinecraftProcess(int Pid, string? Version, string? GameDirectory, bool IsFabric, bool IsPhantom)
{
    /// <summary>Extracts version / game dir / loader from a Java command line.</summary>
    public static MinecraftProcess? FromCommandLine(int pid, string? commandLine)
    {
        if (string.IsNullOrEmpty(commandLine)) return null;
        var isClient = commandLine.Contains("net.minecraft.client.main.Main", StringComparison.Ordinal)
                       || commandLine.Contains("net.fabricmc.loader.impl.launch.knot.KnotClient", StringComparison.Ordinal)
                       || commandLine.Contains("--accessToken", StringComparison.Ordinal) && commandLine.Contains("--version", StringComparison.Ordinal);
        if (!isClient) return null;
        var version = Arg(commandLine, "--version");
        var gameDir = Arg(commandLine, "--gameDir");
        var fabric = commandLine.Contains("fabric", StringComparison.OrdinalIgnoreCase);
        var phantom = (gameDir?.Contains("phantom", StringComparison.OrdinalIgnoreCase) ?? false)
                      || commandLine.Contains("phantom-client", StringComparison.OrdinalIgnoreCase);
        return new MinecraftProcess(pid, version, gameDir, fabric, phantom);
    }

    private static string? Arg(string cmd, string name)
    {
        var i = cmd.IndexOf(name + " ", StringComparison.Ordinal);
        if (i < 0) return null;
        var rest = cmd[(i + name.Length + 1)..].TrimStart();
        if (rest.StartsWith('"'))
        {
            var end = rest.IndexOf('"', 1);
            return end > 0 ? rest[1..end] : rest[1..];
        }
        var space = rest.IndexOf(' ');
        return space < 0 ? rest : rest[..space];
    }
}
