using System.Windows;
using System.Windows.Input;

namespace GoalMaker.App.Controls;

/// <summary>
/// Runs a command on Esc, and only takes the key when the command has something to do (M6-05). A
/// KeyBinding takes Esc even when its command cannot run, which would keep a mini window from closing
/// on an empty composer line; with this, the first Esc clears the line and the next one closes.
/// </summary>
public static class EscapeRuns
{
    public static readonly DependencyProperty CommandProperty = DependencyProperty.RegisterAttached(
        "Command", typeof(ICommand), typeof(EscapeRuns), new PropertyMetadata(null, OnCommandChanged));

    public static ICommand? GetCommand(DependencyObject element) => (ICommand?)element.GetValue(CommandProperty);

    public static void SetCommand(DependencyObject element, ICommand? value) => element.SetValue(CommandProperty, value);

    private static void OnCommandChanged(DependencyObject element, DependencyPropertyChangedEventArgs e)
    {
        if (element is not UIElement target)
        {
            return;
        }

        target.KeyDown -= OnKeyDown;
        if (e.NewValue is not null)
        {
            target.KeyDown += OnKeyDown;
        }
    }

    private static void OnKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key != Key.Escape || Keyboard.Modifiers != ModifierKeys.None || GetCommand((DependencyObject)sender) is not { } command)
        {
            return;
        }

        if (command.CanExecute(null))
        {
            command.Execute(null);
            e.Handled = true;
        }
    }
}
