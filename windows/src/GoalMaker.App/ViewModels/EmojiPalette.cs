namespace GoalMaker.App.ViewModels;

/// <summary>
/// The emoji people reach for when they name a habit or a goal, in the same order as the phone's
/// picker (android ui/components/EmojiField.kt). Anything else can still be typed in.
/// </summary>
public static class EmojiPalette
{
    public static readonly IReadOnlyList<string> All =
    [
        "💧", "🏃", "🚶", "🚴", "🏋️", "🧘", "🥗", "🍎", "😴", "🦷", "💊", "🧴",
        "📖", "📝", "🧠", "🎧", "🎵", "🎸", "🎨", "📷", "🌱", "🧩", "♟️", "🗣️",
        "💻", "📊", "📌", "📅", "✉️", "📞", "🧹", "🛠️", "💰", "🧾", "🚀", "🎯",
        "🏠", "👨‍👩‍👧", "🐕", "🌍", "☀️", "🌙", "🔥", "⭐", "❤️", "🙏", "🍳", "🛒",
    ];

    /// <summary>The palette as rows the picker binds to, each one putting its emoji on <paramref name="pick"/>.</summary>
    public static IReadOnlyList<EmojiChoiceViewModel> Choices(Action<string> pick) =>
        [.. All.Select(emoji => new EmojiChoiceViewModel(emoji, pick))];
}
