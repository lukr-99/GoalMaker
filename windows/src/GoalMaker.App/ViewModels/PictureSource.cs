using System.IO;
using System.Runtime.InteropServices;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace GoalMaker.App.ViewModels;

/// <summary>A life goal picture's JPEG bytes as something WPF can draw, read at about the size it is shown.</summary>
public static class PictureSource
{
    /// <summary>The picture, frozen so any thread may hand it on; null without bytes or when they are not a picture.</summary>
    public static ImageSource? Decode(byte[]? bytes, int width)
    {
        if (bytes is not { Length: > 0 })
        {
            return null;
        }

        try
        {
            var image = new BitmapImage();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.DecodePixelWidth = width;
            image.StreamSource = new MemoryStream(bytes, writable: false);
            image.EndInit();
            image.Freeze();
            return image;
        }
        catch (Exception error) when (error is IOException or NotSupportedException or FormatException or ArgumentException
            or InvalidOperationException or COMException)
        {
            return null;
        }
    }
}
