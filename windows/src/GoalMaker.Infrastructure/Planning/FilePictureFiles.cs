using System.Text.RegularExpressions;
using GoalMaker.Core.Planning;

namespace GoalMaker.Infrastructure.Planning;

/// <summary>
/// <see cref="IPictureFiles"/> in a folder under the app's local data: <c>&lt;id&gt;.jpg</c> for each
/// picture, and an empty <c>&lt;id&gt;.pending</c> beside one that has not gone up yet. A file is
/// written whole under a temporary name first, so a picture is never half there.
/// </summary>
public sealed partial class FilePictureFiles(string folder) : IPictureFiles
{
    private const string Picture = ".jpg";
    private const string Marker = ".pending";

    public bool Has(string id) => File.Exists(PicturePath(id));

    public byte[]? Read(string id)
    {
        var path = PicturePath(id);
        try
        {
            return File.Exists(path) ? File.ReadAllBytes(path) : null;
        }
        catch (FileNotFoundException)
        {
            // Removed between the look and the read.
            return null;
        }
    }

    public void Write(string id, byte[] bytes, bool pending)
    {
        var target = PicturePath(id);
        Directory.CreateDirectory(folder);
        var partial = Path.Combine(folder, id + ".partial");
        File.WriteAllBytes(partial, bytes);
        if (pending)
        {
            File.WriteAllBytes(MarkerPath(id), []);
        }

        File.Move(partial, target, overwrite: true);
    }

    public void Delete(string id)
    {
        File.Delete(PicturePath(id));
        File.Delete(MarkerPath(id));
    }

    public IReadOnlySet<string> Ids() => Names(Picture);

    public IReadOnlySet<string> Pending() => Names(Marker);

    public void Uploaded(string id) => File.Delete(MarkerPath(id));

    private HashSet<string> Names(string suffix) => Directory.Exists(folder)
        ? Directory.EnumerateFiles(folder, "*" + suffix)
            .Select(Path.GetFileName)
            .Where(name => name!.EndsWith(suffix, StringComparison.Ordinal))
            .Select(name => name![..^suffix.Length])
            .ToHashSet(StringComparer.Ordinal)
        : new HashSet<string>(StringComparer.Ordinal);

    private string PicturePath(string id) => Path.Combine(folder, Check(id) + Picture);

    private string MarkerPath(string id) => Path.Combine(folder, Check(id) + Marker);

    // Ids are UUIDs; anything else would be a path, which never belongs here.
    private static string Check(string id) =>
        IdPattern().IsMatch(id) ? id : throw new ArgumentException("Not a picture id: " + id, nameof(id));

    [GeneratedRegex("^[0-9a-fA-F-]{36}$")]
    private static partial Regex IdPattern();
}
