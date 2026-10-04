namespace GoalMaker.Core.Planning;

/// <summary>
/// This device's cache of life goal picture files (ADR 0018), by picture id, with a mark on the ones
/// still to upload. The rows are in the replica; only the JPEG bytes are here.
/// </summary>
public interface IPictureFiles
{
    bool Has(string id);

    byte[]? Read(string id);

    /// <summary>Keeps <paramref name="bytes"/> as picture <paramref name="id"/>; with <paramref name="pending"/>, it waits for an upload.</summary>
    void Write(string id, byte[] bytes, bool pending);

    void Delete(string id);

    /// <summary>Every picture this device holds a file for.</summary>
    IReadOnlySet<string> Ids();

    /// <summary>The pictures added here that have not gone up yet.</summary>
    IReadOnlySet<string> Pending();

    void Uploaded(string id);
}
