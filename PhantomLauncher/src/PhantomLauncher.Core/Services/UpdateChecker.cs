using System.Net.Http.Headers;
using System.Text.Json.Nodes;

namespace PhantomLauncher.Core.Services;

/// <param name="ExeUrl">Download URL of the PhantomClient.exe release asset (null if the release has none).</param>
/// <param name="ChecksumsUrl">Download URL of SHA256SUMS.txt (required for self-update).</param>
public sealed record UpdateInfo(string Version, string Url, string Notes, string? ExeUrl = null, string? ChecksumsUrl = null)
{
    public bool CanSelfUpdate => ExeUrl is not null && ChecksumsUrl is not null;
}

/// <summary>Queries a GitHub-releases-compatible endpoint ("tag_name", "html_url", "body").</summary>
public sealed class UpdateChecker
{
    private readonly HttpClient _http;

    public UpdateChecker(HttpClient http) => _http = http;

    public static bool IsConfigured(string? url) =>
        !string.IsNullOrWhiteSpace(url) && Uri.TryCreate(url, UriKind.Absolute, out var u)
        && u.Scheme == Uri.UriSchemeHttps && !url.Contains("/OWNER/", StringComparison.Ordinal);

    public async Task<UpdateInfo?> CheckAsync(string url, string currentVersion, CancellationToken ct)
    {
        using var req = new HttpRequestMessage(HttpMethod.Get, url);
        req.Headers.UserAgent.Add(new ProductInfoHeaderValue("PhantomClient-Launcher", currentVersion));
        req.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
        using var res = await _http.SendAsync(req, ct).ConfigureAwait(false);
        res.EnsureSuccessStatusCode();
        var json = JsonNode.Parse(await res.Content.ReadAsStringAsync(ct).ConfigureAwait(false));
        return Evaluate(json, currentVersion);
    }

    public static UpdateInfo? Evaluate(JsonNode? release, string currentVersion)
    {
        var tag = release?["tag_name"]?.GetValue<string>();
        if (string.IsNullOrWhiteSpace(tag)) return null;
        if (release?["draft"]?.GetValue<bool>() == true || release?["prerelease"]?.GetValue<bool>() == true) return null;
        if (SemVer.Parse(tag).CompareTo(SemVer.Parse(currentVersion)) <= 0) return null;
        string? Asset(string name)
        {
            if (release?["assets"] is not JsonArray assets) return null;
            foreach (var a in assets)
            {
                if (!string.Equals(a?["name"]?.GetValue<string>(), name, StringComparison.OrdinalIgnoreCase)) continue;
                var url = a?["browser_download_url"]?.GetValue<string>();
                return SelfUpdater.IsTrustedDownloadUrl(url) ? url : null;
            }
            return null;
        }

        return new UpdateInfo(SemVer.Parse(tag).ToString(), release?["html_url"]?.GetValue<string>() ?? "",
            release?["body"]?.GetValue<string>() ?? "", Asset(SelfUpdater.ExeName), Asset(SelfUpdater.ChecksumsName));
    }
}
