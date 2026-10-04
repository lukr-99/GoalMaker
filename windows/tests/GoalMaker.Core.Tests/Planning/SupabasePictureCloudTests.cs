using System.Net;
using System.Net.Http;
using GoalMaker.Core.Sync;
using GoalMaker.Infrastructure.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>The life-goal-pictures bucket through Supabase Storage, over a fake server (ADR 0018).</summary>
public sealed class SupabasePictureCloudTests : IDisposable
{
    private const string Owner = "11111111-1111-1111-1111-111111111111";
    private const string Id = "22222222-2222-2222-2222-222222222222";
    private readonly FakeStorage server = new();
    private readonly HttpClient http;
    private int unauthorized;

    public SupabasePictureCloudTests() => http = new HttpClient(server);

    public void Dispose() => http.Dispose();

    private SupabasePictureCloud Cloud() => new(http, "http://127.0.0.1:55321/", "publishable", () => "the-token", () => unauthorized++);

    [Fact]
    public async Task APictureGoesUpAsTheOwnerAndReplacesAnOlderOne()
    {
        server.Respond(HttpStatusCode.OK, """{"Key":"life-goal-pictures/x.jpg"}""");

        await Cloud().UploadAsync(Owner, Id, [0xFF, 0xD8, 1], TestContext.Current.CancellationToken);

        var request = Assert.Single(server.Requests);
        Assert.Equal(HttpMethod.Post, request.Method);
        Assert.Equal($"http://127.0.0.1:55321/storage/v1/object/life-goal-pictures/{Owner}/{Id}.jpg", request.Url);
        Assert.Equal("Bearer the-token", request.Authorization);
        Assert.Equal("publishable", request.ApiKey);
        Assert.Equal("true", request.Upsert);
        Assert.Equal("image/jpeg", request.ContentType);
        Assert.Equal([0xFF, 0xD8, 1], request.Body);
    }

    [Fact]
    public async Task APictureComesDownThroughTheAuthenticatedPath()
    {
        server.Respond(HttpStatusCode.OK, [7, 8, 9]);

        var bytes = await Cloud().DownloadAsync(Owner, Id, TestContext.Current.CancellationToken);

        Assert.Equal([7, 8, 9], bytes);
        Assert.Equal($"http://127.0.0.1:55321/storage/v1/object/authenticated/life-goal-pictures/{Owner}/{Id}.jpg", Assert.Single(server.Requests).Url);
    }

    [Theory]
    [InlineData(404, """{"error":"not_found"}""")]
    [InlineData(400, """{"statusCode":"404","error":"not_found","message":"Object not found"}""")]
    public async Task AMissingFileIsNullToDownloadAndFineToRemove(int status, string body)
    {
        server.Respond((HttpStatusCode)status, body);

        Assert.Null(await Cloud().DownloadAsync(Owner, Id, TestContext.Current.CancellationToken));
        await Cloud().RemoveAsync(Owner, Id, TestContext.Current.CancellationToken);

        Assert.Equal(HttpMethod.Delete, server.Requests[^1].Method);
        Assert.Equal($"http://127.0.0.1:55321/storage/v1/object/life-goal-pictures/{Owner}/{Id}.jpg", server.Requests[^1].Url);
    }

    [Fact]
    public async Task AMissingBucketObjectIsStillAnErrorForAnUpload()
    {
        server.Respond(HttpStatusCode.NotFound, """{"error":"not_found"}""");

        await Assert.ThrowsAsync<RemoteRejectedException>(() => Cloud().UploadAsync(Owner, Id, [1], TestContext.Current.CancellationToken));
    }

    [Theory]
    [InlineData(408)]
    [InlineData(429)]
    [InlineData(503)]
    public async Task ABusyServerIsTriedAgainLater(int status)
    {
        server.Respond((HttpStatusCode)status, "busy");

        await Assert.ThrowsAsync<RemoteUnavailableException>(() => Cloud().UploadAsync(Owner, Id, [1], TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task ARefusedSessionAsksForARenewalAndIsTriedAgainLater()
    {
        server.Respond(HttpStatusCode.Unauthorized, """{"message":"jwt expired"}""");

        await Assert.ThrowsAsync<RemoteUnauthorizedException>(() => Cloud().DownloadAsync(Owner, Id, TestContext.Current.CancellationToken));

        Assert.Equal(1, unauthorized);
    }

    [Fact]
    public async Task AFileTheServerRefusesIsRejectedAndNoConnectionIsUnavailable()
    {
        server.Respond(HttpStatusCode.RequestEntityTooLarge, """{"error":"Payload too large"}""");
        await Assert.ThrowsAsync<RemoteRejectedException>(() => Cloud().UploadAsync(Owner, Id, [1], TestContext.Current.CancellationToken));

        server.Unreachable = true;
        await Assert.ThrowsAsync<RemoteUnavailableException>(() => Cloud().UploadAsync(Owner, Id, [1], TestContext.Current.CancellationToken));
    }

    private sealed record SeenRequest(HttpMethod Method, string Url, string? Authorization, string? ApiKey, string? Upsert, string? ContentType, byte[] Body);

    private sealed class FakeStorage : HttpMessageHandler
    {
        private HttpStatusCode status = HttpStatusCode.OK;
        private byte[] body = [];

        public List<SeenRequest> Requests { get; } = [];

        public bool Unreachable { get; set; }

        public void Respond(HttpStatusCode code, string text) => Respond(code, System.Text.Encoding.UTF8.GetBytes(text));

        public void Respond(HttpStatusCode code, byte[] bytes)
        {
            status = code;
            body = bytes;
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
                request.Headers.TryGetValues("x-upsert", out var upsert) ? upsert.Single() : null,
                request.Content?.Headers.ContentType?.MediaType,
                request.Content is null ? [] : await request.Content.ReadAsByteArrayAsync(cancellationToken)));
            return new HttpResponseMessage(status) { Content = new ByteArrayContent(body) };
        }
    }
}
