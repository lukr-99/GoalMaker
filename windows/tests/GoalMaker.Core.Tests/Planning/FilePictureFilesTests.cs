using GoalMaker.Infrastructure.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>The picture cache on disk (ADR 0018): a JPEG per picture and a marker on the ones still to upload.</summary>
public sealed class FilePictureFilesTests : IDisposable
{
    private readonly string folder = Path.Combine(Path.GetTempPath(), "goalmaker-tests", Guid.NewGuid().ToString("N"), "pictures");
    private readonly FilePictureFiles files;

    public FilePictureFilesTests() => files = new FilePictureFiles(folder);

    public void Dispose()
    {
        try
        {
            Directory.Delete(Path.GetDirectoryName(folder)!, recursive: true);
        }
        catch (DirectoryNotFoundException)
        {
        }
    }

    [Fact]
    public void AnEmptyCacheHasNothingAndMakesNoFolder()
    {
        var id = Guid.NewGuid().ToString();

        Assert.Empty(files.Ids());
        Assert.Empty(files.Pending());
        Assert.False(files.Has(id));
        Assert.Null(files.Read(id));
        Assert.False(Directory.Exists(folder));
    }

    [Fact]
    public void APictureIsKeptWithItsMarkUntilItGoesUp()
    {
        var added = Guid.NewGuid().ToString();
        var downloaded = Guid.NewGuid().ToString();

        files.Write(added, [1, 2, 3], pending: true);
        files.Write(downloaded, [4, 5], pending: false);

        Assert.Equal([1, 2, 3], files.Read(added));
        Assert.True(files.Has(downloaded));
        Assert.Equal(new HashSet<string> { added, downloaded }, files.Ids());
        Assert.Equal([added], files.Pending());
        Assert.Equal(
            new HashSet<string> { added + ".jpg", added + ".pending", downloaded + ".jpg" },
            Directory.EnumerateFiles(folder).Select(path => Path.GetFileName(path)).ToHashSet());

        files.Uploaded(added);
        Assert.Empty(files.Pending());

        files.Write(added, [9], pending: false);
        Assert.Equal([9], files.Read(added));

        files.Delete(added);
        Assert.Equal([downloaded], files.Ids());
    }

    [Theory]
    [InlineData("..\\..\\secrets")]
    [InlineData("not-a-uuid")]
    public void AnythingButAPictureIdIsRefused(string id) =>
        Assert.Throws<ArgumentException>(() => files.Write(id, [1], pending: false));
}
