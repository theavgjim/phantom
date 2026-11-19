using System.IO;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Net.Http;
using System.Windows;
using System.Windows.Data;
using System.Windows.Threading;
using Microsoft.Win32;
using PhantomLauncher.Core.Models;
using PhantomLauncher.Core.Services;
using PhantomLauncher.Services;

namespace PhantomLauncher.ViewModels;

public sealed class ProfileRow
{
    public required string Name { get; init; }
    public required string Details { get; init; }
    public bool IsActive { get; init; }
}

public sealed class MainViewModel : ObservableObject, IDisposable
{
    private const int MaxConsoleLines = 3000;
    private static readonly string DataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PhantomClient");
    private static readonly string SettingsPath = Path.Combine(DataDir, "settings.json");

    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(20) };
    private readonly DispatcherTimer _poll;
    private readonly Dispatcher _ui;
    private LogTailer? _tailer;
    private CancellationTokenSource? _launchCts;

    public AppSettings Settings { get; }
    public CompatibilityTable Compat { get; }
    /// <summary>The exe's own version (set from the git tag by the release build).</summary>
    public string ClientVersion { get; } = ReadVersion();

    private static string ReadVersion()
    {
        var v = System.Reflection.Assembly.GetExecutingAssembly()
            .GetCustomAttributes(typeof(System.Reflection.AssemblyInformationalVersionAttribute), false)
            .OfType<System.Reflection.AssemblyInformationalVersionAttribute>().FirstOrDefault()?.InformationalVersion ?? "0.0.0";
        var plus = v.IndexOf('+');
        return plus >= 0 ? v[..plus] : v;
    }
    public string Title => $"Phantom Client {ClientVersion}";

    public MainViewModel()
    {
        _ui = Application.Current.Dispatcher;
        Settings = AppSettings.Load(SettingsPath);
        using (var s = LaunchService.OpenEmbedded("compatibility.json")!)
        using (var r = new StreamReader(s)) Compat = CompatibilityTable.Parse(r.ReadToEnd());

        ConsoleView = CollectionViewSource.GetDefaultView(ConsoleLines);
        ConsoleView.Filter = o => o is LogEntry e && PassesFilter(e);
        _consoleFilter = Settings.ConsoleFilter;
        foreach (var e in Log.Snapshot()) ConsoleLines.Add(e);
        Log.EntryAdded += OnLog;

        NavigateCommand = new RelayCommand(p => Page = p as string ?? "Home");
        LaunchCommand = new RelayCommand(async () => await LaunchAsync(), () => !IsBusy);
        CancelCommand = new RelayCommand(() => _launchCts?.Cancel(), () => IsBusy);
        RefreshCommand = new RelayCommand(async () => await DetectAsync());
        BrowseMinecraftDirCommand = new RelayCommand(BrowseMinecraftDir);
        BrowseJavaCommand = new RelayCommand(BrowseJava);
        BrowseLauncherCommand = new RelayCommand(BrowseLauncher);
        ResetSettingsCommand = new RelayCommand(ResetSettings);
        OpenFolderCommand = new RelayCommand(p => OpenFolder(p as string));
        OpenUrlCommand = new RelayCommand(p => OpenUrl(p as string));
        ClearConsoleCommand = new RelayCommand(() => ConsoleLines.Clear());
        CopyConsoleCommand = new RelayCommand(CopyConsole);
        CheckUpdatesCommand = new RelayCommand(async () => await CheckUpdatesAsync(true));
        InstallUpdateCommand = new RelayCommand(async () => await InstallUpdateAsync(), () => !IsUpdating && _update is not null);

        SetActiveProfileCommand = new RelayCommand(p => SelectProfile(p as string));
        DuplicateProfileCommand = new RelayCommand(p => ProfileAction(() =>
        {
            var name = NewProfileName.Trim();
            Profiles.Duplicate(p as string ?? SelectedProfile, name);
            NewProfileName = "";
            return $"Created '{name}'";
        }));
        DeleteProfileCommand = new RelayCommand(p => ProfileAction(() =>
        {
            var name = p as string ?? "";
            if (MessageBox.Show($"Delete profile '{name}'? A backup is kept in the backups folder.", "Phantom Client",
                    MessageBoxButton.YesNo, MessageBoxImage.Question) != MessageBoxResult.Yes) return null;
            Profiles.Delete(name, Profiles.ActiveProfile() ?? SelectedProfile);
            return $"Deleted '{name}'";
        }));
        ImportProfileCommand = new RelayCommand(() => ProfileAction(() =>
        {
            var dlg = new OpenFileDialog { Filter = "Phantom profile (*.json)|*.json", Title = "Import profile" };
            if (dlg.ShowDialog() != true) return null;
            return $"Imported as '{Profiles.Import(dlg.FileName)}'";
        }));
        ExportProfileCommand = new RelayCommand(p => ProfileAction(() =>
        {
            var name = p as string ?? SelectedProfile;
            var dlg = new SaveFileDialog { Filter = "Phantom profile (*.json)|*.json", FileName = $"{name}_{DateTime.Now:yyyyMMdd-HHmmss}.json" };
            if (dlg.ShowDialog() != true) return null;
            Profiles.Export(name, dlg.FileName);
            return $"Exported '{name}'";
        }));

        _poll = new DispatcherTimer(DispatcherPriority.Background) { Interval = TimeSpan.FromSeconds(4) };
        _poll.Tick += async (_, _) => await PollAsync();
    }

    public async Task InitializeAsync()
    {
        Log.Info($"Phantom Client launcher {ClientVersion} on {Environment.OSVersion}");
        if (App.UpdatedFrom is { } from)
        {
            Status = $"Updated from {from} to {ClientVersion}";
            Log.Info(Status);
        }
        await DetectAsync();
        _poll.Start();
        if (Settings.CheckUpdates) _ = CheckUpdatesAsync(false);
    }

    // ------------------------------------------------------------------ navigation / status

    private string _page = "Home";
    public string Page { get => _page; set => Set(ref _page, value); }

    private bool _isBusy;
    public bool IsBusy { get => _isBusy; set { if (Set(ref _isBusy, value)) Raise(nameof(LaunchLabel)); } }

    private string _status = "Ready";
    public string Status { get => _status; set => Set(ref _status, value); }

    public string LaunchLabel => IsBusy ? "PREPARING..." : "LAUNCH";

    // ------------------------------------------------------------------ detection

    public MinecraftInstallation Minecraft => new(string.IsNullOrWhiteSpace(Settings.MinecraftDirectory)
        ? MinecraftInstallation.DefaultRoot(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData))
        : Settings.MinecraftDirectory);

    public string InstanceDir => string.IsNullOrWhiteSpace(Settings.InstanceDirectory)
        ? Path.Combine(Minecraft.Root, "phantom-instance")
        : Settings.InstanceDirectory;

    public PhantomProfiles Profiles => new(Path.Combine(InstanceDir, "config", "phantom"));

    private string _minecraftText = "Scanning...";
    public string MinecraftText { get => _minecraftText; set => Set(ref _minecraftText, value); }

    private string _versionsText = "";
    public string VersionsText { get => _versionsText; set => Set(ref _versionsText, value); }

    private string _compatText = "";
    public string CompatText { get => _compatText; set => Set(ref _compatText, value); }

    private CompatState _compatState = CompatState.Warning;
    public CompatState CompatState { get => _compatState; set => Set(ref _compatState, value); }

    private string _launcherText = "";
    public string LauncherText { get => _launcherText; set => Set(ref _launcherText, value); }

    private string _processText = "Not running";
    public string ProcessText { get => _processText; set => Set(ref _processText, value); }

    private bool _gameRunning;
    public bool GameRunning { get => _gameRunning; set => Set(ref _gameRunning, value); }

    private string _clientText = "Waiting for the client";
    public string ClientText { get => _clientText; set => Set(ref _clientText, value); }

    private bool _clientConnected;
    public bool ClientConnected { get => _clientConnected; set => Set(ref _clientConnected, value); }

    public ObservableCollection<JavaRuntime> JavaRuntimes { get; } = new();

    private JavaRuntime? _selectedJava;
    public JavaRuntime? SelectedJava
    {
        get => _selectedJava;
        set
        {
            if (!Set(ref _selectedJava, value)) return;
            Settings.JavaPath = value?.Executable;
            SaveSettings();
            Raise(nameof(JavaText));
            UpdateCompat();
        }
    }

    public string JavaText => SelectedJava is null
        ? "Using the Minecraft Launcher's bundled Java (recommended)"
        : SelectedJava.Display;

    public async Task DetectAsync()
    {
        Status = "Detecting installation...";
        var mc = Minecraft;
        var result = await Task.Run(() =>
        {
            var versions = mc.Exists ? mc.Versions() : Array.Empty<MinecraftInstallation.InstalledVersion>();
            var java = JavaLocator.Find(mc);
            var launcher = MinecraftLauncherLocator.Find(Settings.MinecraftLauncherPath);
            return (versions, java, launcher);
        });

        MinecraftText = mc.Exists ? mc.Root : $"Not found ({mc.Root})";
        var target = Compat.Primary;
        var vanilla = result.versions.Where(v => !v.IsFabric && v.InheritsFrom is null).Select(v => v.Id).Take(6).ToList();
        var fabricReady = result.versions.Any(v => v.Id == ClientInstaller.FabricVersionId(target));
        VersionsText = vanilla.Count == 0 ? "No versions downloaded yet" : "Installed: " + string.Join(", ", vanilla)
            + (fabricReady ? $"  ·  Fabric {target.FabricLoader} ready" : "");
        _installedSupported = result.versions.Any(v => v.Id == target.Minecraft) ? target.Minecraft : null;
        LauncherText = result.launcher?.Description ?? "Minecraft Launcher not found";

        JavaRuntimes.Clear();
        foreach (var j in result.java) JavaRuntimes.Add(j);
        _selectedJava = JavaRuntimes.FirstOrDefault(j => string.Equals(j.Executable, Settings.JavaPath, StringComparison.OrdinalIgnoreCase));
        Raise(nameof(SelectedJava));
        Raise(nameof(JavaText));
        Log.Info($"Minecraft: {MinecraftText}; {result.versions.Count} versions; {result.java.Count} Java runtimes; launcher: {LauncherText}");
        UpdateCompat();
        RefreshProfiles();
        StartTailing();
        await PollAsync();
        Status = "Ready";
    }

    private string? _installedSupported;

    private void UpdateCompat()
    {
        var r = Compat.Check(_installedSupported, SelectedJava?.Major ?? 0);
        CompatText = r.Message;
        CompatState = r.State;
    }

    private async Task PollAsync()
    {
        var statusFile = Profiles.StatusFile;
        var (procs, status) = await Task.Run(() => (ProcessDetector.Find(), ClientStatus.TryRead(statusFile)));
        GameRunning = procs.Count > 0;
        ProcessText = procs.Count == 0
            ? "Not running"
            : string.Join("\n", procs.Select(p => $"PID {p.Pid} · {p.Version ?? "unknown version"}{(p.IsPhantom ? " · Phantom" : p.IsFabric ? " · Fabric" : "")}"));
        var live = status?.IsLive(DateTimeOffset.UtcNow) == true;
        ClientConnected = live;
        ClientText = live
            ? $"Connected · profile '{status!.Profile}' · {status.ContextDisplay} · {status.Fps} FPS · {status.EnabledModules.Count} modules on"
            : status is { Running: false } ? "Client closed normally" : "Waiting for the client";
    }

    // ------------------------------------------------------------------ launch

    private async Task LaunchAsync()
    {
        if (CompatState == CompatState.Unsupported)
        {
            Log.Warn(CompatText);
        }
        IsBusy = true;
        _launchCts = new CancellationTokenSource();
        var progress = new Progress<string>(s => { Status = s; Log.Info(s); });
        try
        {
            await new LaunchService(_http).RunAsync(new LaunchService.Request(Minecraft, InstanceDir, Compat.Primary,
                SelectedJava, Settings, SelectedProfile, ClientVersion), progress, _launchCts.Token);
            StartTailing();
            if (Settings.CloseOnLaunch) Application.Current.Shutdown();
        }
        catch (OperationCanceledException)
        {
            Status = "Cancelled";
            Log.Warn("Launch cancelled");
        }
        catch (Exception e)
        {
            Status = "Launch failed: " + e.Message;
            Log.Error("Launch failed", e);
            MessageBox.Show(e.Message, "Phantom Client", MessageBoxButton.OK, MessageBoxImage.Error);
        }
        finally
        {
            IsBusy = false;
        }
    }

    // ------------------------------------------------------------------ profiles

    public ObservableCollection<ProfileRow> ProfileRows { get; } = new();
    public ObservableCollection<string> ProfileNames { get; } = new();

    public string SelectedProfile
    {
        get => Settings.SelectedProfile;
        set
        {
            if (value is null || value == Settings.SelectedProfile) return;
            Settings.SelectedProfile = value;
            SaveSettings();
            Raise();
            RefreshProfiles();
        }
    }

    private string _newProfileName = "";
    public string NewProfileName { get => _newProfileName; set => Set(ref _newProfileName, value); }

    private string _profileMessage = "";
    public string ProfileMessage { get => _profileMessage; set => Set(ref _profileMessage, value); }

    private void SelectProfile(string? name)
    {
        if (name is null) return;
        SelectedProfile = name;
        ProfileMessage = $"'{name}' will be loaded the next time you launch";
    }

    private void ProfileAction(Func<string?> action)
    {
        try
        {
            var msg = action();
            if (msg is not null)
            {
                ProfileMessage = msg;
                Log.Info(msg);
            }
        }
        catch (Exception e)
        {
            ProfileMessage = e.Message;
            Log.Warn($"Profile action failed: {e.Message}");
        }
        RefreshProfiles();
    }

    private void RefreshProfiles()
    {
        var store = Profiles;
        var names = store.List().ToList();
        if (!names.Contains(SelectedProfile)) names.Add(SelectedProfile);
        var clientActive = store.ActiveProfile();
        ProfileRows.Clear();
        foreach (var n in names.OrderBy(n => n, StringComparer.OrdinalIgnoreCase))
        {
            var info = store.Describe(n);
            var details = info.Modified is null ? "Not saved yet · created on first launch"
                : $"{info.EnabledModules} modules enabled · saved {info.Modified:g}";
            if (n == clientActive) details += " · last used in game";
            ProfileRows.Add(new ProfileRow { Name = n, Details = details, IsActive = n == SelectedProfile });
        }
        ProfileNames.Clear();
        foreach (var r in ProfileRows) ProfileNames.Add(r.Name);
        Raise(nameof(SelectedProfile));
    }

    // ------------------------------------------------------------------ settings

    public int MemoryMb
    {
        get => Settings.MemoryMb;
        set { Settings.MemoryMb = value; SaveSettings(); Raise(); Raise(nameof(MemoryText)); }
    }

    public string MemoryText => $"{Settings.MemoryMb / 1024.0:0.#} GB";

    public int MaxMemoryMb => (int)Math.Clamp(GC.GetGCMemoryInfo().TotalAvailableMemoryBytes / (1024 * 1024) * 3 / 4, 2048, 32768);

    public string ExtraJvmArgs { get => Settings.ExtraJvmArgs; set { Settings.ExtraJvmArgs = value; SaveSettings(); Raise(); } }
    public string MinecraftDirectory
    {
        get => Settings.MinecraftDirectory ?? Minecraft.Root;
        set { Settings.MinecraftDirectory = string.IsNullOrWhiteSpace(value) ? null : value; SaveSettings(); Raise(); _ = DetectAsync(); }
    }
    public string InstanceDirectory
    {
        get => InstanceDir;
        set { Settings.InstanceDirectory = string.IsNullOrWhiteSpace(value) ? null : value; SaveSettings(); Raise(); RefreshProfiles(); StartTailing(); }
    }
    public string LauncherPath { get => Settings.MinecraftLauncherPath ?? ""; set { Settings.MinecraftLauncherPath = string.IsNullOrWhiteSpace(value) ? null : value; SaveSettings(); Raise(); } }
    public bool CloseOnLaunch { get => Settings.CloseOnLaunch; set { Settings.CloseOnLaunch = value; SaveSettings(); Raise(); } }
    public bool CheckUpdates { get => Settings.CheckUpdates; set { Settings.CheckUpdates = value; SaveSettings(); Raise(); } }
    public string UpdateUrl { get => Settings.UpdateUrl ?? Compat.UpdateUrl; set { Settings.UpdateUrl = value; SaveSettings(); Raise(); } }

    private void SaveSettings()
    {
        try { Settings.Save(SettingsPath); }
        catch (Exception e) { Log.Error("Could not save settings", e); }
    }

    private void BrowseMinecraftDir()
    {
        var dlg = new OpenFolderDialog { Title = "Select your .minecraft folder", InitialDirectory = Minecraft.Root };
        if (dlg.ShowDialog() == true) MinecraftDirectory = dlg.FolderName;
    }

    private void BrowseJava()
    {
        var dlg = new OpenFileDialog { Filter = "Java (javaw.exe;java.exe)|javaw.exe;java.exe", Title = "Select a Java 21+ runtime" };
        if (dlg.ShowDialog() != true) return;
        var rt = JavaLocator.Describe(dlg.FileName, "Custom");
        if (rt is null) { Log.Warn("Selected file is not a usable Java runtime"); return; }
        JavaRuntimes.Insert(0, rt);
        SelectedJava = rt;
    }

    private void BrowseLauncher()
    {
        var dlg = new OpenFileDialog { Filter = "Minecraft Launcher|*.exe", Title = "Select the Minecraft Launcher" };
        if (dlg.ShowDialog() == true) { LauncherPath = dlg.FileName; _ = DetectAsync(); }
    }

    private void ResetSettings()
    {
        Settings.MinecraftDirectory = null;
        Settings.InstanceDirectory = null;
        Settings.JavaPath = null;
        Settings.MinecraftLauncherPath = null;
        Settings.MemoryMb = 4096;
        Settings.ExtraJvmArgs = new AppSettings().ExtraJvmArgs;
        SaveSettings();
        foreach (var p in new[] { nameof(MemoryMb), nameof(MemoryText), nameof(ExtraJvmArgs), nameof(MinecraftDirectory), nameof(InstanceDirectory), nameof(LauncherPath) }) Raise(p);
        _ = DetectAsync();
    }

    // ------------------------------------------------------------------ console

    public ObservableCollection<LogEntry> ConsoleLines { get; } = new();
    public System.ComponentModel.ICollectionView ConsoleView { get; }
    public string[] ConsoleFilters { get; } = { "All", "Launcher", "Game", "Phantom", "Warnings & errors" };

    private string _consoleFilter;
    public string ConsoleFilter
    {
        get => _consoleFilter;
        set
        {
            if (!Set(ref _consoleFilter, value)) return;
            Settings.ConsoleFilter = value;
            SaveSettings();
            ConsoleView.Refresh();
        }
    }

    private bool PassesFilter(LogEntry e) => _consoleFilter switch
    {
        "Launcher" => e.Source == "Launcher",
        "Game" => e.Source == "Game",
        "Phantom" => e.Source == "Game" && e.Message.Contains("(Phantom", StringComparison.Ordinal),
        "Warnings & errors" => e.Level >= LogLevel.Warn,
        _ => true
    };

    private void OnLog(LogEntry e) => _ui.BeginInvoke(() =>
    {
        ConsoleLines.Add(e);
        while (ConsoleLines.Count > MaxConsoleLines) ConsoleLines.RemoveAt(0);
        if (e.Level == LogLevel.Error) ErrorCount++;
    });

    private int _errorCount;
    public int ErrorCount { get => _errorCount; set => Set(ref _errorCount, value); }

    private void StartTailing()
    {
        var path = Path.Combine(InstanceDir, "logs", "latest.log");
        _tailer?.Dispose();
        _tailer = new LogTailer(path, line =>
        {
            if (line.Length == 0) return;
            var level = line.Contains("/ERROR]") || line.Contains("Exception") ? LogLevel.Error
                : line.Contains("/WARN]") ? LogLevel.Warn : LogLevel.Info;
            Log.Write(level, "Game", line);
        }, fromEnd: true, TimeSpan.FromMilliseconds(750));
    }

    private void CopyConsole()
    {
        var text = string.Join(Environment.NewLine, ConsoleView.Cast<LogEntry>().Select(e => e.ToString()));
        try { Clipboard.SetText(text); Status = "Console copied to clipboard"; }
        catch (Exception e) { Log.Warn($"Clipboard unavailable: {e.Message}"); }
    }

    // ------------------------------------------------------------------ updates

    private UpdateInfo? _update;
    private string? _verifiedUpdate;

    private string? _updateText;
    public string? UpdateText { get => _updateText; set { Set(ref _updateText, value); Raise(nameof(UpdateAvailable)); } }
    public bool UpdateAvailable => UpdateText is not null;

    private string? _updateUrl;
    public string? UpdatePageUrl { get => _updateUrl; set => Set(ref _updateUrl, value); }

    private bool _isUpdating;
    public bool IsUpdating { get => _isUpdating; set { Set(ref _isUpdating, value); Raise(nameof(UpdateButtonText)); } }

    private double _updateProgress;
    public double UpdateProgress { get => _updateProgress; set => Set(ref _updateProgress, value); }

    public bool CanSelfUpdate => _update?.CanSelfUpdate == true;
    public string UpdateButtonText => IsUpdating ? "Downloading..." : _verifiedUpdate is not null ? "Restart to update" : "Update & restart";

    public bool AutoUpdate { get => Settings.AutoUpdate; set { Settings.AutoUpdate = value; SaveSettings(); Raise(); } }

    private static string? CurrentExe =>
        Environment.ProcessPath is { } p && p.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) ? p : null;

    private async Task CheckUpdatesAsync(bool manual)
    {
        var url = UpdateUrl;
        if (!UpdateChecker.IsConfigured(url))
        {
            if (manual) Status = "Update URL is not configured (Settings > Launcher)";
            Log.Info("Update check skipped: no release URL configured");
            return;
        }
        try
        {
            var info = await new UpdateChecker(_http).CheckAsync(url, ClientVersion, CancellationToken.None);
            if (info is null)
            {
                if (manual) Status = $"You are on the latest version ({ClientVersion})";
                Log.Info("No update available");
                return;
            }
            _update = info;
            UpdatePageUrl = info.Url;
            UpdateText = $"Phantom Client {info.Version} is available";
            Raise(nameof(CanSelfUpdate));
            Raise(nameof(UpdateButtonText));
            Log.Info(UpdateText);
            if (Settings.AutoUpdate && info.CanSelfUpdate && CurrentExe is not null) await DownloadUpdateAsync();
        }
        catch (Exception e)
        {
            Log.Warn($"Update check failed: {e.Message}");
            if (manual) Status = "Update check failed: " + e.Message;
        }
    }

    private async Task<bool> DownloadUpdateAsync()
    {
        if (_verifiedUpdate is not null) return true;
        if (_update is null || CurrentExe is not { } exe) return false;
        IsUpdating = true;
        try
        {
            var progress = new Progress<double>(p => UpdateProgress = p * 100);
            _verifiedUpdate = await new SelfUpdater(_http).DownloadAsync(_update, exe, progress, CancellationToken.None);
            UpdateText = $"Phantom Client {_update.Version} is ready - it installs when you restart or close the launcher";
            return true;
        }
        catch (Exception e)
        {
            Log.Error("Update download failed", e);
            UpdateText = $"Phantom Client {_update.Version} is available (automatic update failed: {e.Message})";
            return false;
        }
        finally
        {
            IsUpdating = false;
        }
    }

    private async Task InstallUpdateAsync()
    {
        if (_update is null) return;
        if (!_update.CanSelfUpdate || CurrentExe is null)
        {
            OpenUrl(_update.Url);
            return;
        }
        if (IsBusy)
        {
            Status = "Wait for the launch to finish before updating";
            return;
        }
        if (!await DownloadUpdateAsync() || _verifiedUpdate is null) return;
        try
        {
            var exe = CurrentExe!;
            SelfUpdater.Apply(exe, _verifiedUpdate);
            _verifiedUpdate = null;
            Process.Start(new ProcessStartInfo(exe, $"--updated {ClientVersion}") { UseShellExecute = false });
            Application.Current.Shutdown();
        }
        catch (Exception e)
        {
            Log.Error("Could not apply the update", e);
            MessageBox.Show("The update could not be installed:\n\n" + e.Message, "Phantom Client", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    /// <summary>A verified download that was not applied yet is installed when the launcher closes.</summary>
    private void ApplyPendingUpdateOnExit()
    {
        if (_verifiedUpdate is null || CurrentExe is not { } exe) return;
        try
        {
            SelfUpdater.Apply(exe, _verifiedUpdate);
            Log.Info($"Update {_update?.Version} installed on exit");
        }
        catch (Exception e)
        {
            Log.Error("Could not apply the update on exit", e);
        }
    }

    // ------------------------------------------------------------------ misc

    public System.Windows.Input.ICommand NavigateCommand { get; }
    public System.Windows.Input.ICommand LaunchCommand { get; }
    public System.Windows.Input.ICommand CancelCommand { get; }
    public System.Windows.Input.ICommand RefreshCommand { get; }
    public System.Windows.Input.ICommand BrowseMinecraftDirCommand { get; }
    public System.Windows.Input.ICommand BrowseJavaCommand { get; }
    public System.Windows.Input.ICommand BrowseLauncherCommand { get; }
    public System.Windows.Input.ICommand ResetSettingsCommand { get; }
    public System.Windows.Input.ICommand OpenFolderCommand { get; }
    public System.Windows.Input.ICommand OpenUrlCommand { get; }
    public System.Windows.Input.ICommand ClearConsoleCommand { get; }
    public System.Windows.Input.ICommand CopyConsoleCommand { get; }
    public System.Windows.Input.ICommand CheckUpdatesCommand { get; }
    public System.Windows.Input.ICommand InstallUpdateCommand { get; }
    public System.Windows.Input.ICommand SetActiveProfileCommand { get; }
    public System.Windows.Input.ICommand DuplicateProfileCommand { get; }
    public System.Windows.Input.ICommand DeleteProfileCommand { get; }
    public System.Windows.Input.ICommand ImportProfileCommand { get; }
    public System.Windows.Input.ICommand ExportProfileCommand { get; }


    private void OpenFolder(string? which)
    {
        var path = which switch
        {
            "instance" => InstanceDir,
            "config" => Profiles.Root,
            "backups" => Profiles.BackupsDir,
            "minecraft" => Minecraft.Root,
            "crash" => Path.Combine(InstanceDir, "crash-reports"),
            "logs" => Log.Directory ?? DataDir,
            _ => which
        };
        if (string.IsNullOrEmpty(path)) return;
        try
        {
            Directory.CreateDirectory(path);
            Process.Start(new ProcessStartInfo("explorer.exe", $"\"{path}\"") { UseShellExecute = true });
        }
        catch (Exception e) { Log.Warn($"Could not open {path}: {e.Message}"); }
    }

    private static void OpenUrl(string? url)
    {
        if (string.IsNullOrWhiteSpace(url) || !url.StartsWith("https://", StringComparison.Ordinal)) return;
        Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
    }

    public void Dispose()
    {
        ApplyPendingUpdateOnExit();
        _poll.Stop();
        _tailer?.Dispose();
        Log.EntryAdded -= OnLog;
        _http.Dispose();
    }
}
