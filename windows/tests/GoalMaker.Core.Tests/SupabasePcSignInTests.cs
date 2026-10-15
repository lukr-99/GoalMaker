using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json.Nodes;
using GoalMaker.Core.Auth;
using GoalMaker.Infrastructure.Auth;

namespace GoalMaker.Core.Tests;

/// <summary>The PC's calls to the pc-sign-in function over a fake server (ADR 0020).</summary>
public sealed class SupabasePcSignInTests
{
    private const string Scan = "Qx3mT9vB2kLw8RfN1pZc7YhJ4sDg6EaU0oKiVt5nM-_";
    private const string Pickup = "Ab3mT9vB2kLw8RfN1pZc7YhJ4sDg6EaU0oKiVt5nM-_";
    private static readonly PcSignInStart.Started Request = new("c0ffee00-0000-4000-8000-000000000001", Scan, Pickup, TimeSpan.FromMinutes(2));
    private readonly FakeServer server = new();

    private SupabasePcSignIn Client() => new(new HttpClient(server), "http://127.0.0.1:55321/", "publishable");

    [Fact]
    public async Task StartingSendsTheNameWithOnlyThePublishableKey()
    {
        server.Respond(HttpStatusCode.OK, $$"""{"id":"{{Request.Id}}","scan":"{{Scan}}","pickup":"{{Pickup}}","expires_in":120}""");

        var started = await Client().StartAsync("DESKTOP-1", TestContext.Current.CancellationToken);

        Assert.Equal(Request, started);
        var request = Assert.Single(server.Requests);
        Assert.Equal("http://127.0.0.1:55321/functions/v1/pc-sign-in", request.Url);
        Assert.Equal("publishable", request.ApiKey);
        Assert.Null(request.Authorization);
        Assert.True(JsonNode.DeepEquals(JsonNode.Parse("""{"action":"start","device":"DESKTOP-1"}"""), JsonNode.Parse(request.Body)));
    }

    [Theory]
    [InlineData(404, typeof(PcSignInStart.Unavailable))]
    [InlineData(429, typeof(PcSignInStart.Busy))]
    [InlineData(502, typeof(PcSignInStart.Failed))]
    public async Task AStartWithoutARequestSaysWhy(int status, Type expected)
    {
        server.Respond((HttpStatusCode)status, """{"error":"x","message":"y"}""");
        Assert.IsType(expected, await Client().StartAsync("PC", TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task AStartWithABadSecretIsAFailure()
    {
        server.Respond(HttpStatusCode.OK, $$"""{"id":"x","scan":"short","pickup":"{{Pickup}}","expires_in":120}""");
        Assert.IsType<PcSignInStart.Failed>(await Client().StartAsync("PC", TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task NoServerIsOffline()
    {
        server.Unreachable = true;
        Assert.IsType<PcSignInStart.Offline>(await Client().StartAsync("PC", TestContext.Current.CancellationToken));
        Assert.IsType<PcSignInState.Offline>(await Client().PickUpAsync(Request, TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task PickingUpSendsThePickupSecretAndNeverTheScanSecret()
    {
        server.Respond(HttpStatusCode.OK, """{"state":"waiting"}""");

        Assert.IsType<PcSignInState.Waiting>(await Client().PickUpAsync(Request, TestContext.Current.CancellationToken));

        var body = Assert.Single(server.Requests).Body;
        Assert.True(JsonNode.DeepEquals(
            JsonNode.Parse($$"""{"action":"pick_up","id":"{{Request.Id}}","pickup":"{{Pickup}}"}"""),
            JsonNode.Parse(body)));
        Assert.DoesNotContain(Scan, body, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("""{"state":"declined"}""", typeof(PcSignInState.Declined))]
    [InlineData("""{"state":"expired"}""", typeof(PcSignInState.Expired))]
    [InlineData("""{"state":"approved","email":"me@example.com"}""", typeof(PcSignInState.Failed))]
    [InlineData("""{"state":"something new"}""", typeof(PcSignInState.Failed))]
    [InlineData("""[1, 2]""", typeof(PcSignInState.Failed))]
    public async Task EveryAnswerIsAState(string answer, Type expected)
    {
        server.Respond(HttpStatusCode.OK, answer);
        Assert.IsType(expected, await Client().PickUpAsync(Request, TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task AnApprovalBringsTheOwnersCode()
    {
        server.Respond(HttpStatusCode.OK, """{"state":"approved","email":"me@example.com","code":"482913"}""");

        var state = Assert.IsType<PcSignInState.Approved>(await Client().PickUpAsync(Request, TestContext.Current.CancellationToken));

        Assert.Equal("me@example.com", state.Email.Value);
        Assert.Equal("482913", state.Code.Value);
        Assert.DoesNotContain("482913", state.ToString(), StringComparison.Ordinal);
        Assert.DoesNotContain(Pickup, Request.ToString(), StringComparison.Ordinal);
    }

    private sealed record SeenRequest(string Url, string? Authorization, string? ApiKey, string Body);

    private sealed class FakeServer : HttpMessageHandler
    {
        private HttpStatusCode status = HttpStatusCode.OK;
        private string body = "{}";

        public List<SeenRequest> Requests { get; } = [];

        public bool Unreachable { get; set; }

        public void Respond(HttpStatusCode code, string text)
        {
            status = code;
            body = text;
        }

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            if (Unreachable)
            {
                throw new HttpRequestException("No connection could be made.");
            }

            Requests.Add(new SeenRequest(
                request.RequestUri!.AbsoluteUri,
                request.Headers.Authorization?.ToString(),
                request.Headers.TryGetValues("apikey", out var keys) ? keys.Single() : null,
                request.Content is null ? string.Empty : await request.Content.ReadAsStringAsync(cancellationToken)));
            return new HttpResponseMessage(status) { Content = new StringContent(body, Encoding.UTF8, "application/json") };
        }
    }
}
