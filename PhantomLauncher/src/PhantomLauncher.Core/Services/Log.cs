using System.Collections.Concurrent;
using System.Text;

namespace PhantomLauncher.Core.Services;

public enum LogLevel { Debug, Info, Warn, Error }

public sealed record LogEntry(DateTime Time, LogLevel Level, string Source, string Message)
{
    public override string ToString() => $"[{Time:HH:mm:ss}] [{Source}/{Level.ToString().ToUpperInvariant()}] {Message}";
}

/// <summary>
/// Thread-safe launcher log: daily rolling file under %LOCALAPPDATA%\PhantomClient\logs plus an
/// in-memory event stream the console view subscribes to.
/// </summary>
public static class Log
{
    private static readonly object FileLock = new();
    private static string? _directory;
    private static readonly ConcurrentQueue<LogEntry> Recent = new();
    private const int MaxRecent = 2000;

    public static event Action<LogEntry>? EntryAdded;

    public static string? Directory => _directory;

    public static void Initialize(string directory)
    {
        _directory = directory;
        System.IO.Directory.CreateDirectory(directory);
        // Keep the 14 newest log files.
        foreach (var old in new DirectoryInfo(directory).GetFiles("launcher-*.log")
                     .OrderByDescending(f => f.Name).Skip(14))
        {
            try { old.Delete(); } catch { /* in use */ }
        }
    }

    public static string? CurrentFile =>
        _directory is null ? null : Path.Combine(_directory, $"launcher-{DateTime.Now:yyyyMMdd}.log");

    public static IReadOnlyList<LogEntry> Snapshot() => Recent.ToArray();

    public static void Debug(string m, string source = "Launcher") => Write(LogLevel.Debug, source, m);
    public static void Info(string m, string source = "Launcher") => Write(LogLevel.Info, source, m);
    public static void Warn(string m, string source = "Launcher") => Write(LogLevel.Warn, source, m);

    public static void Error(string m, Exception? ex = null, string source = "Launcher") =>
        Write(LogLevel.Error, source, ex is null ? m : $"{m}: {ex.GetType().Name}: {ex.Message}{Environment.NewLine}{ex.StackTrace}");

    public static void Write(LogLevel level, string source, string message)
    {
        var entry = new LogEntry(DateTime.Now, level, source, message);
        Recent.Enqueue(entry);
        while (Recent.Count > MaxRecent && Recent.TryDequeue(out _)) { }

        var file = CurrentFile;
        if (file is not null && source != "Game")
        {
            lock (FileLock)
            {
                try { File.AppendAllText(file, entry + Environment.NewLine, Encoding.UTF8); }
                catch { /* never fail because of logging */ }
            }
        }
        EntryAdded?.Invoke(entry);
    }
}
