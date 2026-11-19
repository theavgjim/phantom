using System.Management;
using PhantomLauncher.Core.Models;
using PhantomLauncher.Core.Services;

namespace PhantomLauncher.Services;

/// <summary>Detects running Minecraft Java clients by inspecting java/javaw command lines via WMI.</summary>
public static class ProcessDetector
{
    public static IReadOnlyList<MinecraftProcess> Find()
    {
        var list = new List<MinecraftProcess>();
        try
        {
            using var searcher = new ManagementObjectSearcher(
                "SELECT ProcessId, CommandLine FROM Win32_Process WHERE Name = 'javaw.exe' OR Name = 'java.exe'");
            foreach (ManagementObject mo in searcher.Get())
            {
                using (mo)
                {
                    var pid = Convert.ToInt32(mo["ProcessId"]);
                    var p = MinecraftProcess.FromCommandLine(pid, mo["CommandLine"] as string);
                    if (p is not null) list.Add(p);
                }
            }
        }
        catch (Exception e)
        {
            Log.Debug($"Process scan failed: {e.Message}");
        }
        return list;
    }
}
