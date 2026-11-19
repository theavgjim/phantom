using System.Net.Http.Headers;
using System.Text.Json.Nodes;

namespace PhantomLauncher.Core.Services;

public sealed record UpdateInfo(string Version, string Url, string Notes);

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
        return new UpdateInfo(SemVer.Parse(tag).ToString(), release?["html_url"]?.GetValue<string>() ?? "",
            release?["body"]?.GetValue<string>() ?? "");
    }
}
