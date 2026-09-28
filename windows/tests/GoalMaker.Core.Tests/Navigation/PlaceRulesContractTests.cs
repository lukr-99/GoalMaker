using System.Text.Json;
using GoalMaker.Core.Navigation;

namespace GoalMaker.Core.Tests.Navigation;

/// <summary>contracts/vectors/navigation.json, the same file the Android tests read.</summary>
public sealed class PlaceRulesContractTests
{
    private readonly JsonElement vectors = ContractFiles.Load("vectors/navigation.json").RootElement;

    [Fact]
    public void ThePlacesAreTheOnesTheContractNamesInItsOrder()
    {
        Assert.Equal(Ids(vectors.GetProperty("places")), PlaceRules.Places);
    }

    [Fact]
    public void LimitsAndDefaultsPerDevice()
    {
        foreach (var name in new[] { "phone", "pc" })
        {
            var limit = vectors.GetProperty("limits").GetProperty(name);
            int? expected = limit.ValueKind == JsonValueKind.Null ? null : limit.GetInt32();
            Assert.Equal(expected, PlaceRules.Limit(Device(name)));
            Assert.Equal(Ids(vectors.GetProperty("defaults").GetProperty(name)), PlaceRules.Defaults(Device(name)));
        }
    }

    [Fact]
    public void EveryPin()
    {
        foreach (var testCase in vectors.GetProperty("pin").EnumerateArray())
        {
            var actual = PlaceRules.Pin(
                Ids(testCase.GetProperty("pins")),
                testCase.GetProperty("place").GetString()!,
                Device(testCase.GetProperty("device").GetString()!));
            AssertResult(testCase, actual);
        }
    }

    [Fact]
    public void EveryUnpin()
    {
        foreach (var testCase in vectors.GetProperty("unpin").EnumerateArray())
        {
            AssertResult(testCase, PlaceRules.Unpin(Ids(testCase.GetProperty("pins")), testCase.GetProperty("place").GetString()!));
        }
    }

    [Fact]
    public void EveryStoredList()
    {
        foreach (var testCase in vectors.GetProperty("stored").EnumerateArray())
        {
            var stored = testCase.GetProperty("stored");
            var actual = PlaceRules.Stored(
                stored.ValueKind == JsonValueKind.Array ? Ids(stored) : null,
                Device(testCase.GetProperty("device").GetString()!));
            Assert.True(Ids(testCase.GetProperty("expect")).SequenceEqual(actual), Name(testCase));
        }
    }

    [Fact]
    public void EveryCount()
    {
        foreach (var testCase in vectors.GetProperty("count").EnumerateArray())
        {
            var waiting = testCase.GetProperty("waiting").EnumerateObject().ToDictionary(entry => entry.Name, entry => entry.Value.GetInt32());
            Assert.True(
                testCase.GetProperty("expect").GetInt32() == PlaceRules.Count(Ids(testCase.GetProperty("pins")), waiting),
                Name(testCase));
        }
    }

    private static void AssertResult(JsonElement testCase, PinResult actual)
    {
        var expect = testCase.GetProperty("expect");
        Assert.True(Ids(expect.GetProperty("pins")).SequenceEqual(actual.Pins), Name(testCase));
        Assert.True(expect.GetProperty("refused").GetBoolean() == actual.Refused, Name(testCase));
    }

    private static List<string> Ids(JsonElement array) => [.. array.EnumerateArray().Select(id => id.GetString()!)];

    private static DeviceKind Device(string name) => name switch
    {
        "phone" => DeviceKind.Phone,
        "pc" => DeviceKind.Pc,
        _ => throw new ArgumentException("unknown device " + name),
    };

    private static string Name(JsonElement testCase) => testCase.GetProperty("name").GetString()!;
}
