using System.Windows;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// Email, then the 6-digit code, and beside them the QR code the phone scans. All behavior is in
/// <c>SignInViewModel</c>; the view only tells it when the window comes back, so a code copied in the
/// mail app goes in by itself, and starts and stops the QR code with the screen.
/// </summary>
public partial class SignInView
{
    private Window? window;

    public SignInView()
    {
        InitializeComponent();
        // The view stays loaded and is only hidden once signed in, and a window in the tray hides it
        // too: a code is asked for while the screen is on show, and never otherwise.
        IsVisibleChanged += (_, e) =>
        {
            var phone = (DataContext as SignInViewModel)?.Phone;
            if ((bool)e.NewValue)
            {
                phone?.Start();
            }
            else
            {
                phone?.Stop();
            }
        };
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
