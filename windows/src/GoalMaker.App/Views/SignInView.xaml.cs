using System.Windows;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// Email, then the 6-digit code. All behavior is in <c>SignInViewModel</c>; the view only tells it when
/// the window comes back, so a code copied in the mail app goes in by itself.
/// </summary>
public partial class SignInView
{
    private Window? window;

    public SignInView()
    {
        InitializeComponent();
        Loaded += (_, _) =>
        {
            window = Window.GetWindow(this);
            if (window is not null)
            {
                window.Activated += OnActivated;
            }
        };
        Unloaded += (_, _) =>
        {
            if (window is not null)
            {
                window.Activated -= OnActivated;
            }
        };
    }

    private void OnActivated(object? sender, EventArgs e) => (DataContext as SignInViewModel)?.TakeCopiedCode();
}
