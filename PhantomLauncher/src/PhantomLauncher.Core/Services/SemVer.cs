namespace PhantomLauncher.Core.Services;

/// <summary>Lenient dotted version compare, mirrors dev.phantom.core.util.Version on the Java side.</summary>
public readonly record struct SemVer(int[] Parts, string Qualifier) : IComparable<SemVer>
{
    public static SemVer Parse(string text)
    {
        var s = text.Trim().TrimStart('v', 'V');
        var qualifier = "";
        var cut = s.IndexOfAny(new[] { '-', '+' });
        if (cut >= 0) { qualifier = s[(cut + 1)..]; s = s[..cut]; }
        var parts = s.Split('.').Select(p => int.TryParse(new string(p.Where(char.IsDigit).ToArray()), out var n) ? n : 0).ToArray();
        return new SemVer(parts, qualifier);
    }

    public int CompareTo(SemVer other)
    {
        var n = Math.Max(Parts.Length, other.Parts.Length);
        for (var i = 0; i < n; i++)
        {
            var a = i < Parts.Length ? Parts[i] : 0;
            var b = i < other.Parts.Length ? other.Parts[i] : 0;
            if (a != b) return a.CompareTo(b);
        }
        if (Qualifier.Length == 0 != (other.Qualifier.Length == 0)) return Qualifier.Length == 0 ? 1 : -1;
        return string.CompareOrdinal(Qualifier, other.Qualifier);
    }

    public bool AtLeast(SemVer other) => CompareTo(other) >= 0;

    public override string ToString() => string.Join('.', Parts) + (Qualifier.Length > 0 ? "-" + Qualifier : "");
}
