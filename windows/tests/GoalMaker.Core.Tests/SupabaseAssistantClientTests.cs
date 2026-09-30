using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json.Nodes;
using GoalMaker.Core.Assistant;
using GoalMaker.Infrastructure.Assistant;

namespace GoalMaker.Core.Tests;

/// <summary>The quick chat's call to the assistant function over a fake server (the M7 plan, "The call").</summary>
public sealed class SupabaseAssistantClientTests
{
    private const string Backend = "http://127.0.0.1:55321/";
    private readonly FakeServer server = new();
    private string? token = "the-token";
    private int unauthorized;

    [Fact]
    public async Task TheWholeThreadGoesWithTheOwnersSessionAndTheAnswerComesBack()
    {
        server.Respond(HttpStatusCode.OK, """{"text":"Added Call the bank for tomorrow."}""");

        var reply = await Client().SendAsync(
            [
                new AssistantMessage(AssistantRole.User, "hi"),
                new AssistantMessage(AssistantRole.Model, "Hello."),
                new AssistantMessage(AssistantRole.User, "add call the bank tomorrow"),
            ],
            TestContext.Current.CancellationToken);

        Assert.Equal(new AssistantReply.Answer("Added Call the bank for tomorrow."), reply);
        var request = Assert.Single(server.Requests);
        Assert.Equal(HttpMethod.Post, request.Method);
        Assert.Equal("http://127.0.0.1:55321/functions/v1/assistant", request.Url);
        Assert.Equal("Bearer the-token", request.Authorization);
        Assert.Equal("publishable", request.ApiKey);
        Assert.True(JsonNode.DeepEquals(
            JsonNode.Parse("""{"messages":[{"role":"user","text":"hi"},{"role":"model","text":"Hello."},{"role":"user","text":"add call the bank tomorrow"}]}"""),
            JsonNode.Parse(request.Body)));
    }

    [Theory]
    [InlineData(503, "unavailable", AssistantProblem.Unavailable)]
    [InlineData(429, "rate_limited", AssistantProblem.RateLimited)]
    [InlineData(429, "provider_limit", AssistantProblem.ProviderLimit)]
    [InlineData(400, "bad_request", AssistantProblem.BadRequest)]
    [InlineData(502, "failed", AssistantProblem.Failed)]
    public async Task EachErrorCodeIsItsOwnProblem(int status, string code, AssistantProblem problem)
    {
        server.Respond((HttpStatusCode)status, $$"""{"error":"{{code}}","message":"the server's words"}""");

        var reply = await Client().SendAsync([new AssistantMessage(AssistantRole.User, "hi")], TestContext.Current.CancellationToken);

        Assert.Equal(new AssistantReply.Failure(problem, "the server's words"), reply);
    }

    [Theory]
    [InlineData(404, AssistantProblem.Unavailable)]
    [InlineData(429, AssistantProblem.RateLimited)]
    [InlineData(500, AssistantProblem.Failed)]
    public async Task AnAnswerWithoutACodeIsReadFromItsStatus(int status, AssistantProblem problem)
    {
        server.Respond((HttpStatusCode)status, "<html>gateway</html>");

        var reply = await Client().SendAsync([new AssistantMessage(AssistantRole.User, "hi")], TestContext.Current.CancellationToken);

        Assert.Equal(problem, Assert.IsType<AssistantReply.Failure>(reply).Problem);
    }

    [Fact]
    public async Task ARefusedSessionIsSignedOutAndAsksForARenewal()
    {
        server.Respond(HttpStatusCode.Unauthorized, """{"msg":"Invalid JWT"}""");

        var reply = await Client().SendAsync([new AssistantMessage(AssistantRole.User, "hi")], TestContext.Current.CancellationToken);

        Assert.Equal(AssistantProblem.SignedOut, Assert.IsType<AssistantReply.Failure>(reply).Problem);
        Assert.Equal(1, unauthorized);
    }

    [Fact]
    public async Task WithoutASessionNothingIsSent()
    {
        token = null;

        var reply = await Client().SendAsync([new AssistantMessage(AssistantRole.User, "hi")], TestContext.Current.CancellationToken);

        Assert.Equal(new AssistantReply.Failure(AssistantProblem.SignedOut), reply);
        Assert.Empty(server.Requests);
    }

    [Fact]
    public async Task AServerThatCantBeReachedIsOffline()
    {
        server.Unreachable = true;

        var reply = await Client().SendAsync([new AssistantMessage(AssistantRole.User, "hi")], TestContext.Current.CancellationToken);

        Assert.Equal(AssistantProblem.Offline, Assert.IsType<AssistantReply.Failure>(reply).Problem);
    }

    [Fact]
    public async Task AnAnswerWithoutTextIsAFailure()
    {
        server.Respond(HttpStatusCode.OK, "{}");

        var reply = await Client().SendAsync([new AssistantMessage(AssistantRole.User, "hi")], TestContext.Current.CancellationToken);

        Assert.Equal(AssistantProblem.Failed, Assert.IsType<AssistantReply.Failure>(reply).Problem);
    }

    private SupabaseAssistantClient Client() =>
        new(new HttpClient(server), Backend, "publishable", () => token, () => unauthorized++);

    private sealed record SeenRequest(HttpMethod Method, string Url, string? Authorization, string? ApiKey, string Body);

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
                request.Method,
                request.RequestUri!.AbsoluteUri,
                request.Headers.Authorization?.ToString(),
                request.Headers.TryGetValues("apikey", out var keys) ? keys.Single() : null,
                request.Content is null ? string.Empty : await request.Content.ReadAsStringAsync(cancellationToken)));
            return new HttpResponseMessage(status) { Content = new StringContent(body, Encoding.UTF8, "application/json") };
        }
    }
}
