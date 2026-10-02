using System.Globalization;
using System.Windows.Data;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Controls;

/// <summary>
/// A reminder slider's value in half hours (0 to 47) as the time it stands for, "20:30", so the row
/// shows the time under the thumb while it moves. The kit's slider formats its value only with a
/// composite format, which cannot turn half hours into a time.
/// </summary>
public sealed class HalfHourTextConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is double half ? SettingsViewModel.HalfHourText(half) : string.Empty;

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}
