using GoalMaker.App.ViewModels;
using GoalMaker.Core.Connector;
using GoalMaker.Core.Sync;

namespace GoalMaker.App.Tests;

/// <summary>The Claude connector card over a fake server (docs/connector.md).</summary>
public sealed class ConnectorViewModelTests
{
    private readonly FakeLinks links = new();
    private readonly List<string> copied = [];

    [Fact]
    public async Task ANewLinkIsShownOnceAsTheConnectorUrlAndCanBeCopied()
    {
        var connector = Connector();
        await connector.RefreshAsync();
        Assert.True(connector.ShowCreate);

        await connector.CreateCommand.ExecuteAsync(null);

        Assert.Equal("https://example.supabase.co/functions/v1/connector/secret-1", connector.NewUrl);
        Assert.True(connector.ShowActive);
        connector.CopyCommand.Execute(null);
        Assert.Equal([connector.NewUrl], copied);
        Assert.True(connector.Copied);
        connector.HideNewUrlCommand.Execute(null);
        Assert.False(connector.HasNewUrl);
    }

    [Fact]
    public async Task MakingANewLinkAndRevokingAskFirst()
    {
        var connector = Connector();
        await connector.CreateCommand.ExecuteAsync(null);

        connector.RotateCommand.Execute(null);
        Assert.True(connector.IsConfirming);
        Assert.Equal("Connector.RotateWarning", connector.ConfirmText);
        Assert.Equal(1, links.Made);
        await connector.ConfirmCommand.ExecuteAsync(null);
        Assert.Equal(2, links.Made);
        Assert.Single(links.Links, link => link.Active);

        connector.RevokeCommand.Execute(null);
        connector.CancelCommand.Execute(null);
        Assert.True(connector.ShowActive);

        connector.RevokeCommand.Execute(null);
        await connector.ConfirmCommand.ExecuteAsync(null);
        Assert.True(connector.ShowCreate);
        Assert.False(connector.HasNewUrl);
    }

    [Fact]
    public async Task ANewLinkRevokedOnTheOtherDeviceIsNoLongerShown()
    {
        var connector = Connector();
        await connector.CreateCommand.ExecuteAsync(null);
        await connector.RefreshAsync();
        Assert.True(connector.HasNewUrl);

        await links.RevokeAsync(TestContext.Current.CancellationToken);
        await connector.RefreshAsync();

        Assert.False(connector.HasNewUrl);
        Assert.True(connector.ShowCreate);
    }

    [Fact]
    public async Task OfflineTheCardSaysItNeedsAConnection()
    {
        links.Offline = true;
        var connector = Connector();

        await connector.RefreshAsync();

        Assert.True(connector.Unavailable);
        Assert.False(connector.ShowCreate || connector.ShowActive);
    }

    private ConnectorViewModel Connector() => new(links, "https://example.supabase.co/", new TestPlanner.FormatStrings(), copied.Add);

    private sealed class FakeLinks : IConnectorLinks
    {
        public List<ConnectorLink> Links { get; } = [];

        public bool Offline { get; set; }

        public int Made { get; private set; }

        public Task<IReadOnlyList<ConnectorLink>> ListAsync(CancellationToken cancellationToken = default) =>
            Offline
                ? throw new RemoteUnavailableException("offline")
                : Task.FromResult<IReadOnlyList<ConnectorLink>>([.. Links.OrderByDescending(link => link.CreatedAt)]);

        public async Task<string> CreateAsync(CancellationToken cancellationToken = default)
        {
            await RevokeAsync(cancellationToken);
            Made++;
            Links.Add(new ConnectorLink($"link-{Made}", new DateTimeOffset(2026, 9, 19, 10, Made, 0, TimeSpan.Zero), null, null));
            return $"secret-{Made}";
        }

        public Task RevokeAsync(CancellationToken cancellationToken = default)
        {
            for (var index = 0; index < Links.Count; index++)
            {
                if (Links[index].Active)
                {
                    Links[index] = Links[index] with { RevokedAt = new DateTimeOffset(2026, 9, 19, 11, 0, 0, TimeSpan.Zero) };
                }
            }

            return Task.CompletedTask;
        }
    }
}
