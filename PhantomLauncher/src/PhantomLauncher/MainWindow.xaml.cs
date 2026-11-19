using System.Collections.Specialized;
using System.Windows;
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
        Loaded += async (_, _) => await _vm.InitializeAsync();
        Closed += (_, _) => _vm.Dispose();
    }

    private void Minimize_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void Maximize_Click(object sender, RoutedEventArgs e) =>
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;

    private void Close_Click(object sender, RoutedEventArgs e) => Close();
}
