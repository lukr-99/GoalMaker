using System.Windows;
using System.Windows.Controls;
using GoalMaker.App.ViewModels;

namespace GoalMaker.App.Views;

/// <summary>
/// A list page (Today, Tomorrow, Inbox): the shared ListTemplate (Resources/ListTemplate.xaml) around
/// its view model, in the theme's background, font and text color. Subclasses give NavigationView a
/// type per list.
/// </summary>
public abstract class ListPage : Page
{
    protected ListPage(ListViewModel viewModel)
    {
        DataContext = viewModel;
        Title = viewModel.Title;
        SetResourceReference(BackgroundProperty, "GM.BackgroundBrush");
        SetResourceReference(FontFamilyProperty, "GM.BodyFont");
        SetResourceReference(ForegroundProperty, "GM.TextBrush");
        var host = new ContentControl
        {
            Content = viewModel,
            Focusable = false,
            HorizontalContentAlignment = HorizontalAlignment.Stretch,
            VerticalContentAlignment = VerticalAlignment.Stretch,
        };
        host.SetResourceReference(ContentControl.ContentTemplateProperty, "ListTemplate");
        Content = host;
    }
}
