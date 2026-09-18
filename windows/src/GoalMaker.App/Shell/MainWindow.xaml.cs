using System.ComponentModel;
using System.Windows.Threading;
using GoalMaker.App.Composition;
using GoalMaker.App.Startup;
using GoalMaker.App.Views;

namespace GoalMaker.App.Shell;

/// <summary>The main window. Closing hides it to the tray unless the app is quitting.</summary>
public partial class MainWindow
{
    private Type pendingPage = typeof(TodayPage);

    public MainWindow(AppGraph graph)
    {
        InitializeComponent();
        DataContext = graph.Shell;
        Navigation.SetPageProviderService(new PageProvider(new Dictionary<Type, Func<object>>
        {
            [typeof(TodayPage)] = () => new TodayPage(graph.Shell),
            [typeof(SettingsPage)] = () => new SettingsPage(graph.SettingsPage),
        }));
        Navigation.Loaded += (_, _) => NavigateWhenReady();
        Navigation.IsVisibleChanged += (_, _) => NavigateWhenReady();
    }

    public bool AllowClose { get; set; }

    public void Open(AppPage page)
    {
        pendingPage = page == AppPage.Settings ? typeof(SettingsPage) : typeof(TodayPage);
        NavigateWhenReady();
    }

    protected override void OnClosing(CancelEventArgs e)
    {
        if (!AllowClose)
        {
            e.Cancel = true;
            Hide();
        }

        base.OnClosing(e);
    }

    // NavigationView can only navigate once its template is applied: after Loaded, while visible.
    private void NavigateWhenReady()
    {
        if (Navigation.IsLoaded && Navigation.IsVisible)
        {
            Dispatcher.BeginInvoke(DispatcherPriority.Loaded, () => Navigation.Navigate(pendingPage));
        }
    }
}
