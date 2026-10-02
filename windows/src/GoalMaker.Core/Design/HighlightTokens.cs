namespace GoalMaker.Core.Design;

/// <summary>
/// How a Settings card lights up in one theme and mode (themes.json, "highlight"): the theme's
/// brightest accent <paramref name="Spot"/>, mixed into the card at <paramref name="Tint"/>; a 2 px
/// inner ring in <paramref name="Ring"/> at <paramref name="RingAlpha"/>; a 4 px soft glow of the spot
/// at <paramref name="GlowAlpha"/>; the 3 px left edge bar in <paramref name="Edge"/>; and the title in
/// <paramref name="Title"/> at the peak. Colors are opaque ARGB.
/// </summary>
public sealed record HighlightTokens(uint Spot, double Tint, uint Ring, double RingAlpha, double GlowAlpha, uint Edge, uint Title);
