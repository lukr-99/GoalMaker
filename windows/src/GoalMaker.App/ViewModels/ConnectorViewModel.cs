using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.App.Localization;
using GoalMaker.Core.Connector;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The Claude connector card in Settings (docs/connector.md): the link's state, Create, Make a new link
/// and Revoke, each of the last two confirmed in place because they stop the current link. A new link's
/// URL lives only here, shown once, and is never written anywhere.
/// </summary>
public sealed partial class ConnectorViewModel : ObservableObject
{
    private readonly IConnectorLinks links;
    private readonly string backendUrl;
    private readonly IStrings strings;
    private readonly Action<string> copy;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowCreate), nameof(ShowActive))]
    private bool loaded;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowCreate), nameof(ShowActive))]
    private bool unavailable;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(CreateCommand), nameof(RotateCommand), nameof(RevokeCommand), nameof(ConfirmCommand), nameof(RefreshCommand))]
    private bool busy;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowCreate), nameof(ShowActive))]
    private bool hasActive;

    [ObservableProperty]
    private string status = string.Empty;

    [ObservableProperty]
    private string lastUsed = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasNewUrl))]
    private string newUrl = string.Empty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsConfirming), nameof(ConfirmText), nameof(ConfirmLabel))]
    private ConnectorAction? pending;

    [ObservableProperty]
    private bool copied;

    public ConnectorViewModel(IConnectorLinks links, string backendUrl, IStrings strings, Action<string> copy)
    {
        this.links = links;
        this.backendUrl = backendUrl;
        this.strings = strings;
        this.copy = copy;
    }

    public bool HasNewUrl => NewUrl.Length > 0;

    /// <summary>No link yet: offer to make one.</summary>
    public bool ShowCreate => Loaded && !Unavailable && !HasActive;

    /// <summary>A link is active: say when it was made and used, and offer a new one or revoking it.</summary>
    public bool ShowActive => Loaded && !Unavailable && HasActive;

    public bool IsConfirming => Pending is not null;

    public string ConfirmText => Pending switch
    {
        ConnectorAction.Rotate => strings.Get("Connector.RotateWarning"),
        ConnectorAction.Revoke => strings.Get("Connector.RevokeWarning"),
        _ => string.Empty,
    };

    public string ConfirmLabel => Pending == ConnectorAction.Revoke ? strings.Get("Connector.Revoke") : strings.Get("Connector.Rotate");

    /// <summary>Reads the link's state; the page calls it when it opens.</summary>
    [RelayCommand(CanExecute = nameof(CanCall))]
    public Task RefreshAsync() => CallAsync(LoadAsync);

    private bool CanCall() => !Busy;

    [RelayCommand(CanExecute = nameof(CanCall))]
    private Task CreateAsync() => CallAsync(async () =>
    {
        var secret = await links.CreateAsync();
        NewUrl = IConnectorLinks.Url(backendUrl, secret);
        Copied = false;
        await LoadAsync();
    });

    [RelayCommand(CanExecute = nameof(CanCall))]
    private void Rotate() => Pending = ConnectorAction.Rotate;

    [RelayCommand(CanExecute = nameof(CanCall))]
    private void Revoke() => Pending = ConnectorAction.Revoke;

    [RelayCommand(CanExecute = nameof(CanCall))]
    private Task ConfirmAsync()
    {
        var action = Pending;
        Pending = null;
        return action == ConnectorAction.Revoke
            ? CallAsync(async () =>
            {
                await links.RevokeAsync();
                NewUrl = string.Empty;
                await LoadAsync();
            })
            : CreateAsync();
    }

    [RelayCommand]
    private void Cancel() => Pending = null;

    [RelayCommand]
    private void Copy()
    {
        if (HasNewUrl)
        {
            copy(NewUrl);
            Copied = true;
        }
    }

    /// <summary>The owner is done with the new link; it isn't shown again.</summary>
    [RelayCommand]
    private void HideNewUrl()
    {
        NewUrl = string.Empty;
        Copied = false;
    }

    private async Task LoadAsync()
    {
        var active = (await links.ListAsync()).FirstOrDefault(link => link.Active);
        HasActive = active is not null;
        Status = active is null ? strings.Get("Connector.None") : strings.Get("Connector.Active", Format(active.CreatedAt));
        LastUsed = active is null
            ? string.Empty
            : active.LastUsedAt is { } used ? strings.Get("Connector.LastUsed", Format(used)) : strings.Get("Connector.NeverUsed");
        Unavailable = false;
        Loaded = true;
    }

    private async Task CallAsync(Func<Task> work)
    {
        if (Busy)
        {
            return;
        }

        Busy = true;
        try
        {
            await work();
        }
        catch (Exception error) when (error is RemoteUnavailableException or RemoteRejectedException)
        {
            Unavailable = true;
            Loaded = true;
        }
        finally
        {
            Busy = false;
        }
    }

    private static string Format(DateTimeOffset at) => at.ToLocalTime().ToString("ddd d MMM, HH:mm", CultureInfo.CurrentCulture);
}
