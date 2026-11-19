using System.IO;
using System.Threading;
using PhantomLauncher.Core.Services;
using System.Windows;
using System.Windows.Threading;

namespace PhantomLauncher;

public partial class App : Application
{
    private Mutex? _single;
    private bool _ownsMutex;

    /// <summary>Version we were updated from when started with <c>--updated &lt;version&gt;</c>.</summary>
    public static string? UpdatedFrom { get; private set; }

    protected override void OnStartup(StartupEventArgs e)
    {
        var dataDir = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhantomClient");
        Log.Initialize(System.IO.Path.Combine(dataDir, "logs"));

        // One launcher at a time.
        var idx = Array.IndexOf(e.Args, "--updated");
        if (idx >= 0) UpdatedFrom = idx + 1 < e.Args.Length ? e.Args[idx + 1] : "?";

        _single = new Mutex(true, @"Local\PhantomClientLauncher", out var first);
        _ownsMutex = first;
        if (!first && UpdatedFrom is not null)
        {
            // Restarted by the updater: wait for the previous version to finish exiting.
            try { _ownsMutex = _single.WaitOne(TimeSpan.FromSeconds(20)); }
            catch (AbandonedMutexException) { _ownsMutex = true; }
        }
        if (!_ownsMutex)
        {
            MessageBox.Show("Phantom Client is already running.", "Phantom Client", MessageBoxButton.OK, MessageBoxImage.Information);
            Shutdown();
            return;
        }

        if (Environment.ProcessPath is { } exe) SelfUpdater.Cleanup(exe);
        DispatcherUnhandledException += OnDispatcherException;
        AppDomain.CurrentDomain.UnhandledException += (_, ev) =>
            Log.Error("Fatal unhandled exception", ev.ExceptionObject as Exception);
        TaskScheduler.UnobservedTaskException += (_, ev) =>
        {
            Log.Error("Unobserved task exception", ev.Exception);
            ev.SetObserved();
        };
        base.OnStartup(e);
        MainWindow = new MainWindow();
        MainWindow.Show();
    }

    private static void OnDispatcherException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        Log.Error("Unhandled UI exception", e.Exception);
        MessageBox.Show($"Something went wrong:\n\n{e.Exception.Message}\n\nDetails were written to the launcher log.",
            "Phantom Client", MessageBoxButton.OK, MessageBoxImage.Error);
        e.Handled = true;
    }

    protected override void OnExit(ExitEventArgs e)
    {
        Log.Info("Launcher closed");
        if (_ownsMutex) _single?.ReleaseMutex();
        _single?.Dispose();
        base.OnExit(e);
    }
}
