using System.IO;
using System.Diagnostics;
using Microsoft.Win32;
using PhantomLauncher.Core.Models;
using PhantomLauncher.Core.Services;

namespace PhantomLauncher.Services;

/// <summary>Finds installed Java runtimes: JAVA_HOME, PATH, registry, common folders and Minecraft's bundled runtimes.</summary>
public static class JavaLocator
{
    public static IReadOnlyList<JavaRuntime> Find(MinecraftInstallation mc)
    {
        var homes = new List<(string home, string source)>();

        var javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrWhiteSpace(javaHome)) homes.Add((javaHome, "JAVA_HOME"));

        foreach (var dir in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(Path.PathSeparator))
        {
            if (dir.Length > 0 && File.Exists(Path.Combine(dir, "java.exe"))) homes.Add((Path.GetDirectoryName(dir.TrimEnd('\\'))!, "PATH"));
        }

        foreach (var (hive, view) in new[] { (RegistryHive.LocalMachine, RegistryView.Registry64), (RegistryHive.LocalMachine, RegistryView.Registry32) })
        {
            try
            {
                using var root = RegistryKey.OpenBaseKey(hive, view);
                foreach (var path in new[] { @"SOFTWARE\JavaSoft\JDK", @"SOFTWARE\JavaSoft\Java Runtime Environment", @"SOFTWARE\JavaSoft\JRE" })
                {
                    using var key = root.OpenSubKey(path);
                    if (key is null) continue;
                    foreach (var ver in key.GetSubKeyNames())
                    {
                        using var vk = key.OpenSubKey(ver);
                        if (vk?.GetValue("JavaHome") is string h) homes.Add((h, "Registry"));
                    }
                }
                foreach (var vendorPath in new[] { @"SOFTWARE\Eclipse Adoptium\JDK", @"SOFTWARE\Eclipse Adoptium\JRE", @"SOFTWARE\Microsoft\JDK", @"SOFTWARE\Azul Systems\Zulu" })
                {
                    using var key = root.OpenSubKey(vendorPath);
                    if (key is null) continue;
                    foreach (var ver in key.GetSubKeyNames())
                    {
                        using var msi = key.OpenSubKey(ver + @"\hotspot\MSI") ?? key.OpenSubKey(ver);
                        if (msi?.GetValue("Path") is string h) homes.Add((h, "Registry"));
                        else if (msi?.GetValue("InstallationPath") is string h2) homes.Add((h2, "Registry"));
                    }
                }
            }
            catch (Exception e) { Log.Debug($"Registry scan skipped: {e.Message}"); }
        }

        foreach (var pf in new[] { Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86) })
        {
            if (string.IsNullOrEmpty(pf)) continue;
            foreach (var vendor in new[] { "Java", "Eclipse Adoptium", "Microsoft", "Zulu", "BellSoft", "Amazon Corretto", "Semeru" })
            {
                var dir = Path.Combine(pf, vendor);
                if (!Directory.Exists(dir)) continue;
                foreach (var sub in Directory.EnumerateDirectories(dir)) homes.Add((sub, vendor));
            }
        }

        foreach (var h in mc.BundledJavaHomes()) homes.Add((h, "Minecraft Launcher"));
        var store = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            @"Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime");
        if (Directory.Exists(store))
            foreach (var h in new MinecraftInstallation(Path.GetDirectoryName(store)!).BundledJavaHomes()) homes.Add((h, "Minecraft Launcher (Store)"));

        var result = new List<JavaRuntime>();
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var (home, source) in homes)
        {
            var exe = Path.Combine(home, "bin", "javaw.exe");
            if (!File.Exists(exe)) exe = Path.Combine(home, "bin", "java.exe");
            if (!File.Exists(exe)) continue;
            var full = Path.GetFullPath(exe);
            if (!seen.Add(full)) continue;
            var rt = Describe(full, source);
            if (rt is not null) result.Add(rt);
        }
        return result.OrderByDescending(r => r.Major).ThenBy(r => r.Source).ToList();
    }

    public static JavaRuntime? Describe(string exe, string source)
    {
        try
        {
            var home = Path.GetDirectoryName(Path.GetDirectoryName(exe))!;
            var release = Path.Combine(home, "release");
            if (File.Exists(release))
            {
                var parsed = JavaRuntime.ParseReleaseFile(File.ReadAllText(release));
                if (parsed is { } p) return new JavaRuntime(exe, p.version, p.major, p.vendor, source);
            }
            // Fallback: ask the runtime (java.exe prints the version on stderr).
            var javaExe = Path.Combine(Path.GetDirectoryName(exe)!, "java.exe");
            var psi = new ProcessStartInfo(File.Exists(javaExe) ? javaExe : exe, "-version")
            {
                RedirectStandardError = true, UseShellExecute = false, CreateNoWindow = true
            };
            using var proc = Process.Start(psi)!;
            var output = proc.StandardError.ReadToEnd();
            proc.WaitForExit(5000);
            var v = JavaRuntime.ParseVersionOutput(output);
            return v is null ? null : new JavaRuntime(exe, v.Value.version, v.Value.major, "Unknown", source);
        }
        catch (Exception e)
        {
            Log.Debug($"Could not inspect {exe}: {e.Message}");
            return null;
        }
    }
}
