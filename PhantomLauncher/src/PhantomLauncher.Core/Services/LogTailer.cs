using System.Text;

namespace PhantomLauncher.Core.Services;

/// <summary>
/// Follows a growing log file (the game's logs/latest.log) without locking it. Handles the file
/// being truncated or recreated when Minecraft restarts. Polls at a low rate (minimal background work).
/// </summary>
public sealed class LogTailer : IDisposable
{
    private readonly string _path;
    private readonly Action<string> _onLine;
    private readonly Timer _timer;
    private long _position;
    private string _partial = "";
    private byte[] _head = Array.Empty<byte>();
    private int _busy;

    public LogTailer(string path, Action<string> onLine, bool fromEnd, TimeSpan interval)
    {
        _path = path;
        _onLine = onLine;
        if (fromEnd && File.Exists(path))
        {
            _position = new FileInfo(path).Length;
            _head = ReadHead(path);
        }
        _timer = new Timer(_ => Poll(), null, interval, interval);
    }

    public void Poll()
    {
        if (Interlocked.Exchange(ref _busy, 1) == 1) return;
        try
        {
            if (!File.Exists(_path)) return;
            var info = new FileInfo(_path);
            // A new game session rewrites latest.log: detect it by shrinkage or a different first line.
            // (File timestamps are not reliable for this across platforms.)
            var head = ReadHead(_path);
            if (info.Length < _position || !head.AsSpan().StartsWith(_head.AsSpan(0, Math.Min(_head.Length, head.Length))))
            {
                _position = 0;
                _partial = "";
            }
            _head = head;
            if (info.Length == _position) return;
            using var fs = new FileStream(_path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            fs.Seek(_position, SeekOrigin.Begin);
            using var reader = new StreamReader(fs, Encoding.UTF8);
            var text = _partial + reader.ReadToEnd();
            _position = fs.Position;
            var lines = text.Split('\n');
            _partial = lines[^1];
            for (var i = 0; i < lines.Length - 1; i++) _onLine(lines[i].TrimEnd('\r'));
        }
        catch (IOException)
        {
            // file briefly unavailable, try next tick
        }
        finally
        {
            Interlocked.Exchange(ref _busy, 0);
        }
    }

    private static byte[] ReadHead(string path)
    {
        try
        {
            using var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            var buf = new byte[Math.Min(64, fs.Length)];
            fs.ReadExactly(buf);
            return buf;
        }
        catch (IOException)
        {
            return Array.Empty<byte>();
        }
    }

    public void Dispose() => _timer.Dispose();
}
