using System.Globalization;
using System.Windows;
using System.Windows.Data;
using System.Windows.Media;
using PhantomLauncher.Core.Models;
using PhantomLauncher.Core.Services;

namespace PhantomLauncher.Views;

/// <summary>Visible when the bound page name equals the converter parameter.</summary>
public sealed class PageToVisibility : IValueConverter
{
    public object Convert(object value, Type t, object p, CultureInfo c) =>
        Equals(value as string, p as string) ? Visibility.Visible : Visibility.Collapsed;
    public object ConvertBack(object value, Type t, object p, CultureInfo c) => Binding.DoNothing;
}

/// <summary>Checks the nav radio button whose parameter matches the current page.</summary>
public sealed class PageToChecked : IValueConverter
{
    public object Convert(object value, Type t, object p, CultureInfo c) => Equals(value as string, p as string);
    public object ConvertBack(object value, Type t, object p, CultureInfo c) => value is true ? p : Binding.DoNothing;
}

public sealed class BoolToBrush : IValueConverter
{
    public Brush True { get; set; } = Brushes.LimeGreen;
    public Brush False { get; set; } = Brushes.Gray;
    public object Convert(object value, Type t, object p, CultureInfo c) => value is true ? True : False;
    public object ConvertBack(object value, Type t, object p, CultureInfo c) => Binding.DoNothing;
}

public sealed class CompatToBrush : IValueConverter
{
    public object Convert(object value, Type t, object p, CultureInfo c) => value switch
    {
        CompatState.Compatible => new SolidColorBrush(Color.FromRgb(0x66, 0xBB, 0x6A)),
        CompatState.Warning => new SolidColorBrush(Color.FromRgb(0xFF, 0xA7, 0x26)),
        _ => new SolidColorBrush(Color.FromRgb(0xEF, 0x53, 0x50))
    };
    public object ConvertBack(object value, Type t, object p, CultureInfo c) => Binding.DoNothing;
}

public sealed class LogLevelToBrush : IValueConverter
{
    public object Convert(object value, Type t, object p, CultureInfo c) => value switch
    {
        LogLevel.Error => new SolidColorBrush(Color.FromRgb(0xEF, 0x53, 0x50)),
        LogLevel.Warn => new SolidColorBrush(Color.FromRgb(0xFF, 0xA7, 0x26)),
        LogLevel.Debug => new SolidColorBrush(Color.FromRgb(0x6A, 0x6A, 0x80)),
        _ => new SolidColorBrush(Color.FromRgb(0xC8, 0xC8, 0xD8))
    };
    public object ConvertBack(object value, Type t, object p, CultureInfo c) => Binding.DoNothing;
}

public sealed class NonZero : IValueConverter
{
    public static readonly NonZero Instance = new();
    public object Convert(object value, Type t, object p, CultureInfo c) => value is int i && i > 0 ? Visibility.Visible : Visibility.Collapsed;
    public object ConvertBack(object value, Type t, object p, CultureInfo c) => Binding.DoNothing;
}
