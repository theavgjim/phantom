using System.Threading;
using System.Windows;
using System.Windows.Threading;
using PhantomLauncher.Core.Services;

namespace PhantomLauncher;

public partial class App : Application
{
    private Mutex? _single;

    protected override void OnStartup(StartupEventArgs e)
    {
        var dataDir = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhantomClient");
        Log.Initialize(System.IO.Path.Combine(dataDir, "logs"));

        // One launcher at a time.
        _single = new Mutex(true, @"Local\PhantomClientLauncher", out var first);
        if (!first)
        {
            MessageBox.Show("Phantom Client is already running.", "Phantom Client", MessageBoxButton.OK, MessageBoxImage.Information);
            Shutdown();
            return;
        }

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
        _single?.ReleaseMutex();
        _single?.Dispose();
        base.OnExit(e);
    }
}
