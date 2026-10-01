using System.IO;
using DotNetLib.Tray;
using GoalMaker.App.Controls;
using GoalMaker.Infrastructure.Sync;

namespace GoalMaker.App.Tests;

/// <summary>The tray icon: the logo in each theme's colors as an .ico file from the tray kit.</summary>
public sealed class GoalMakerLogoTests
{
    [Fact]
    public void TheTrayIconHasAFramePerTraySize() => StaThread.Run(() =>
    {
        var theme = ContractResources.Themes().Theme(null);

        var file = GoalMakerLogo.IconFile(ContractResources.Logo(), theme.Logo);

        using var reader = new BinaryReader(new MemoryStream(file));
        Assert.Equal(0, reader.ReadUInt16());
        Assert.Equal(1, reader.ReadUInt16());
        var count = reader.ReadUInt16();
        var sizes = new List<int>();
        for (var index = 0; index < count; index++)
        {
            sizes.Add(reader.ReadByte());
            reader.ReadBytes(15);
        }

        Assert.Equal(IconFile.TraySizes, sizes);
        using var icon = new System.Drawing.Icon(new MemoryStream(file), 32, 32);
        Assert.Equal(32, icon.Width);
    });

    [Fact]
    public void EveryThemeMakesItsOwnIcon() => StaThread.Run(() =>
    {
        var tokens = ContractResources.Themes();
        var mark = ContractResources.Logo();

        var files = tokens.Themes.Select(theme => Convert.ToHexString(GoalMakerLogo.IconFile(mark, theme.Logo))).ToList();

        Assert.Equal(files.Count, files.Distinct().Count());
    });
}
