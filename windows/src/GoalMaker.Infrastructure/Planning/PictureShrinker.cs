using System.Runtime.InteropServices;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using GoalMaker.Core.Planning;

namespace GoalMaker.Infrastructure.Planning;

/// <summary>
/// Makes a picked picture ready to keep (ADR 0018): turned the way its EXIF orientation says, at most
/// <see cref="MaxSide"/> pixels on its longest side, laid on white where it is see-through, and saved
/// as a JPEG at quality 85. Blocks, so callers run it off the UI thread. Null when the file can't be
/// read as a picture.
/// </summary>
public static class PictureShrinker
{
    public const int MaxSide = 1600;
    private const int Quality = 85;

    // EXIF 274: 1 is upright; 2 to 8 say how the camera held it (mirrored, turned or both).
    private const string OrientationQuery = "System.Photo.Orientation";

    public static ShrunkPicture? Shrink(string path)
    {
        try
        {
            return Shrink(File.ReadAllBytes(path));
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException or ArgumentException or NotSupportedException)
        {
            return null;
        }
    }

    public static ShrunkPicture? Shrink(byte[] file)
    {
        try
        {
            using var stream = new MemoryStream(file, writable: false);
            var decoder = BitmapDecoder.Create(stream, BitmapCreateOptions.None, BitmapCacheOption.OnLoad);
            var frame = decoder.Frames[0];
            var transforms = new TransformGroup();
            Orient(transforms, Orientation(frame));
            var scale = (double)MaxSide / Math.Max(frame.PixelWidth, frame.PixelHeight);
            if (scale < 1)
            {
                transforms.Children.Add(new ScaleTransform(scale, scale));
            }

            BitmapSource picture = transforms.Children.Count == 0 ? frame : new TransformedBitmap(frame, transforms);
            // Plain 24-bit color, which every decoder reads (a CMYK or 16-bit JPEG is not that).
            picture = new FormatConvertedBitmap(OnWhite(picture), PixelFormats.Bgr24, null, 0);
            var encoder = new JpegBitmapEncoder { QualityLevel = Quality };
            encoder.Frames.Add(BitmapFrame.Create(picture));
            using var output = new MemoryStream();
            encoder.Save(output);
            return new ShrunkPicture(output.ToArray(), picture.PixelWidth, picture.PixelHeight);
        }
        catch (Exception error) when (error is IOException or NotSupportedException or FormatException or ArgumentException
            or InvalidOperationException or OverflowException or COMException or OutOfMemoryException)
        {
            return null;
        }
    }

    private static int Orientation(BitmapFrame frame)
    {
        try
        {
            return frame.Metadata is BitmapMetadata metadata && metadata.GetQuery(OrientationQuery) is ushort value ? value : 1;
        }
        catch (Exception error) when (error is NotSupportedException or InvalidOperationException or ArgumentException or COMException)
        {
            // PNG, BMP and GIF have no EXIF, and some decoders refuse the question.
            return 1;
        }
    }

    // Mirroring first, then the turn clockwise, as the EXIF values mean them.
    private static void Orient(TransformGroup transforms, int orientation)
    {
        var (mirror, turn) = orientation switch
        {
            2 => (true, 0),
            3 => (false, 180),
            4 => (true, 180),
            5 => (true, 270),
            6 => (false, 90),
            7 => (true, 90),
            8 => (false, 270),
            _ => (false, 0),
        };
        if (mirror)
        {
            transforms.Children.Add(new ScaleTransform(-1, 1));
        }

        if (turn != 0)
        {
            transforms.Children.Add(new RotateTransform(turn));
        }
    }

    // A JPEG has no see-through parts, so a PNG's are laid on white rather than left to turn black.
    private static BitmapSource OnWhite(BitmapSource picture)
    {
        if (!HasAlpha(picture.Format))
        {
            return picture;
        }

        var straight = new FormatConvertedBitmap(picture, PixelFormats.Bgra32, null, 0);
        var stride = straight.PixelWidth * 4;
        var pixels = new byte[stride * straight.PixelHeight];
        straight.CopyPixels(pixels, stride, 0);
        for (var index = 0; index < pixels.Length; index += 4)
        {
            var alpha = pixels[index + 3];
            for (var channel = 0; channel < 3; channel++)
            {
                pixels[index + channel] = (byte)(((pixels[index + channel] * alpha) + (255 * (255 - alpha)) + 127) / 255);
            }

            pixels[index + 3] = 255;
        }

        return BitmapSource.Create(straight.PixelWidth, straight.PixelHeight, 96, 96, PixelFormats.Bgr32, null, pixels, stride);
    }

    private static bool HasAlpha(PixelFormat format) =>
        format == PixelFormats.Bgra32 || format == PixelFormats.Pbgra32 || format == PixelFormats.Prgba64
        || format == PixelFormats.Rgba64 || format == PixelFormats.Rgba128Float || format == PixelFormats.Prgba128Float
        || format == PixelFormats.Indexed8 || format == PixelFormats.Indexed4 || format == PixelFormats.Indexed2 || format == PixelFormats.Indexed1;
}
