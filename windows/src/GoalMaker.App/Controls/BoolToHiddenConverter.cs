using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace GoalMaker.App.Controls;

/// <summary>Visible when true, Hidden (still taking its room) when false, so a row of buttons keeps its places.</summary>
public sealed class BoolToHiddenConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is true ? Visibility.Visible : Visibility.Hidden;

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is Visibility.Visible;
}
