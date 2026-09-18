using System.Windows.Media;
using CommunityToolkit.Mvvm.Input;
using GoalMaker.Core.Composer;
using Wpf.Ui.Controls;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// One part of the composer's preview: what the line will save. The label carries a note when it
/// isn't plain ("new" area or tag, "later" for parts a future milestone saves); removing the chip
/// removes <see cref="Spans"/> from the line.
/// </summary>
public sealed class ComposerChipViewModel(
    SpanKind kind,
    string label,
    string removeName,
    SymbolRegular symbol,
    Brush? areaBrush,
    bool muted,
    IReadOnlyList<ComposerSpan> spans,
    Action<ComposerChipViewModel> remove)
{
    public SpanKind Kind { get; } = kind;

    public string Label { get; } = label;

    public string RemoveName { get; } = removeName;

    public SymbolRegular Symbol { get; } = symbol;

    /// <summary>The area's color, for the dot an area chip shows instead of an icon.</summary>
    public Brush? AreaBrush { get; } = areaBrush;

    public bool IsArea => Kind == SpanKind.Area;

    public bool ShowsSymbol => !IsArea;

    public double Opacity { get; } = muted ? 0.7 : 1;

    public IReadOnlyList<ComposerSpan> Spans { get; } = spans;

    public IRelayCommand RemoveCommand => new RelayCommand(() => remove(this));
}
