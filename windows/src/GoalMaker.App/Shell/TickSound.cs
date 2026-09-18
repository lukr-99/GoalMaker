using System.IO;
using System.Media;
using System.Windows;

namespace GoalMaker.App.Shell;

/// <summary>The optional completion tick (tools/generate_tick_sound.py), loaded once from the app's resources.</summary>
public sealed class TickSound : IDisposable
{
    private readonly Lazy<SoundPlayer?> player = new(Load);

    public void Play() => player.Value?.Play();

    public void Dispose()
    {
        if (player.IsValueCreated)
        {
            player.Value?.Dispose();
        }
    }

    private static SoundPlayer? Load()
    {
        var resource = Application.GetResourceStream(new Uri("pack://application:,,,/Assets/tick.wav"));
        if (resource is null)
        {
            return null;
        }

        var memory = new MemoryStream();
        using (resource.Stream)
        {
            resource.Stream.CopyTo(memory);
        }

        memory.Position = 0;
        var sound = new SoundPlayer(memory);
        sound.Load();
        return sound;
    }
}
