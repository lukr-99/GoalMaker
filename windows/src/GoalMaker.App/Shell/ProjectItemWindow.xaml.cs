using System.Windows;
using System.Windows.Input;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Shell;

/// <summary>
/// The new item window of the Projects page (docs/projects.md): a small dialog over the main window
/// for writing a fuller project item. The form is <see cref="ProjectItemFormViewModel"/>; the window
/// hands it the keys, puts the keyboard in the title when it opens and when the form asks, and closes
/// when the form is finished.
/// </summary>
public partial class ProjectItemWindow
{
    public ProjectItemWindow(ProjectItemFormViewModel form)
    {
        InitializeComponent();
        DataContext = form;
        form.Finished += (_, _) => Close();
        form.TitleWanted += (_, _) => FocusTitle();

        // Enter is caught on the way down, before the notes box turns Ctrl+Enter into a new line.
        PreviewKeyDown += (_, e) =>
        {
            if (e.Key == Key.Enter && form.Press(e.Key, Keyboard.Modifiers, TitleBox.IsKeyboardFocusWithin))
            {
                e.Handled = true;
            }
        };

        // Escape on the way up, so an open picker closes first and only the next Escape closes the window.
        KeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape && form.Press(e.Key, Keyboard.Modifiers, TitleBox.IsKeyboardFocusWithin))
            {
                e.Handled = true;
            }
        };
        Loaded += (_, _) =>
        {
            Activate();
            FocusTitle();
        };
    }

    /// <summary>
    /// Shows the window for <paramref name="form"/> over <paramref name="owner"/> (centred on the screen
    /// without one) and waits until it closes. <paramref name="prepare"/> runs on it first; the app
    /// passes the theme's.
    /// </summary>
    public static void Open(ProjectItemFormViewModel form, Window? owner, Action<Window>? prepare = null)
    {
        var window = new ProjectItemWindow(form);
        if (owner is { IsVisible: true })
        {
            window.Owner = owner;
        }
        else
        {
            window.WindowStartupLocation = WindowStartupLocation.CenterScreen;
        }

        prepare?.Invoke(window);
        window.ShowDialog();
    }

    private void FocusTitle()
    {
        TitleBox.Focus();
        TitleBox.CaretIndex = TitleBox.Text.Length;
    }
}
