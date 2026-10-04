namespace GoalMaker.Core.Planning;

/// <summary>A picture made ready to keep: a JPEG at most 1600 pixels on its longest side (ADR 0018).</summary>
public sealed record ShrunkPicture(byte[] Jpeg, int Width, int Height);
