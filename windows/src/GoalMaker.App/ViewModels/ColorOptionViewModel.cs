using System.Windows.Media;

namespace GoalMaker.App.ViewModels;

/// <summary>One color of the area palette (themes.json) in the area color picker.</summary>
public sealed record ColorOptionViewModel(string Id, string Label, Brush? Brush);
