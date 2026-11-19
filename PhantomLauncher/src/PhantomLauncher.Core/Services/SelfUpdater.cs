using System.Net.Http.Headers;
using System.Security.Cryptography;

namespace PhantomLauncher.Core.Services;

/// <summary>
/// In-place self-update for PhantomClient.exe.
/// <list type="number">
/// <item>Download the new exe from the GitHub release next to the running one (same volume).</item>
/// <item>Verify its SHA-256 against the release's SHA256SUMS.txt; anything else is discarded.</item>
/// <item>Swap: rename the running exe to <c>.old</c> (Windows allows renaming a running image),
/// move the new file into place, restart. The <c>.old</c> file is deleted on the next start.</item>
/// </list>
/// The Phantom mod is embedded in the exe, so the next Launch installs the matching mod as well.
/// </summary>
public sealed class SelfUpdater
{
    public const string ExeName = "PhantomClient.exe";
    public const string ChecksumsName = "SHA256SUMS.txt";
    private readonly HttpClient _http;

    public SelfUpdater(HttpClient http) => _http = http;

    public static string OldPath(string exe) => exe + ".old";
    public static string PendingPath(string exe) => exe + ".update";

    /// <summary>Release assets must come over HTTPS from GitHub.</summary>
    public static bool IsTrustedDownloadUrl(string? url) =>
        Uri.TryCreate(url, UriKind.Absolute, out var u) && u.Scheme == Uri.UriSchemeHttps &&
        (u.Host == "github.com" || u.Host.EndsWith(".githubusercontent.com", StringComparison.OrdinalIgnoreCase));

    /// <summary>Finds the hash for <paramref name="fileName"/> in sha256sum output ("hash  name" or "hash *name").</summary>
    public static string? ExpectedHash(string checksums, string fileName)
    {
        foreach (var raw in checksums.Split('\n'))
        {
            var line = raw.Trim();
            var space = line.IndexOf(' ');
            if (space != 64) continue;
            var name = line[(space + 1)..].TrimStart(' ', '*');
            if (string.Equals(name, fileName, StringComparison.OrdinalIgnoreCase)) return line[..64].ToLowerInvariant();
        }
        return null;
    }

    public static string Sha256(string path)
    {
        using var fs = File.OpenRead(path);
        return Convert.ToHexString(SHA256.HashData(fs)).ToLowerInvariant();
    }

    public static bool CanWriteTo(string directory)
    {
        try
        {
            var probe = Path.Combine(directory, $".phantom-write-test-{Guid.NewGuid():N}");
            File.WriteAllText(probe, "");
            File.Delete(probe);
            return true;
        }
        catch { return false; }
    }

    /// <summary>Downloads and verifies the update. Returns the path of the verified file (<see cref="PendingPath"/>).</summary>
    public async Task<string> DownloadAsync(UpdateInfo info, string currentExe, IProgress<double>? progress, CancellationToken ct)
    {
        if (!info.CanSelfUpdate) throw new InvalidOperationException("This release has no PhantomClient.exe and checksum file attached");
        var dir = Path.GetDirectoryName(currentExe)!;
        if (!CanWriteTo(dir)) throw new UnauthorizedAccessException($"Cannot write to {dir}. Move PhantomClient.exe to a folder you own (e.g. Documents) to enable updates.");

        var sums = await GetStringAsync(info.ChecksumsUrl!, ct).ConfigureAwait(false);
        var expected = ExpectedHash(sums, ExeName) ?? throw new InvalidDataException("SHA256SUMS.txt has no entry for " + ExeName);

        var pending = PendingPath(currentExe);
        var partial = pending + ".part";
        try
        {
            using (var req = Request(info.ExeUrl!))
            using (var res = await _http.SendAsync(req, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false))
            {
                res.EnsureSuccessStatusCode();
                var total = res.Content.Headers.ContentLength;
                await using var src = await res.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
                await using var dst = File.Create(partial);
                var buffer = new byte[81920];
                long done = 0;
                int n;
                while ((n = await src.ReadAsync(buffer, ct).ConfigureAwait(false)) > 0)
                {
                    await dst.WriteAsync(buffer.AsMemory(0, n), ct).ConfigureAwait(false);
                    done += n;
                    if (total > 0) progress?.Report((double)done / total.Value);
                }
            }
            var actual = Sha256(partial);
            if (actual != expected) throw new InvalidDataException($"Checksum mismatch (expected {expected[..12]}..., got {actual[..12]}...). The download was discarded.");
            File.Move(partial, pending, true);
            progress?.Report(1);
            Log.Info($"Update {info.Version} downloaded and verified ({expected[..12]}...)");
            return pending;
        }
        finally
        {
            if (File.Exists(partial)) File.Delete(partial);
        }
    }

    /// <summary>Swaps the verified download into place. Rolls back if anything fails.</summary>
    public static void Apply(string currentExe, string verifiedDownload)
    {
        var old = OldPath(currentExe);
        if (File.Exists(old)) File.Delete(old);
        File.Move(currentExe, old);
        try
        {
            File.Move(verifiedDownload, currentExe);
        }
        catch
        {
            File.Move(old, currentExe);
            throw;
        }
        Log.Info("Update applied; previous version kept as " + Path.GetFileName(old) + " until next start");
    }

    /// <summary>Called at startup: removes the previous version and stale partial downloads.</summary>
    public static void Cleanup(string currentExe)
    {
        foreach (var f in new[] { OldPath(currentExe), PendingPath(currentExe) + ".part" })
        {
            try { if (File.Exists(f)) File.Delete(f); } catch { /* still locked: next start */ }
        }
    }

    private async Task<string> GetStringAsync(string url, CancellationToken ct)
    {
        using var req = Request(url);
        using var res = await _http.SendAsync(req, ct).ConfigureAwait(false);
        res.EnsureSuccessStatusCode();
        return await res.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
    }

    private static HttpRequestMessage Request(string url)
    {
        var req = new HttpRequestMessage(HttpMethod.Get, url);
        req.Headers.UserAgent.Add(new ProductInfoHeaderValue("PhantomClient-Launcher", "1"));
        req.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/octet-stream"));
        return req;
    }
}
