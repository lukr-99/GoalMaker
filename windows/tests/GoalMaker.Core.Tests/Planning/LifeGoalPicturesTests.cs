using GoalMaker.Core.Planning;
using GoalMaker.Core.Sync;
using GoalMaker.Core.Tests.Sync;
using Microsoft.Extensions.Time.Testing;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>Life goal pictures kept here, sent up, brought down and cleared (docs/life-goals.md, ADR 0018).</summary>
public sealed class LifeGoalPicturesTests : IDisposable
{
    private static readonly byte[] Jpeg = [0xFF, 0xD8, 1, 2, 3];
    private readonly TestReplica test = new();
    private readonly FakeTimeProvider time = new(new DateTimeOffset(2026, 10, 4, 12, 0, 0, TimeSpan.Zero));
    private readonly MemoryFiles files = new();
    private readonly MemoryCloud cloud = new();
    private readonly LifeGoalList lifeGoals;
    private readonly LifeGoalPictures pictures;
    private int transfers;

    public LifeGoalPicturesTests()
    {
        time.SetLocalTimeZone(TimeZoneInfo.Utc);
        var rows = new NewRows(test.Catalog, () => TestReplica.Owner, time);
        lifeGoals = new LifeGoalList(test.Replica, rows, () => { });
        pictures = new LifeGoalPictures(lifeGoals, files, cloud, () => TestReplica.Owner, time, () => transfers++);
    }

    public void Dispose() => test.Dispose();

    [Fact]
    public async Task APictureIsKeptAtOnceAndGoesUpWhenTheServerCanBeReached()
    {
        var car = lifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof"))!;
        var changes = 0;
        pictures.Changed += (_, _) => changes++;
        var photo = pictures.Add(car.Id, Jpeg, 1600, 900)!;

        Assert.Equal(Jpeg, pictures.Read(photo.Id));
        Assert.Equal([photo.Id], files.Pending());
        Assert.Equal(1, transfers);
        Assert.Equal(1, changes);

        cloud.Offline = true;
        Assert.False(await pictures.TransferAsync(TestContext.Current.CancellationToken));
        Assert.Equal([photo.Id], files.Pending());

        cloud.Offline = false;
        Assert.True(await pictures.TransferAsync(TestContext.Current.CancellationToken));
        Assert.Equal(Jpeg, cloud.Stored[$"{TestReplica.Owner}/{photo.Id}"]);
        Assert.Empty(files.Pending());
    }

    [Fact]
    public async Task APictureFromTheOtherDeviceComesDownOnce()
    {
        var car = lifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof"))!;
        var photo = lifeGoals.AddPicture(car.Id, 1600, 900)!;
        cloud.Stored[$"{TestReplica.Owner}/{photo.Id}"] = Jpeg;
        var changes = 0;
        pictures.Changed += (_, _) => changes++;

        Assert.True(await pictures.TransferAsync(TestContext.Current.CancellationToken));
        Assert.True(await pictures.TransferAsync(TestContext.Current.CancellationToken));

        Assert.Equal(Jpeg, pictures.Read(photo.Id));
        Assert.Equal(1, cloud.Downloads);
        Assert.Equal(1, changes);
    }

    [Fact]
    public async Task ADeletedPicturesFileGoesADayLaterSoAnUndoStillFindsIt()
    {
        var car = lifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof"))!;
        var photo = pictures.Add(car.Id, Jpeg, 1600, 900)!;
        Assert.True(await pictures.TransferAsync(TestContext.Current.CancellationToken));

        Assert.True(lifeGoals.Delete(car.Id));
        Assert.True(await pictures.TransferAsync(TestContext.Current.CancellationToken));
        Assert.Equal(Jpeg, pictures.Read(photo.Id));
        Assert.True(lifeGoals.Restore(car.Id));
        Assert.True(lifeGoals.Delete(car.Id));

        time.Advance(TimeSpan.FromDays(2));
        Assert.True(await pictures.TransferAsync(TestContext.Current.CancellationToken));

        Assert.Null(pictures.Read(photo.Id));
        Assert.False(cloud.Stored.ContainsKey($"{TestReplica.Owner}/{photo.Id}"));
    }

