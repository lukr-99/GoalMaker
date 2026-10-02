using GoalMaker.Core.Settings;

namespace GoalMaker.Core.Tests.Settings;

/// <summary>What the Settings text fields accept, the same cases as the phone's SettingsFieldRulesTest.</summary>
public sealed class SettingsFieldRulesTests
{
    [Fact]
    public void TimesAreHoursAndMinutes()
    {
        Assert.Equal(new TimeOnly(22, 0), SettingsFieldRules.Time("22:00"));
        Assert.Equal(new TimeOnly(7, 30), SettingsFieldRules.Time(" 7:30 "));
        Assert.Equal(TimeOnly.MinValue, SettingsFieldRules.Time("00:00"));
        Assert.Null(SettingsFieldRules.Time("24:00"));
        Assert.Null(SettingsFieldRules.Time("22:60"));
        Assert.Null(SettingsFieldRules.Time("22"));
        Assert.Null(SettingsFieldRules.Time("10 pm"));
        Assert.Null(SettingsFieldRules.Time(""));
        Assert.Equal("07:30", SettingsFieldRules.Format(new TimeOnly(7, 30)));
    }

    [Fact]
    public void ABackendIsAnHttpOrHttpsAddressWithAHost()
    {
        Assert.True(SettingsFieldRules.BackendUrl("http://192.168.1.20:55321"));
        Assert.True(SettingsFieldRules.BackendUrl("https://abc.supabase.co"));
        Assert.True(SettingsFieldRules.BackendUrl("http://127.0.0.1:55321/"));
        Assert.False(SettingsFieldRules.BackendUrl("192.168.1.20:55321"));
        Assert.False(SettingsFieldRules.BackendUrl("ftp://example.com"));
        Assert.False(SettingsFieldRules.BackendUrl("http://"));
        Assert.False(SettingsFieldRules.BackendUrl("http://exa mple.com"));
        Assert.False(SettingsFieldRules.BackendUrl(""));
    }
}
