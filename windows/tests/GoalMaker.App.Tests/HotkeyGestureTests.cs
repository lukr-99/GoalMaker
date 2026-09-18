using System.Windows.Input;
using GoalMaker.App.Shell;

namespace GoalMaker.App.Tests;

/// <summary>The quick-add shortcut as the settings store it.</summary>
public sealed class HotkeyGestureTests
{
    [Fact]
    public void TheDefaultIsWinAltSpace()
    {
        Assert.Equal("Win+Alt+Space", HotkeyGesture.Default.ToString());
    }

    [Theory]
    [InlineData("Win+Alt+Space", "Win+Alt+Space")]
    [InlineData("alt+win+space", "Win+Alt+Space")]
    [InlineData("Ctrl+Shift+K", "Ctrl+Shift+K")]
    [InlineData("control + alt + 7", "Ctrl+Alt+7")]
    [InlineData("Windows+F9", "Win+F9")]
    public void ReadsWhatPeopleWriteAndWritesItTheSameWay(string text, string written)
    {
        Assert.Equal(written, HotkeyGesture.Parse(text)?.ToString());
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("Space")]
    [InlineData("Ctrl+Alt")]
    [InlineData("Ctrl+K+J")]
    [InlineData("Ctrl+Banana")]
    [InlineData("Ctrl+42")]
    public void RefusesWhatCantBeAGlobalShortcut(string? text)
    {
        Assert.Null(HotkeyGesture.Parse(text));
    }

    [Fact]
    public void KeysPressedTogetherNeedAModifierAndAnotherKey()
    {
        Assert.Equal("Ctrl+Alt+N", HotkeyGesture.FromKeys(ModifierKeys.Control | ModifierKeys.Alt, Key.N)?.ToString());
        Assert.Null(HotkeyGesture.FromKeys(ModifierKeys.None, Key.N));
        Assert.Null(HotkeyGesture.FromKeys(ModifierKeys.Control, Key.LeftShift));
    }
}