    [Fact]
    public async Task APictureDeletedBeforeItWentUpIsNeverSent()
    {
        var car = lifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof"))!;
        var photo = pictures.Add(car.Id, Jpeg, 1600, 900)!;
        Assert.True(lifeGoals.RemovePicture(photo.Id));

        Assert.True(await pictures.TransferAsync(TestContext.Current.CancellationToken));

        Assert.Empty(cloud.Stored);
        Assert.Empty(files.Pending());
    }

    [Fact]
    public async Task ARefusedFileIsSkippedAndTheRestStillGo()
    {
        var car = lifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof"))!;
        var big = pictures.Add(car.Id, Jpeg, 1600, 900)!;
        var small = pictures.Add(car.Id, Jpeg, 800, 450)!;
        cloud.Refuse = [big.Id];

        Assert.True(await pictures.TransferAsync(TestContext.Current.CancellationToken));

        Assert.Equal([big.Id], files.Pending());
        Assert.True(cloud.Stored.ContainsKey($"{TestReplica.Owner}/{small.Id}"));
    }

    [Fact]
    public async Task AFileWhoseRowIsLongGoneIsClearedAndNothingIsSentWithoutAnOwner()
    {
        var orphan = Guid.NewGuid().ToString();
        files.Write(orphan, Jpeg, pending: false);
        var car = lifeGoals.Add(new LifeGoalDraft("Own an Audi R8", "Proof"))!;
        var nobody = new LifeGoalPictures(lifeGoals, files, cloud, () => null, time, () => { });
        var photo = nobody.Add(car.Id, Jpeg, 1600, 900)!;

        Assert.True(await nobody.TransferAsync(TestContext.Current.CancellationToken));
        Assert.Empty(cloud.Stored);
        Assert.Equal([photo.Id], files.Pending());

        Assert.True(await pictures.TransferAsync(TestContext.Current.CancellationToken));
        Assert.False(files.Has(orphan));
        Assert.True(files.Has(photo.Id));
    }

    private sealed class MemoryFiles : IPictureFiles
    {
        private readonly Dictionary<string, byte[]> bytes = [];
        private readonly HashSet<string> waiting = [];

        public bool Has(string id) => bytes.ContainsKey(id);

        public byte[]? Read(string id) => bytes.GetValueOrDefault(id);

        public void Write(string id, byte[] bytes, bool pending)
        {
            this.bytes[id] = bytes;
            if (pending)
            {
                waiting.Add(id);
            }
        }

        public void Delete(string id)
        {
            bytes.Remove(id);
            waiting.Remove(id);
        }

        public IReadOnlySet<string> Ids() => bytes.Keys.ToHashSet();

        public IReadOnlySet<string> Pending() => waiting.ToHashSet();

        public void Uploaded(string id) => waiting.Remove(id);
    }

    private sealed class MemoryCloud : IPictureCloud
    {
        public Dictionary<string, byte[]> Stored { get; } = [];

        public bool Offline { get; set; }

        public HashSet<string> Refuse { get; set; } = [];

        public int Downloads { get; private set; }

        public Task UploadAsync(string owner, string id, byte[] bytes, CancellationToken cancellationToken)
        {
            Check();
            if (Refuse.Contains(id))
            {
                throw new RemoteRejectedException("HTTP 413: too big");
            }

            Stored[$"{owner}/{id}"] = bytes;
            return Task.CompletedTask;
        }

        public Task<byte[]?> DownloadAsync(string owner, string id, CancellationToken cancellationToken)
        {
            Check();
            Downloads++;
            return Task.FromResult(Stored.GetValueOrDefault($"{owner}/{id}"));
        }

        public Task RemoveAsync(string owner, string id, CancellationToken cancellationToken)
        {
            Check();
            Stored.Remove($"{owner}/{id}");
            return Task.CompletedTask;
        }

        private void Check()
        {
            if (Offline)
            {
                throw new RemoteUnavailableException("The server can't be reached.");
            }
        }
    }
}
