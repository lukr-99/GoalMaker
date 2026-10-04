using System.Net;
using System.Net.Http.Headers;
using GoalMaker.Core.Planning;
using GoalMaker.Core.Sync;

namespace GoalMaker.Infrastructure.Planning;

/// <summary>
/// <see cref="IPictureCloud"/> over Supabase Storage (<c>/storage/v1/object/...</c>) with the publishable
/// key and the owner's access token, in the life-goal-pictures bucket (ADR 0018). As with PostgREST
/// calls, a refused session is <see cref="RemoteUnauthorizedException"/> and
/// <paramref name="unauthorized"/> hears of it, so the session gets renewed or ended (docs/sign-in.md);
/// offline, a timeout or a busy server is <see cref="RemoteUnavailableException"/>, and anything else
/// the server refuses is <see cref="RemoteRejectedException"/>.
/// </summary>
public sealed class SupabasePictureCloud(
    HttpClient http, string baseUrl, string publishableKey, Func<string?> accessToken, Action? unauthorized = null) : IPictureCloud
{
    private const string Bucket = "life-goal-pictures";
    private readonly string objects = baseUrl.TrimEnd('/') + "/storage/v1/object/";

    public async Task UploadAsync(string owner, string id, byte[] bytes, CancellationToken cancellationToken)
    {
        using var request = Request(HttpMethod.Post, $"{Bucket}/{owner}/{id}.jpg");
        request.Headers.Add("x-upsert", "true");
        request.Content = new ByteArrayContent(bytes);
        request.Content.Headers.ContentType = new MediaTypeHeaderValue("image/jpeg");
        await SendAsync(request, missingIsFine: false, cancellationToken).ConfigureAwait(false);
    }

    public async Task<byte[]?> DownloadAsync(string owner, string id, CancellationToken cancellationToken)
    {
        using var request = Request(HttpMethod.Get, $"authenticated/{Bucket}/{owner}/{id}.jpg");
        return await SendAsync(request, missingIsFine: true, cancellationToken).ConfigureAwait(false);
    }

    public async Task RemoveAsync(string owner, string id, CancellationToken cancellationToken)
    {
        using var request = Request(HttpMethod.Delete, $"{Bucket}/{owner}/{id}.jpg");
        await SendAsync(request, missingIsFine: true, cancellationToken).ConfigureAwait(false);
    }

    private HttpRequestMessage Request(HttpMethod method, string path)
    {
        var token = accessToken() ?? throw new RemoteUnavailableException("Not signed in.");
        var request = new HttpRequestMessage(method, objects + path);
        request.Headers.Add("apikey", publishableKey);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        return request;
    }

    private async Task<byte[]?> SendAsync(HttpRequestMessage request, bool missingIsFine, CancellationToken cancellationToken)
    {
        HttpResponseMessage response;
        try
        {
            response = await http.SendAsync(request, cancellationToken).ConfigureAwait(false);
        }
        catch (HttpRequestException error)
        {
            throw new RemoteUnavailableException("The server can't be reached.", error);
        }
        catch (TaskCanceledException error) when (!cancellationToken.IsCancellationRequested)
        {
            throw new RemoteUnavailableException("The server didn't answer in time.", error);
        }

        using (response)
        {
            if (response.IsSuccessStatusCode)
            {
                return await response.Content.ReadAsByteArrayAsync(cancellationToken).ConfigureAwait(false);
            }

            var text = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
            var status = response.StatusCode;
            // Storage answers a missing file with 404, or with 400 and "not_found" in the body.
            if (missingIsFine && (status == HttpStatusCode.NotFound
                || (status == HttpStatusCode.BadRequest && text.Contains("not_found", StringComparison.OrdinalIgnoreCase))))
            {
                return null;
            }

            var message = $"HTTP {(int)status}: {(text.Length <= 300 ? text : text[..300])}";
            if (status == HttpStatusCode.Unauthorized)
            {
                unauthorized?.Invoke();
                throw new RemoteUnauthorizedException(message);
            }

            var temporary = status is HttpStatusCode.RequestTimeout or HttpStatusCode.TooManyRequests || (int)status >= 500;
            throw temporary ? new RemoteUnavailableException(message) : new RemoteRejectedException(message);
        }
    }
}
