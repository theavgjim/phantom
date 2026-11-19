using System.Collections.Specialized;
using System.ComponentModel;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;
using PhantomLauncher.ViewModels;

namespace PhantomLauncher;

public partial class MainWindow : Window
{
    private readonly MainViewModel _vm;

    public MainWindow()
    {
        InitializeComponent();
        _vm = new MainViewModel();
        DataContext = _vm;
        // Auto-scroll the console to the newest line.
        ((INotifyCollectionChanged)_vm.ConsoleLines).CollectionChanged += (_, e) =>
        {
            if (e.Action == NotifyCollectionChangedAction.Add && ConsoleList.Items.Count > 0 && _vm.Page == "Console")
                ConsoleList.ScrollIntoView(ConsoleList.Items[^1]);
        };
        _vm.PropertyChanged += OnVmChanged;
        Opacity = 0;
        Loaded += async (_, _) =>
        {
            BeginAnimation(OpacityProperty, Fade(0, 1, 260));
            AnimatePage();
            await _vm.InitializeAsync();
        };
        Closed += (_, _) => _vm.Dispose();
    }

    private void OnVmChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(MainViewModel.Page)) AnimatePage();
    }

    /// <summary>New page fades in while sliding up a few pixels.</summary>
    private void AnimatePage()
    {
        Pages.BeginAnimation(OpacityProperty, Fade(0, 1, 220));
        PagesOffset.BeginAnimation(TranslateTransform.YProperty, Fade(12, 0, 280));
    }

    private static DoubleAnimation Fade(double from, double to, int ms) => new(from, to, TimeSpan.FromMilliseconds(ms))
    {
        EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
    };

    private void Minimize_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void Maximize_Click(object sender, RoutedEventArgs e) =>
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;

    private void Close_Click(object sender, RoutedEventArgs e) => Close();
}
