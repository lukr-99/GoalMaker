using GoalMaker.Core.Sync;

namespace GoalMaker.Core.Planning;

/// <summary>
/// The life-goal-pictures bucket (ADR 0018), as the signed-in owner, at <c>&lt;owner&gt;/&lt;id&gt;.jpg</c>.
/// A call that can't reach the server throws <see cref="RemoteUnavailableException"/>, like the sync's,
/// and one the server refuses throws <see cref="RemoteRejectedException"/>.
/// </summary>
public interface IPictureCloud
{
    Task UploadAsync(string owner, string id, byte[] bytes, CancellationToken cancellationToken);

    /// <summary>The file's bytes, or null when the bucket has no such file.</summary>
    Task<byte[]?> DownloadAsync(string owner, string id, CancellationToken cancellationToken);

    /// <summary>Removes the file; one that is already gone is fine.</summary>
    Task RemoveAsync(string owner, string id, CancellationToken cancellationToken);
}
