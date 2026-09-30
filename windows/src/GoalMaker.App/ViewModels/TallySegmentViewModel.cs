using System.Windows.Media;

namespace GoalMaker.App.ViewModels;

/// <summary>One category in a Tally legend (docs/tally.md): its swatch, emoji, name and time.</summary>
public sealed record TallySegmentViewModel(string Name, string Emoji, string Value, Brush? Brush);
