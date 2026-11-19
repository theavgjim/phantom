namespace PhantomLauncher.Core.Models;

public sealed record JavaRuntime(string Executable, string Version, int Major, string Vendor, string Source)
{
    public string Display => $"Java {Version} · {Vendor} ({Source})";

    /// <summary>Parses the JDK/JRE "release" file next to bin/ (fast, no process start).</summary>
    public static (string version, int major, string vendor)? ParseReleaseFile(string content)
    {
        string? Get(string key)
        {
            foreach (var line in content.Split('\n'))
            {
                var l = line.Trim();
                if (l.StartsWith(key + "=", StringComparison.Ordinal)) return l[(key.Length + 1)..].Trim('"');
            }
            return null;
        }

        var version = Get("JAVA_VERSION");
        if (string.IsNullOrEmpty(version)) return null;
        var vendor = Get("IMPLEMENTOR") ?? "Unknown";
        return (version, MajorOf(version), vendor);
    }

    /// <summary>Parses the first line of `java -version` (stderr).</summary>
    public static (string version, int major)? ParseVersionOutput(string output)
    {
        var start = output.IndexOf('"');
        if (start < 0) return null;
        var end = output.IndexOf('"', start + 1);
        if (end < 0) return null;
        var v = output[(start + 1)..end];
        return (v, MajorOf(v));
    }

    public static int MajorOf(string version)
    {
        var parts = version.Split('.', '_', '-', '+');
        if (!int.TryParse(parts[0], out var first)) return 0;
        // Legacy "1.8.0_392" -> 8
        if (first == 1 && parts.Length > 1 && int.TryParse(parts[1], out var second)) return second;
        return first;
    }
}
