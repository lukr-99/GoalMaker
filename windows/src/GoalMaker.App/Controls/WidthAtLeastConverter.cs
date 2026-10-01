using System.Globalization;
using System.Windows.Data;

namespace GoalMaker.App.Controls;

/// <summary>
/// True when a width reaches the number in the converter's parameter: what lets a template lay itself
/// out for the room it has (Today's habits beside the tasks or under them, the Habits page in one
/// column or two), the same in the main window, a mini window and a snapshot.
/// </summary>
public sealed class WidthAtLeastConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is double width && double.TryParse(parameter as string, NumberStyles.Float, CultureInfo.InvariantCulture, out var least) && width >= least;

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}
