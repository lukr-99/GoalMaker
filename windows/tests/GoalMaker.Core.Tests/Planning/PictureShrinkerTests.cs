using System.Windows.Media;
using System.Windows.Media.Imaging;
using GoalMaker.Infrastructure.Planning;

namespace GoalMaker.Core.Tests.Planning;

/// <summary>A picked picture made ready to keep: turned upright, at most 1600 pixels, a JPEG (ADR 0018).</summary>
public sealed class PictureShrinkerTests
{
    [Fact]
    public void ABigPictureIsShrunkToItsLongestSideAsAJpeg()
    {
        var shrunk = PictureShrinker.Shrink(Encode(new PngBitmapEncoder(), Solid(4000, 2000, PixelFormats.Bgra32, 128)))!;

        Assert.Equal((1600, 800), (shrunk.Width, shrunk.Height));
        Assert.Equal([0xFF, 0xD8], shrunk.Jpeg[..2]);
        var back = BitmapDecoder.Create(new MemoryStream(shrunk.Jpeg), BitmapCreateOptions.None, BitmapCacheOption.OnLoad).Frames[0];
        Assert.Equal((1600, 800), (back.PixelWidth, back.PixelHeight));
    }

    [Fact]
    public void ASmallPictureKeepsItsSize()
    {
        var shrunk = PictureShrinker.Shrink(Encode(new JpegBitmapEncoder(), Solid(640, 480, PixelFormats.Bgr32, 255)))!;

        Assert.Equal((640, 480), (shrunk.Width, shrunk.Height));
    }

    [Fact]
    public void APictureTheCameraHeldSidewaysIsTurnedUpright()
    {
        var metadata = new BitmapMetadata("jpg");
        metadata.SetQuery("/app1/ifd/{ushort=274}", (ushort)6);
        var encoder = new JpegBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(Solid(400, 200, PixelFormats.Bgr32, 255), null, metadata, null));
        using var stream = new MemoryStream();
        encoder.Save(stream);

        var shrunk = PictureShrinker.Shrink(stream.ToArray())!;

        Assert.Equal((200, 400), (shrunk.Width, shrunk.Height));
    }

    [Fact]
    public void SomethingThatIsNoPictureGivesNothing()
    {
        Assert.Null(PictureShrinker.Shrink([1, 2, 3]));
        Assert.Null(PictureShrinker.Shrink(Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N") + ".jpg")));
    }

    private static BitmapSource Solid(int width, int height, PixelFormat format, byte alpha)
    {
        var stride = width * 4;
        var pixels = new byte[stride * height];
        for (var index = 0; index < pixels.Length; index += 4)
        {
            pixels[index] = 200;
            pixels[index + 1] = 100;
            pixels[index + 2] = 50;
            pixels[index + 3] = alpha;
        }

        return BitmapSource.Create(width, height, 96, 96, format, null, pixels, stride);
    }

    private static byte[] Encode(BitmapEncoder encoder, BitmapSource source)
    {
        encoder.Frames.Add(BitmapFrame.Create(source));
        using var stream = new MemoryStream();
        encoder.Save(stream);
        return stream.ToArray();
    }
}
